package cl.ferrefix.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** Formato único de error para toda la API (incluidos 401 y 403 del filtro de seguridad). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDto(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details) {

    public static ErrorResponseDto of(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponseDto(LocalDateTime.now(), status, error, message, path, details);
    }
}
