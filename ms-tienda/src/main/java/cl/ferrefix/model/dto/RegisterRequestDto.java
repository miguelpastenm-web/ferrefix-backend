package cl.ferrefix.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @NotBlank(message = "El RUT es obligatorio")
        @Size(max = 12, message = "El RUT no puede superar 12 caracteres")
        String rut,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password,

        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        String telefono,

        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String direccion,

        @Size(max = 80, message = "La región no puede superar 80 caracteres")
        String region,

        @Size(max = 80, message = "La comuna no puede superar 80 caracteres")
        String comuna) {
}
