package cl.ferrefix.service.impl;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.*;
import cl.ferrefix.model.dto.CrearOrdenRequestDto;
import cl.ferrefix.model.dto.CrearOrdenRequestDto.LineaOrden;
import cl.ferrefix.model.dto.OrdenResponseDto;
import cl.ferrefix.model.mapper.OrdenMapper;
import cl.ferrefix.repository.OrdenRepository;
import cl.ferrefix.repository.ProductoRepository;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.OrdenService;
import cl.ferrefix.service.PoliticaEnvio;
import cl.ferrefix.util.Textos;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Órdenes de compra.
 *
 * Crear es todo o nada en UNA transacción: el stock se descuenta línea por línea con un UPDATE atómico
 * (WHERE STOCK >= cantidad, exigiendo 1 fila); si cualquier línea falla se lanza una excepción y la transacción
 * completa se revierte, así que no queda ninguna línea descontada.
 *
 * Orden de bloqueo (evita deadlocks): las líneas SIEMPRE se procesan por productoId ascendente, tanto al
 * descontar como al devolver stock; y en los cambios de una orden existente se bloquea primero la orden y
 * después sus productos. Crear nunca espera una orden existente, por lo que no hay ciclos.
 */
@Service
@RequiredArgsConstructor
public class OrdenServiceImpl implements OrdenService {

    private final OrdenRepository ordenRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrdenMapper mapper;
    private final PoliticaEnvio politicaEnvio;
    private final Clock clock; // hora de Chile (ver ClockConfig)

    // ---------------------------------------------------------------- crear
    @Override
    @Transactional
    public OrdenResponseDto crear(String emailCliente, CrearOrdenRequestDto request) {
        // 1) Validaciones sin base de datos. Líneas ordenadas por productoId: ese es el orden de bloqueo global.
        List<LineaOrden> lineas = new ArrayList<>(request.items());
        lineas.sort(Comparator.comparing(LineaOrden::productoId));
        for (int i = 1; i < lineas.size(); i++) {
            if (lineas.get(i).productoId().equals(lineas.get(i - 1).productoId())) {
                throw new BadRequestException("El producto " + lineas.get(i).productoId() + " está repetido en la orden");
            }
        }
        Usuario cliente = usuarioRepository.findByEmailIgnoreCase(emailCliente)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Usuario no válido"));

        // 2) Existencia (solo ids, sin cargar entidades que luego quedarían desactualizadas por el UPDATE).
        List<Long> ids = lineas.stream().map(LineaOrden::productoId).toList();
        Set<Long> existentes = new HashSet<>(productoRepository.idsExistentes(ids));
        List<Long> faltantes = ids.stream().filter(id -> !existentes.contains(id)).toList();
        if (!faltantes.isEmpty()) {
            throw new ResourceNotFoundException("Producto no encontrado: " + faltantes);
        }

        // 3) Descuento atómico por línea, en orden ascendente. Se intentan TODAS para informar todos los problemas
        //    a la vez; si hubo alguno, la excepción revierte los descuentos que sí se alcanzaron a hacer.
        List<LineaOrden> sinStock = new ArrayList<>();
        for (LineaOrden l : lineas) {
            if (productoRepository.descontarStock(l.productoId(), l.cantidad()) != 1) {
                sinStock.add(l);
            }
        }
        if (!sinStock.isEmpty()) {
            throw stockInsuficiente(sinStock);
        }

        // 4) Recién ahora (con las filas bloqueadas por nuestro UPDATE) se leen nombre, sku y precio del catálogo.
        Map<Long, Producto> catalogo = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        LocalDateTime ahora = LocalDateTime.now(clock);
        Orden orden = new Orden();
        orden.setUsuario(cliente);
        orden.setClienteRut(cliente.getRut());
        orden.setClienteNombre(cliente.getNombre());
        orden.setClienteEmail(cliente.getEmail());
        orden.setTelefonoContacto(request.telefono().trim());
        orden.setDireccionEnvio(request.direccionEnvio().trim());
        orden.setRegionEnvio(request.region().trim());
        orden.setComunaEnvio(request.comuna().trim());
        orden.setIndicaciones(Textos.limpiar(request.indicaciones()));
        orden.setMetodoPago(request.metodoPago());          // pago simulado: siempre aprobado
        orden.setEstado(EstadoOrden.PAGADA);
        orden.setCreatedAt(ahora);
        orden.setUpdatedAt(ahora);

        long subtotal = 0;
        for (LineaOrden l : lineas) {
            Producto p = catalogo.get(l.productoId());
            OrdenItem item = new OrdenItem();
            item.setProductoId(p.getId());
            item.setSku(p.getSku());
            item.setNombre(p.getNombre());
            item.setPrecioUnitario(p.getPrecioVenta());      // precio del catálogo, nunca del cliente
            item.setCantidad(l.cantidad());
            item.setSubtotal((long) p.getPrecioVenta() * l.cantidad());
            subtotal += item.getSubtotal();
            orden.agregarItem(item);
        }
        int envio = politicaEnvio.costoPara(subtotal);
        orden.setSubtotal(subtotal);
        orden.setCostoEnvio(envio);
        orden.setTotal(subtotal + envio);

        return mapper.toResponse(ordenRepository.save(orden), false);
    }

