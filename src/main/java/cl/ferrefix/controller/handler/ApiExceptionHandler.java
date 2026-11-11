package cl.ferrefix.controller.handler;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .sorted()
                .toList();
        return construir(HttpStatus.BAD_REQUEST, "Datos de entrada inválidos", req, detalles);
    }

    @ExceptionHandler({BadRequestException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDto> solicitudInvalida(Exception ex, HttpServletRequest req) {
        String mensaje = ex instanceof BadRequestException ? ex.getMessage() : "Solicitud mal formada";
        return construir(HttpStatus.BAD_REQUEST, mensaje, req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> credenciales(BadCredentialsException ex, HttpServletRequest req) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
    }

    /** Se relanza para que lo resuelva RestAccessDeniedHandler (403 en JSON) y no caiga en el 500 genérico. */
    @ExceptionHandler(AccessDeniedException.class)
    public void accesoDenegado(AccessDeniedException ex) {
        throw ex;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> noEncontrado(ResourceNotFoundException ex, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler({DuplicateResourceException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponseDto> conflicto(Exception ex, HttpServletRequest req) {
        String mensaje = ex instanceof DuplicateResourceException
                ? ex.getMessage() : "La operación viola una restricción de datos (registro duplicado o en uso)";
        return construir(HttpStatus.CONFLICT, mensaje, req, null);
    }

    /**
     * Errores propios de Spring MVC (ruta inexistente, método o content-type no soportado, falta un parámetro).
     * Todas implementan ErrorResponse, así que se respeta el status que trae la excepción (404, 405, 415, 400)
     * y sus cabeceras (p. ej. "Allow" en el 405). Sin este handler los captura el genérico y devuelven 500.
     */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponseDto> errorDeSpringMvc(Exception ex, HttpServletRequest req) {
        ErrorResponse errorResponse = (ErrorResponse) ex;
        HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
        String mensaje = switch (ex) {
            case NoResourceFoundException e -> "Recurso no encontrado";
            case HttpRequestMethodNotSupportedException e -> "Método HTTP no permitido para este recurso";
            case HttpMediaTypeNotSupportedException e -> "Tipo de contenido no soportado";
            case MissingServletRequestParameterException e -> "Falta el parámetro obligatorio: " + e.getParameterName();
            default -> status.getReasonPhrase();
        };
        return ResponseEntity.status(status)
                .headers(errorResponse.getHeaders())
                .body(ErrorResponseDto.of(status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req, null);
    }

    private ResponseEntity<ErrorResponseDto> construir(HttpStatus status, String mensaje, HttpServletRequest req,
                                                       List<String> detalles) {
        return ResponseEntity.status(status)
                .body(ErrorResponseDto.of(status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI(), detalles));
    }
}
