package cl.ferrefix.model.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ProductoResponseDto(
        Long id,
        String sku,
        String nombre,
        String marca,
        String categoria,
        Integer precioVenta,
        Integer stock,
        String estado,
        String imagenUrl,
        String imagenOfertaUrl,
        String descripcion,
        Map<String, String> especificaciones,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
