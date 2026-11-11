package cl.ferrefix.security;

import cl.ferrefix.model.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** Escribe ErrorResponseDto como JSON desde los handlers de seguridad (que corren fuera de los controllers). */
@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String mensaje)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponseDto cuerpo = ErrorResponseDto.of(status.value(), status.getReasonPhrase(), mensaje,
                request.getRequestURI(), null);
        response.getWriter().write(objectMapper.writeValueAsString(cuerpo));
    }
}
