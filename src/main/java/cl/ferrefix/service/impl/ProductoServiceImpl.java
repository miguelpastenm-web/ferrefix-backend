package cl.ferrefix.service.impl;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.CategoriaProducto;
import cl.ferrefix.model.Producto;
import cl.ferrefix.model.ProductoEspecificacion;
import cl.ferrefix.model.dto.ProductoRequestDto;
import cl.ferrefix.model.dto.ProductoResponseDto;
import cl.ferrefix.model.mapper.ProductoMapper;
import cl.ferrefix.repository.ProductoRepository;
import cl.ferrefix.service.ProductoService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    /** alias público -> propiedad de la entidad. Cualquier otro campo de sort se rechaza con 400. */
    static final Map<String, String> CAMPOS_ORDENABLES = Map.of(
            "nombre", "nombre",
            "marca", "marca",
            "categoria", "categoria",
            "precio", "precioVenta",
            "stock", "stock",
            "fecha", "createdAt");

    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;

    // ---------------------------------------------------------------- lectura
    @Override
    @Transactional(readOnly = true)
    public List<ProductoResponseDto> listar(String q, String categoria, String marca, String sort) {
        Sort orden = parsearSort(sort);
        String categoriaCanonica = null;
        if (categoria != null && !categoria.isBlank()) {
            categoriaCanonica = CategoriaProducto.canonica(categoria)
                    .orElseThrow(() -> new BadRequestException(mensajeCategoria()));
        }
        Specification<Producto> filtros = filtros(q, categoriaCanonica, marca);
        return productoRepository.findAll(filtros, orden).stream().map(productoMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoResponseDto obtener(Long id) {
        return productoMapper.toResponse(buscar(id));
    }

    // ---------------------------------------------------------------- escritura
    @Override
    @Transactional
    public ProductoResponseDto crear(ProductoRequestDto request) {
        String sku = request.sku().trim();
        if (productoRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("Ya existe un producto con el SKU " + sku);
        }
        String categoria = categoriaValida(request.categoria());
        Map<String, String> specs = normalizarEspecificaciones(request.especificaciones());

        Producto producto = productoMapper.toEntity(request, sku, categoria, specs);
        producto.recalcularEstado();
        return productoMapper.toResponse(productoRepository.save(producto));
    }

    @Override
    @Transactional
    public ProductoResponseDto actualizar(Long id, ProductoRequestDto request) {
        Producto producto = buscar(id);
        String sku = request.sku().trim();
        if (productoRepository.existsBySkuAndIdNot(sku, id)) {
            throw new DuplicateResourceException("Ya existe otro producto con el SKU " + sku);
        }
        String categoria = categoriaValida(request.categoria());
        Map<String, String> specs = normalizarEspecificaciones(request.especificaciones());

        // Campo a campo: se conservan id y createdAt.
        producto.setSku(sku);
        producto.setNombre(request.nombre().trim());
        producto.setMarca(request.marca().trim());
        producto.setCategoria(categoria);
        producto.setPrecioVenta(request.precioVenta());
        producto.setStock(request.stock());
        producto.setDescripcion(ProductoMapper.limpiar(request.descripcion()));
        producto.setImagenUrl(ProductoMapper.limpiar(request.imagenUrl()));
        producto.setImagenOfertaUrl(ProductoMapper.limpiar(request.imagenOfertaUrl()));
        sincronizarEspecificaciones(producto, specs);
        producto.recalcularEstado();
        producto.setUpdatedAt(LocalDateTime.now()); // explícito: Hibernate no hace UPDATE si nada cambió

        return productoMapper.toResponse(productoRepository.saveAndFlush(producto));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        productoRepository.delete(buscar(id));
    }

    // ---------------------------------------------------------------- helpers
    private Producto buscar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }

    private String categoriaValida(String categoria) {
        return CategoriaProducto.canonica(categoria).orElseThrow(() -> new BadRequestException(mensajeCategoria()));
    }

    private String mensajeCategoria() {
        return "Categoría inválida. Valores permitidos: " + String.join(", ", CategoriaProducto.TODAS);
    }

    /**
     * Valida y limpia las especificaciones conservando el orden de llegada.
     * Clave y valor no vacíos, hasta 80 y 300 caracteres, y sin claves repetidas tras recortar espacios.
     */
    private Map<String, String> normalizarEspecificaciones(Map<String, String> entrada) {
        Map<String, String> resultado = new LinkedHashMap<>();
        if (entrada == null) {
            return resultado;
        }
        for (Map.Entry<String, String> e : entrada.entrySet()) {
            String clave = e.getKey() == null ? "" : e.getKey().trim();
            String valor = e.getValue() == null ? "" : e.getValue().trim();
            if (clave.isEmpty() || valor.isEmpty()) {
                throw new BadRequestException("Las especificaciones no pueden tener clave ni valor vacíos");
            }
            if (clave.length() > 80) {
                throw new BadRequestException("La clave de una especificación no puede superar 80 caracteres");
            }
            if (valor.length() > 300) {
                throw new BadRequestException("El valor de la especificación '" + clave + "' supera 300 caracteres");
            }
            if (resultado.put(clave, valor) != null) {
                throw new BadRequestException("Especificación repetida: " + clave);
            }
        }
        return resultado;
    }

    /**
     * Actualiza en el lugar (por clave) en vez de borrar y reinsertar: Hibernate ejecuta los INSERT antes
     * que los DELETE y chocaría con UNIQUE (PRODUCTO_ID, CLAVE).
     */
    private void sincronizarEspecificaciones(Producto producto, Map<String, String> nuevas) {
        Map<String, ProductoEspecificacion> existentes = new HashMap<>();
        producto.getEspecificaciones().forEach(e -> existentes.put(e.getClave(), e));

        producto.getEspecificaciones().removeIf(e -> !nuevas.containsKey(e.getClave()));

        int orden = 0;
        for (Map.Entry<String, String> e : nuevas.entrySet()) {
            ProductoEspecificacion actual = existentes.get(e.getKey());
            if (actual != null) {
                actual.setValor(e.getValue());
                actual.setOrden(orden);
            } else {
                producto.agregarEspecificacion(e.getKey(), e.getValue(), orden);
            }
            orden++;
        }
        // En memoria quedan las existentes primero y las nuevas al final: se reordena por ORDEN
        // para que la respuesta del PUT sea idéntica al GET posterior (la BD ya lo devuelve así por @OrderBy).
        producto.getEspecificaciones().sort(Comparator.comparing(ProductoEspecificacion::getOrden));
    }

    /** "campo" o "campo,asc|desc". Campo y dirección se validan contra listas cerradas. */
    private Sort parsearSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id"));
        }
        String[] partes = sort.split(",", -1);
        String propiedad = partes.length >= 1 ? CAMPOS_ORDENABLES.get(partes[0].trim().toLowerCase(Locale.ROOT)) : null;
        String dir = partes.length == 2 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (partes.length > 2 || propiedad == null || !(dir.equals("asc") || dir.equals("desc"))) {
            throw new BadRequestException("Parámetro sort inválido. Use campo[,asc|desc] con campo en "
                    + new TreeSet<>(CAMPOS_ORDENABLES.keySet()));
        }
        Sort.Direction direccion = dir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direccion, propiedad), Sort.Order.asc("id"));
    }

    private Specification<Producto> filtros(String q, String categoria, String marca) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String patron = "%" + escaparLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), patron, '\\'),
                        cb.like(cb.lower(root.get("marca")), patron, '\\')));
            }
            if (categoria != null) {
                predicados.add(cb.equal(root.get("categoria"), categoria));
            }
            if (marca != null && !marca.isBlank()) {
                predicados.add(cb.equal(cb.lower(root.get("marca")), marca.trim().toLowerCase(Locale.ROOT)));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }

    private String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
