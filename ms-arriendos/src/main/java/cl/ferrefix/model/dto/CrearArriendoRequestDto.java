package cl.ferrefix.model.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * El cliente indica QUÉ modelo, por cuántos días y desde cuándo, y dónde estará la faena.
 * La unidad, la tarifa, el total y los datos del cliente (RUT, nombre, email) los pone el servidor.
 */
public record CrearArriendoRequestDto(
        @NotNull(message = "La máquina es obligatoria")
        Long maquinaId,

        @NotNull(message = "Los días de arriendo son obligatorios")
        @Min(value = 1, message = "El arriendo mínimo es de 1 día")
        @Max(value = 90, message = "El máximo de arriendo es de 90 días")
        Integer diasArriendo,

        @NotNull(message = "La fecha de inicio es obligatoria (formato AAAA-MM-DD)")
        LocalDate fechaInicio,

        @NotBlank(message = "El teléfono de contacto es obligatorio")
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        @Pattern(regexp = "^[0-9+()\\- ]{8,20}$", message = "El teléfono solo admite números, +, espacios, guiones y paréntesis (8 a 20 caracteres)")
        String telefono,

        @NotBlank(message = "La dirección de la faena es obligatoria")
        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String direccionFaena,

        @NotBlank(message = "La región es obligatoria")
        @Size(max = 80, message = "La región no puede superar 80 caracteres")
        String region,

        @NotBlank(message = "La comuna es obligatoria")
        @Size(max = 80, message = "La comuna no puede superar 80 caracteres")
        String comuna) {
}
