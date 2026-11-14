package cl.ferrefix.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Lo único editable de un arriendo: contacto y lugar de la faena. Unidad, días, fechas, tarifa y total no cambian. */
public record ActualizarArriendoRequestDto(
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
