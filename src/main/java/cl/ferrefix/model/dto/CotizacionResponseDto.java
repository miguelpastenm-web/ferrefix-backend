package cl.ferrefix.model.dto;

import java.time.LocalDateTime;

/** Vista completa de una cotización. Solo la entregan los endpoints de ADMIN. */
public record CotizacionResponseDto(
        Long id,
        String codigo,
        Long usuarioId,
        String nombre,
        String rut,
        String email,
        String telefono,
        String tipoProyecto,
        String region,
        String detalle,
        boolean despachoObra,
        String estado,
        String notaAdmin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
