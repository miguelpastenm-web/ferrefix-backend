package cl.ferrefix.model.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record MaquinaResponseDto(
        Long id,
        String codigo,
        String nombre,
        String marca,
        String categoria,
        Integer tarifaDia,
        String estado,
        String incluye,
        String imagenUrl,
        Map<String, String> especificaciones,
        long unidadesTotales,
        long unidadesDisponibles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
