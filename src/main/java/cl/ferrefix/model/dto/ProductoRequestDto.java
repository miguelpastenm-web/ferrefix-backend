package cl.ferrefix.model.dto;

import jakarta.validation.constraints.*;

import java.util.Map;

/** Datos editables de un producto (sirve para crear y para reemplazar en PUT). El estado NO se envía: lo calcula el servidor. */
public record ProductoRequestDto(
        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 30, message = "El SKU no puede superar 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "El SKU solo admite letras, números, punto, guion y guion bajo")
        String sku,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,

        @NotBlank(message = "La marca es obligatoria")
        @Size(max = 60, message = "La marca no puede superar 60 caracteres")
        String marca,

        @NotBlank(message = "La categoría es obligatoria")
        String categoria,

        @NotNull(message = "El precio es obligatorio")
        @Min(value = 1, message = "El precio debe ser mayor a 0")
        @Max(value = 999_999_999, message = "El precio supera el máximo permitido")
        Integer precioVenta,

        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo")
        @Max(value = 1_000_000, message = "El stock supera el máximo permitido")
        Integer stock,

        @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
        String descripcion,

        @Size(max = 300, message = "La URL de la imagen no puede superar 300 caracteres")
        @Pattern(regexp = "^(/|https?://).*$|^$", message = "La URL de la imagen debe ser relativa (/media/...) o http(s)")
        String imagenUrl,

        @Size(max = 300, message = "La URL de la imagen de oferta no puede superar 300 caracteres")
        @Pattern(regexp = "^(/|https?://).*$|^$", message = "La URL de la imagen de oferta debe ser relativa (/media/...) o http(s)")
        String imagenOfertaUrl,

        @Size(max = 30, message = "Máximo 30 especificaciones")
        Map<String, String> especificaciones) {
}
