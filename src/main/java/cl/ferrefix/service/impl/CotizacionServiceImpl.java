package cl.ferrefix.service.impl;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.Cotizacion;
import cl.ferrefix.model.EstadoCotizacion;
import cl.ferrefix.model.TipoProyecto;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.*;
import cl.ferrefix.model.mapper.CotizacionMapper;
import cl.ferrefix.repository.CotizacionRepository;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.CotizacionService;
import cl.ferrefix.util.RutValidator;
import cl.ferrefix.util.Textos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CotizacionServiceImpl implements CotizacionService {

    private static final Sort MAS_NUEVAS_PRIMERO = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final CotizacionRepository cotizacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final CotizacionMapper mapper;
    private final Clock clock; // hora de Chile (ver ClockConfig)

    // ---------------------------------------------------------------- crear (público)
    @Override
    @Transactional
    public CotizacionCreadaResponseDto crear(String emailAutenticado, CrearCotizacionRequestDto request) {
        // Las anotaciones del DTO validan formato y largos; aquí van las reglas que dependen del contenido recortado.
        String nombre = request.nombre().trim();
        if (nombre.length() < 3) {
            throw new BadRequestException("El nombre o razón social debe tener al menos 3 caracteres");
        }
        String rut = RutValidator.normalizar(request.rut());
        if (rut == null || !RutValidator.esValido(rut)) {
            throw new BadRequestException("El RUT ingresado no es válido");
        }
        String tipo = TipoProyecto.canonica(request.tipoProyecto())
                .orElseThrow(() -> new BadRequestException("Tipo de proyecto inválido. Valores permitidos: " + String.join(" | ", TipoProyecto.TODOS)));
        String detalle = request.detalle().trim();
        if (detalle.length() < 10) {
            throw new BadRequestException("Describe tu requerimiento con al menos 10 caracteres");
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        Cotizacion c = new Cotizacion();
        c.setUsuarioId(usuarioDelToken(emailAutenticado));
        c.setNombre(nombre);
        c.setRut(rut);
        c.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        c.setTelefono(request.telefono().trim());
        c.setTipoProyecto(tipo);
        c.setRegion(request.region().trim());
        c.setDetalle(detalle);
        c.setDespachoObra(request.despachoObra() == null || request.despachoObra());
        c.setEstado(EstadoCotizacion.PENDIENTE);
        c.setCreatedAt(ahora);
        c.setUpdatedAt(ahora);
        return mapper.toCreada(cotizacionRepository.save(c));
    }

    /** Un token válido asocia el usuario; si el usuario ya no existe o está inactivo, no falla: queda anónima. */
    private Long usuarioDelToken(String email) {
        if (email == null) {
            return null;
        }
        return usuarioRepository.findByEmailIgnoreCase(email).filter(Usuario::isActivo).map(Usuario::getId).orElse(null);
    }

    // ---------------------------------------------------------------- ADMIN
    @Override
    @Transactional(readOnly = true)
    public List<CotizacionResponseDto> listar(String estado) {
        List<Cotizacion> resultado;
        if (estado == null || estado.isBlank()) {
            resultado = cotizacionRepository.findAll(MAS_NUEVAS_PRIMERO);
        } else {
            EstadoCotizacion filtro;
            try {
                filtro = EstadoCotizacion.valueOf(estado.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Estado inválido. Valores permitidos: " + Arrays.toString(EstadoCotizacion.values()));
            }
            resultado = cotizacionRepository.findByEstado(filtro, MAS_NUEVAS_PRIMERO);
        }
        return resultado.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CotizacionResponseDto obtener(Long id) {
        return mapper.toResponse(buscar(id));
    }

    @Override
    @Transactional
    public CotizacionResponseDto actualizar(Long id, ActualizarCotizacionRequestDto request) {
        if (request.estado() == null && request.notaAdmin() == null) {
            throw new BadRequestException("Debe enviar estado y/o notaAdmin");
        }
        Cotizacion c = buscar(id);
        if (request.estado() != null) {
            c.setEstado(request.estado());            // cualquier estado a cualquier otro; repetir el actual no es error
        }
        if (request.notaAdmin() != null) {
            c.setNotaAdmin(Textos.limpiar(request.notaAdmin()));   // vacía o en blanco = borrar la nota
        }
        c.setUpdatedAt(LocalDateTime.now(clock));
        return mapper.toResponse(cotizacionRepository.save(c));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        cotizacionRepository.delete(buscar(id));
    }

    private Cotizacion buscar(Long id) {
        return cotizacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cotización no encontrada: " + id));
    }
}
