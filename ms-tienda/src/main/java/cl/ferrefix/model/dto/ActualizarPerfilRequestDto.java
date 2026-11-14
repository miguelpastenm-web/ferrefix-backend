package cl.ferrefix.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edición del propio perfil (PUT /api/v1/users/me). Reemplaza estos cinco campos: un opcional omitido o en blanco
 * se guarda como null. Rut, email, contraseña, roles y estado NO se pueden cambiar por aquí (no existen en el DTO,
 * así que un cuerpo que los traiga se ignora). Los largos son los de las columnas de USUARIOS (V1).
 */
public record ActualizarPerfilRequestDto(
        // MISMAS reglas que el nombre del registro (RegisterRequestDto): ReglasDeNombreTest obliga a que sigan iguales.
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,

        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        @Pattern(regexp = "^$|^[0-9+()\\- ]{8,20}$", message = "El teléfono solo admite números, +, espacios, guiones y paréntesis (8 a 20 caracteres)")
        String telefono,

        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String direccion,

        @Size(max = 80, message = "La región no puede superar 80 caracteres")
        String region,

        @Size(max = 80, message = "La comuna no puede superar 80 caracteres")
        String comuna) {
}
