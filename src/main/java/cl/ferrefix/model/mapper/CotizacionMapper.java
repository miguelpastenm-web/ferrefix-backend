package cl.ferrefix.model.mapper;

import cl.ferrefix.model.Cotizacion;
import cl.ferrefix.model.dto.CotizacionCreadaResponseDto;
import cl.ferrefix.model.dto.CotizacionResponseDto;
import org.springframework.stereotype.Component;

@Component
public class CotizacionMapper {

    public static String codigo(Long id) {
        return String.format("COT-%06d", id);
    }

    public CotizacionCreadaResponseDto toCreada(Cotizacion c) {
        return new CotizacionCreadaResponseDto(c.getId(), codigo(c.getId()), c.getEstado().name(), c.getCreatedAt());
    }

    public CotizacionResponseDto toResponse(Cotizacion c) {
        return new CotizacionResponseDto(c.getId(), codigo(c.getId()), c.getUsuarioId(), c.getNombre(), c.getRut(),
                c.getEmail(), c.getTelefono(), c.getTipoProyecto(), c.getRegion(), c.getDetalle(), c.isDespachoObra(),
                c.getEstado().name(), c.getNotaAdmin(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
