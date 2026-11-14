package cl.ferrefix.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mismo formato de error que usan los servicios (ErrorResponseDto de ms-tienda), para que el frontend lea igual un
 * error del gateway (ruta inexistente, servicio caído, tiempo agotado) que uno del servicio.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDto(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details) {

    public static ErrorResponseDto of(int status, String error, String message, String path) {
        return new ErrorResponseDto(LocalDateTime.now(), status, error, message, path, null);
    }
}
