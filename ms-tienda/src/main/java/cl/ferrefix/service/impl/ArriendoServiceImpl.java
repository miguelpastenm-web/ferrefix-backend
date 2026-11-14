package cl.ferrefix.service.impl;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.*;
import cl.ferrefix.model.dto.ActualizarArriendoRequestDto;
import cl.ferrefix.model.dto.ArriendoResponseDto;
import cl.ferrefix.model.dto.CrearArriendoRequestDto;
import cl.ferrefix.model.mapper.ArriendoMapper;
import cl.ferrefix.repository.ArriendoRepository;
import cl.ferrefix.repository.MaquinaUnidadRepository;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.ArriendoService;
import cl.ferrefix.service.MaquinaDisponibilidad;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Arriendos. Toda operación que cambia el estado de una unidad sigue el mismo orden, que es lo que evita
 * dos arriendos simultáneos de la misma unidad:
 *   1) SELECT ... FOR UPDATE sobre el modelo (serializa las operaciones de ese modelo);
 *   2) UPDATE atómico de la unidad exigiendo que afecte exactamente 1 fila;
 *   3) recalcular el estado del modelo en la misma transacción.
 * Y, como red de seguridad final, el índice único parcial UX_ARRIENDOS_UNIDAD_VIGENTE en la base.
 */
@Service
@RequiredArgsConstructor
public class ArriendoServiceImpl implements ArriendoService {

    static final int MAX_DIAS = 90;
    static final int MAX_DIAS_DE_ANTICIPACION = 365;

    private final ArriendoRepository arriendoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MaquinaUnidadRepository unidadRepository;
    private final MaquinaDisponibilidad disponibilidad;
    private final ArriendoMapper mapper;
    private final Clock clock; // hora de Chile (ver ClockConfig)

    // ---------------------------------------------------------------- crear
    @Override
    @Transactional
    public ArriendoResponseDto crear(String emailCliente, CrearArriendoRequestDto request) {
        // Validaciones que no necesitan base de datos: fallan antes de tomar ningún bloqueo.
        validarPeriodo(request.diasArriendo(), request.fechaInicio());
        Usuario cliente = usuarioRepository.findByEmailIgnoreCase(emailCliente)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Usuario no válido"));

        Maquina maquina = disponibilidad.bloquear(request.maquinaId());                      // 1) bloquear el modelo
        MaquinaUnidad unidad = unidadRepository.findById(tomarPrimeraUnidadLibre(maquina.getId()))
                .orElseThrow(() -> new IllegalStateException("La unidad reservada no existe"));  // 2) unidad ya ARRENDADA

        LocalDateTime ahora = LocalDateTime.now(clock);
        Arriendo a = new Arriendo();
        a.setUnidad(unidad);
        a.setUsuario(cliente);
        a.setTarifaDia(maquina.getTarifaDia());                                  // tarifa copiada del modelo, no del cliente
        a.setDias(request.diasArriendo());
        a.setFechaInicio(request.fechaInicio());
        a.setFechaFin(request.fechaInicio().plusDays(request.diasArriendo() - 1L));
        a.setTotal((long) maquina.getTarifaDia() * request.diasArriendo());
        a.setTelefonoContacto(request.telefono().trim());
        a.setDireccionFaena(request.direccionFaena().trim());
        a.setRegionFaena(request.region().trim());
        a.setComunaFaena(request.comuna().trim());
        a.setEstado(EstadoArriendo.VIGENTE);
        a.setCreatedAt(ahora);
        a.setUpdatedAt(ahora);
        Arriendo guardado = arriendoRepository.save(a);

        disponibilidad.recalcularEstado(maquina);                                // 3) estado del modelo
        return mapper.toResponse(guardado, false);
    }

    /** Primera unidad DISPONIBLE por código. Cada intento es un UPDATE condicionado que debe afectar 1 fila. */
    private Long tomarPrimeraUnidadLibre(Long maquinaId) {
        for (Long unidadId : unidadRepository.idsPorEstado(maquinaId, EstadoUnidad.DISPONIBLE)) {
            if (unidadRepository.cambiarEstado(unidadId, EstadoUnidad.DISPONIBLE, EstadoUnidad.ARRENDADA) == 1) {
                return unidadId;
            }
        }
        throw new ConflictException("No hay unidades disponibles de esta máquina");
    }

    private void validarPeriodo(int dias, LocalDate fechaInicio) {
        if (dias < 1 || dias > MAX_DIAS) {
            throw new BadRequestException("Los días de arriendo deben estar entre 1 y " + MAX_DIAS);
        }
        LocalDate hoy = LocalDate.now(clock); // America/Santiago, no la hora del contenedor
        if (fechaInicio.isBefore(hoy)) {
            throw new BadRequestException("La fecha de inicio no puede ser anterior a hoy (" + hoy + ")");
        }
        if (fechaInicio.isAfter(hoy.plusDays(MAX_DIAS_DE_ANTICIPACION))) {
            throw new BadRequestException("La fecha de inicio no puede superar " + MAX_DIAS_DE_ANTICIPACION + " días desde hoy");
        }
    }

