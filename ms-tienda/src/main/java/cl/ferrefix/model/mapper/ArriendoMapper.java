package cl.ferrefix.model.mapper;

import cl.ferrefix.model.Arriendo;
import cl.ferrefix.model.Maquina;
import cl.ferrefix.model.MaquinaUnidad;
import cl.ferrefix.model.dto.ArriendoResponseDto;
import org.springframework.stereotype.Component;

@Component
public class ArriendoMapper {

    /** @param incluirCliente true solo para ADMIN (RUT, nombre y email del arrendatario, copiados al arrendar). */
    public ArriendoResponseDto toResponse(Arriendo a, boolean incluirCliente) {
        MaquinaUnidad unidad = a.getUnidad();
        Maquina m = unidad.getMaquina();
        return new ArriendoResponseDto(
                a.getId(),
                String.format("ARR-%06d", a.getId()),
                a.getEstado().name(),
                new ArriendoResponseDto.MaquinaResumen(m.getId(), m.getCodigo(), m.getNombre(), m.getMarca(), m.getImagenUrl()),
                unidad.getCodigo(),
                a.getTarifaDia(),
                a.getDias(),
                a.getFechaInicio(),
                a.getFechaFin(),
                a.getTotal(),
                a.getTelefonoContacto(),
                a.getDireccionFaena(),
                a.getRegionFaena(),
                a.getComunaFaena(),
                a.getFechaDevolucion(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                incluirCliente ? new ArriendoResponseDto.ClienteResumen(a.getUsuarioId(), a.getClienteRut(), a.getClienteNombre(), a.getClienteEmail()) : null);
    }
}