    private ConflictException stockInsuficiente(List<LineaOrden> sinStock) {
        Map<Long, Producto> productos = productoRepository.findAllById(sinStock.stream().map(LineaOrden::productoId).toList()).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        List<String> detalles = new ArrayList<>();
        for (LineaOrden l : sinStock) {
            Producto p = productos.get(l.productoId());
            if (p == null) {
                detalles.add("Producto " + l.productoId() + ": ya no existe");
            } else if (p.getStock() <= 0) {
                detalles.add(p.getNombre() + " (SKU " + p.getSku() + "): agotado");
            } else {
                detalles.add(p.getNombre() + " (SKU " + p.getSku() + "): disponibles " + p.getStock() + ", solicitaste " + l.cantidad());
            }
        }
        return new ConflictException("No hay stock suficiente para completar la compra. No se descontó nada", detalles);
    }

    // ---------------------------------------------------------------- lectura
    @Override
    @Transactional(readOnly = true)
    public List<OrdenResponseDto> misOrdenes(String emailCliente) {
        Usuario cliente = usuarioRepository.findByEmailIgnoreCase(emailCliente)
                .orElseThrow(() -> new BadCredentialsException("Usuario no válido"));
        return ordenRepository.findByUsuarioIdOrderByCreatedAtDescIdDesc(cliente.getId()).stream()
                .map(o -> mapper.toResponse(o, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrdenResponseDto obtener(Long id, String email, boolean esAdmin) {
        Orden o = ordenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + id));
        if (!esAdmin && !o.getUsuario().getEmail().equalsIgnoreCase(email)) {
            // Se responde igual que si no existiera: no se revela que ese id pertenece a otra persona.
            throw new ResourceNotFoundException("Orden no encontrada: " + id);
        }
        return mapper.toResponse(o, esAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrdenResponseDto> listar(String estado, Long usuarioId) {
        EstadoOrden estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            try {
                estadoFiltro = EstadoOrden.valueOf(estado.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Estado inválido. Valores permitidos: " + Arrays.toString(EstadoOrden.values()));
            }
        }
        final EstadoOrden filtro = estadoFiltro;
        Specification<Orden> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (filtro != null) {
                p.add(cb.equal(root.get("estado"), filtro));
            }
            if (usuarioId != null) {
                p.add(cb.equal(root.get("usuario").get("id"), usuarioId));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return ordenRepository.findAll(spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).stream()
                .map(o -> mapper.toResponse(o, true)).toList();
    }

    // ---------------------------------------------------------------- ADMIN: estado, cancelación y borrado
    @Override
    @Transactional
    public OrdenResponseDto cambiarEstado(Long id, EstadoOrden nuevoEstado) {
        Orden o = bloquear(id); // primero la orden; después, si se cancela, sus productos en orden ascendente
        if (!o.getEstado().puedePasarA(nuevoEstado)) {
            throw new ConflictException("Transición no permitida: " + o.getEstado() + " → " + nuevoEstado);
        }
        if (nuevoEstado == EstadoOrden.CANCELADA) {
            devolverStock(o);
        }
        o.setEstado(nuevoEstado);
        o.setUpdatedAt(LocalDateTime.now(clock));
        return mapper.toResponse(ordenRepository.save(o), true);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Orden o = bloquear(id);
        if (!o.getEstado().esFinal()) {
            throw new ConflictException("Solo se pueden eliminar órdenes ENTREGADA o CANCELADA. Estado actual: "
                    + o.getEstado() + (o.getEstado() == EstadoOrden.DESPACHADA ? "" : ". Cancélela primero"));
        }
        ordenRepository.delete(o); // no toca el stock: si estaba CANCELADA ya se devolvió; si estaba ENTREGADA, la mercadería salió
    }

    private Orden bloquear(Long id) {
        return ordenRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + id));
    }

    /** Suma de vuelta cada cantidad, por productoId ascendente. Un ítem cuyo producto ya no existe no se puede restituir. */
    private void devolverStock(Orden o) {
        o.getItems().stream()
                .filter(i -> i.getProductoId() != null)
                .sorted(Comparator.comparing(OrdenItem::getProductoId))
                .forEach(i -> productoRepository.restaurarStock(i.getProductoId(), i.getCantidad()));
    }
}
