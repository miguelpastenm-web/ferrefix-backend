package cl.ferrefix.model.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Vista pública del usuario. Nunca incluye el hash de la contraseña. */
public record UsuarioResponseDto(
        Long id,
        String rut,
        String nombre,
        String email,
        String telefono,
        String direccion,
        String region,
        String comuna,
        boolean activo,
        List<String> roles,
        LocalDateTime createdAt) {
}