    // ---------------------------------------------------------------- lectura
    @Override
    @Transactional(readOnly = true)
    public List<ArriendoResponseDto> misArriendos(String emailCliente) {
        Usuario cliente = usuarioRepository.findByEmailIgnoreCase(emailCliente)
                .orElseThrow(() -> new BadCredentialsException("Usuario no válido"));
        return arriendoRepository.findByUsuarioIdOrderByCreatedAtDescIdDesc(cliente.getId()).stream()
                .map(a -> mapper.toResponse(a, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ArriendoResponseDto obtener(Long id, String email, boolean esAdmin) {
        Arriendo a = arriendoRepository.findDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Arriendo no encontrado: " + id));
        if (!esAdmin && !a.getUsuario().getEmail().equalsIgnoreCase(email)) {
            // Se responde igual que si no existiera: no se revela que ese id pertenece a otra persona.
            throw new ResourceNotFoundException("Arriendo no encontrado: " + id);
        }
        return mapper.toResponse(a, esAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArriendoResponseDto> listar(String estado, Long usuarioId, Long maquinaId) {
        EstadoArriendo estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            try {
                estadoFiltro = EstadoArriendo.valueOf(estado.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Estado inválido. Valores permitidos: VIGENTE, FINALIZADO");
            }
        }
        final EstadoArriendo filtro = estadoFiltro;
        Specification<Arriendo> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (filtro != null) {
                p.add(cb.equal(root.get("estado"), filtro));
            }
            if (usuarioId != null) {
                p.add(cb.equal(root.get("usuario").get("id"), usuarioId));
            }
            if (maquinaId != null) {
                p.add(cb.equal(root.get("unidad").get("maquina").get("id"), maquinaId));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return arriendoRepository.findAll(spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).stream()
                .map(a -> mapper.toResponse(a, true)).toList();
    }

    // ---------------------------------------------------------------- ADMIN: editar / devolver / eliminar
    @Override
    @Transactional
    public ArriendoResponseDto actualizar(Long id, ActualizarArriendoRequestDto request) {
        Arriendo a = arriendoRepository.findDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Arriendo no encontrado: " + id));
        if (a.getEstado() != EstadoArriendo.VIGENTE) {
            throw new ConflictException("Solo se pueden editar arriendos vigentes");
        }
        // Solo contacto y faena. Unidad, días, fechas, tarifa y total no se tocan (CHECK total = tarifa x días).
        a.setTelefonoContacto(request.telefono().trim());
        a.setDireccionFaena(request.direccionFaena().trim());
        a.setRegionFaena(request.region().trim());
        a.setComunaFaena(request.comuna().trim());
        a.setUpdatedAt(LocalDateTime.now(clock));
        return mapper.toResponse(arriendoRepository.save(a), true);
    }

    @Override
    @Transactional
    public ArriendoResponseDto devolver(Long id) {
        Arriendo a = cargarBloqueandoElModelo(id);
        if (a.getEstado() != EstadoArriendo.VIGENTE) {
            throw new ConflictException("El arriendo ya fue devuelto");
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        a.setEstado(EstadoArriendo.FINALIZADO);
        a.setFechaDevolucion(ahora);
        a.setUpdatedAt(ahora);
        liberarUnidad(a);
        Arriendo guardado = arriendoRepository.save(a);
        disponibilidad.recalcularEstado(a.getUnidad().getMaquina());
        return mapper.toResponse(guardado, true);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Arriendo a = cargarBloqueandoElModelo(id);
        if (a.getEstado() == EstadoArriendo.VIGENTE) {
            liberarUnidad(a); // borrar un arriendo vigente libera la unidad
        }
        arriendoRepository.delete(a);
        disponibilidad.recalcularEstado(a.getUnidad().getMaquina());
    }

    /**
     * Averigua el modelo del arriendo con una consulta escalar, lo BLOQUEA y recién entonces lee el arriendo,
     * para decidir con su estado actual (otra operación pudo cambiarlo mientras esperábamos el bloqueo).
     */
    private Arriendo cargarBloqueandoElModelo(Long arriendoId) {
        Long maquinaId = arriendoRepository.findMaquinaIdById(arriendoId)
                .orElseThrow(() -> new ResourceNotFoundException("Arriendo no encontrado: " + arriendoId));
        disponibilidad.bloquear(maquinaId);
        return arriendoRepository.findDetalleById(arriendoId)
                .orElseThrow(() -> new ResourceNotFoundException("Arriendo no encontrado: " + arriendoId));
    }

    private void liberarUnidad(Arriendo a) {
        int filas = unidadRepository.cambiarEstado(a.getUnidad().getId(), EstadoUnidad.ARRENDADA, EstadoUnidad.DISPONIBLE);
        if (filas != 1) {
            // Un arriendo VIGENTE con su unidad ya libre es una inconsistencia de datos: no se oculta.
            throw new IllegalStateException("La unidad " + a.getUnidad().getCodigo() + " no estaba marcada como arrendada");
        }
    }
}
