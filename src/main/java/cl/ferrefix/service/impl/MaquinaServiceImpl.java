package cl.ferrefix.service.impl;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.*;
import cl.ferrefix.model.dto.MaquinaRequestDto;
import cl.ferrefix.model.dto.MaquinaResponseDto;
import cl.ferrefix.model.dto.UnidadResponseDto;
import cl.ferrefix.model.mapper.MaquinaMapper;
import cl.ferrefix.repository.MaquinaRepository;
import cl.ferrefix.repository.MaquinaUnidadRepository;
import cl.ferrefix.service.MaquinaService;
import cl.ferrefix.util.EspecificacionesUtil;
import cl.ferrefix.util.SortUtil;
import cl.ferrefix.util.Textos;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MaquinaServiceImpl implements MaquinaService {

    static final Map<String, String> CAMPOS_ORDENABLES = Map.of(
            "nombre", "nombre",
            "marca", "marca",
            "categoria", "categoria",
            "tarifa", "tarifaDia",
            "fecha", "createdAt");

    static final int MAX_UNIDADES_POR_ALTA = 20;
    private static final Pattern SUFIJO_UNIDAD = Pattern.compile("-U(\\d+)$");

    private final MaquinaRepository maquinaRepository;
    private final MaquinaUnidadRepository unidadRepository;
    private final MaquinaMapper mapper;

    // ---------------------------------------------------------------- lectura
    @Override
    @Transactional(readOnly = true)
    public List<MaquinaResponseDto> listar(String q, String categoria, boolean soloDisponibles, String sort) {
        Sort orden = SortUtil.parsear(sort, CAMPOS_ORDENABLES);
        List<Maquina> maquinas = maquinaRepository.findAll(filtros(q, categoria, soloDisponibles), orden);
        Map<Long, long[]> conteos = contar(maquinas.stream().map(Maquina::getId).toList());
        return maquinas.stream().map(m -> responder(m, conteos)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MaquinaResponseDto obtener(Long id) {
        Maquina m = buscar(id);
        return responder(m, contar(List.of(id)));
    }

    // ---------------------------------------------------------------- escritura de modelos
    @Override
    @Transactional
    public MaquinaResponseDto crear(MaquinaRequestDto request) {
        String codigo = request.codigo().trim();
        if (maquinaRepository.existsByCodigo(codigo)) {
            throw new DuplicateResourceException("Ya existe una máquina con el código " + codigo);
        }
        Map<String, String> specs = EspecificacionesUtil.normalizar(request.especificaciones());
        Maquina guardada = maquinaRepository.save(mapper.toEntity(request, codigo, specs));
        return mapper.toResponse(guardada, 0, 0); // recién creada: sin unidades => NO_DISPONIBLE hasta dar de alta una
    }

    @Override
    @Transactional
    public MaquinaResponseDto actualizar(Long id, MaquinaRequestDto request) {
        Maquina m = buscar(id);
        // El código es inmutable: las unidades se nombran con él (<código>-U01) y cambiarlo las dejaría con el prefijo viejo.
        if (!request.codigo().trim().equals(m.getCodigo())) {
            throw new ConflictException("El código de una máquina no se puede cambiar");
        }
        Map<String, String> specs = EspecificacionesUtil.normalizar(request.especificaciones());

        // Campo a campo: se conservan id, código, createdAt y estado (el estado lo decide el stock de unidades, no el PUT).
        m.setNombre(request.nombre().trim());
        m.setMarca(request.marca().trim());
        m.setCategoria(request.categoria().trim());
        m.setTarifaDia(request.tarifaDia());
        m.setIncluye(Textos.limpiar(request.incluye()));
        m.setImagenUrl(Textos.limpiar(request.imagenUrl()));
        sincronizarEspecificaciones(m, specs);
        m.setUpdatedAt(LocalDateTime.now());

        Maquina guardada = maquinaRepository.saveAndFlush(m);
        return responder(guardada, contar(List.of(id)));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Maquina m = bloquear(id);
        try {
            maquinaRepository.delete(m);
            maquinaRepository.flush(); // fuerza el DELETE aquí para traducir la violación de FK en un 409 claro
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("La máquina tiene arriendos registrados y no puede eliminarse");
        }
    }

    // ---------------------------------------------------------------- unidades
    @Override
    @Transactional(readOnly = true)
    public List<UnidadResponseDto> listarUnidades(Long maquinaId) {
        buscar(maquinaId);
        return unidadRepository.findByMaquinaIdOrderByCodigoAsc(maquinaId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public List<UnidadResponseDto> agregarUnidades(Long maquinaId, int cantidad) {
        if (cantidad < 1 || cantidad > MAX_UNIDADES_POR_ALTA) {
            throw new BadRequestException("La cantidad debe estar entre 1 y " + MAX_UNIDADES_POR_ALTA);
        }
        Maquina m = bloquear(maquinaId); // 1) siempre se bloquea el modelo primero

        int siguiente = unidadRepository.findByMaquinaIdOrderByCodigoAsc(maquinaId).stream()
                .map(u -> SUFIJO_UNIDAD.matcher(u.getCodigo()))
                .filter(Matcher::find)
                .mapToInt(mt -> Integer.parseInt(mt.group(1)))
                .max().orElse(0) + 1;

        List<MaquinaUnidad> nuevas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            MaquinaUnidad u = new MaquinaUnidad();
            u.setMaquina(m);
            u.setCodigo(String.format("%s-U%02d", m.getCodigo(), siguiente + i));
            u.setEstado(EstadoUnidad.DISPONIBLE);
            nuevas.add(u);
        }
        List<MaquinaUnidad> guardadas = unidadRepository.saveAll(nuevas);
        recalcularEstado(m);
        return guardadas.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public void eliminarUnidad(Long maquinaId, Long unidadId) {
        Maquina m = bloquear(maquinaId);
        MaquinaUnidad u = unidadRepository.findByIdAndMaquinaId(unidadId, maquinaId)
                .orElseThrow(() -> new ResourceNotFoundException("Unidad no encontrada: " + unidadId));
        if (u.getEstado() != EstadoUnidad.DISPONIBLE) {
            throw new ConflictException("La unidad " + u.getCodigo() + " está arrendada y no puede eliminarse");
        }
        try {
            unidadRepository.delete(u);
            unidadRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("La unidad " + u.getCodigo() + " tiene arriendos registrados y no puede eliminarse");
        }
        recalcularEstado(m);
    }

    // ---------------------------------------------------------------- helpers
    private Maquina buscar(Long id) {
        return maquinaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Máquina no encontrada: " + id));
    }

    /** SELECT ... FOR UPDATE del modelo: primer paso de toda operación que modifica sus unidades. */
    private Maquina bloquear(Long id) {
        return maquinaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Máquina no encontrada: " + id));
    }

    /** DISPONIBLE si queda al menos una unidad libre; si no, NO_DISPONIBLE. Se llama con el modelo ya bloqueado. */
    private void recalcularEstado(Maquina m) {
        long libres = unidadRepository.countByMaquinaIdAndEstado(m.getId(), EstadoUnidad.DISPONIBLE);
        m.setEstado(libres > 0 ? EstadoMaquina.DISPONIBLE : EstadoMaquina.NO_DISPONIBLE);
        m.setUpdatedAt(LocalDateTime.now());
    }

    /** maquinaId -> {total, disponibles}. Una sola consulta agrupada para todos los modelos pedidos. */
    private Map<Long, long[]> contar(Collection<Long> ids) {
        Map<Long, long[]> conteos = new HashMap<>();
        if (ids.isEmpty()) {
            return conteos;
        }
        for (Object[] fila : unidadRepository.contarPorMaquinaYEstado(ids)) {
            long[] c = conteos.computeIfAbsent((Long) fila[0], k -> new long[2]);
            long cantidad = ((Number) fila[2]).longValue();
            c[0] += cantidad;
            if (fila[1] == EstadoUnidad.DISPONIBLE) {
                c[1] += cantidad;
            }
        }
        return conteos;
    }

    private MaquinaResponseDto responder(Maquina m, Map<Long, long[]> conteos) {
        long[] c = conteos.getOrDefault(m.getId(), new long[2]);
        return mapper.toResponse(m, c[0], c[1]);
    }

    /** Actualiza por clave en el lugar (evita INSERT antes de DELETE contra el UNIQUE) y deja la lista ordenada por ORDEN. */
    private void sincronizarEspecificaciones(Maquina m, Map<String, String> nuevas) {
        Map<String, MaquinaEspecificacion> existentes = new HashMap<>();
        m.getEspecificaciones().forEach(e -> existentes.put(e.getClave(), e));
        m.getEspecificaciones().removeIf(e -> !nuevas.containsKey(e.getClave()));

        int orden = 0;
        for (Map.Entry<String, String> e : nuevas.entrySet()) {
            MaquinaEspecificacion actual = existentes.get(e.getKey());
            if (actual != null) {
                actual.setValor(e.getValue());
                actual.setOrden(orden);
            } else {
                m.agregarEspecificacion(e.getKey(), e.getValue(), orden);
            }
            orden++;
        }
        m.getEspecificaciones().sort(Comparator.comparing(MaquinaEspecificacion::getOrden));
    }

    private Specification<Maquina> filtros(String q, String categoria, boolean soloDisponibles) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String patron = "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), patron, '\\'),
                        cb.like(cb.lower(root.get("marca")), patron, '\\'),
                        cb.like(cb.lower(root.get("codigo")), patron, '\\')));
            }
            if (categoria != null && !categoria.isBlank()) {
                predicados.add(cb.equal(cb.lower(root.get("categoria")), categoria.trim().toLowerCase(Locale.ROOT)));
            }
            if (soloDisponibles) {
                predicados.add(cb.equal(root.get("estado"), EstadoMaquina.DISPONIBLE));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
