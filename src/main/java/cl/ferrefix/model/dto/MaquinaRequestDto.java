package cl.ferrefix.model.dto;

import jakarta.validation.constraints.*;

import java.util.Map;

/** Datos editables de un modelo de máquina. El estado NO se envía: depende de las unidades libres. */
public record MaquinaRequestDto(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código no puede superar 30 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "El código solo admite letras, números, punto, guion y guion bajo")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
        String nombre,

        @NotBlank(message = "La marca es obligatoria")
        @Size(max = 60, message = "La marca no puede superar 60 caracteres")
        String marca,

        @NotBlank(message = "La categoría es obligatoria")
        @Size(max = 60, message = "La categoría no puede superar 60 caracteres")
        String categoria,

        @NotNull(message = "La tarifa por día es obligatoria")
        @Min(value = 1, message = "La tarifa por día debe ser mayor a 0")
        @Max(value = 99_999_999, message = "La tarifa por día supera el máximo permitido")
        Integer tarifaDia,

        @Size(max = 500, message = "El campo 'incluye' no puede superar 500 caracteres")
        String incluye,

        @Size(max = 300, message = "La URL de la imagen no puede superar 300 caracteres")
        @Pattern(regexp = "^(/|https?://).*$|^$", message = "La URL de la imagen debe ser relativa (/media/...) o http(s)")
        String imagenUrl,

        @Size(max = 30, message = "Máximo 30 especificaciones")
        Map<String, String> especificaciones) {
}
