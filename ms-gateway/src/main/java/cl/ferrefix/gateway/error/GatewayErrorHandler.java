package cl.ferrefix.gateway.error;

import io.netty.channel.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Errores que nacen EN el gateway, siempre en JSON con el formato de ErrorResponseDto:
 *  - 404: la ruta no pertenece a ningún servicio.
 *  - 502: no se pudo conectar con el servicio (caído, reiniciándose o cortó la conexión).
 *  - 504: el servicio no respondió dentro del tiempo máximo.
 * Los errores que responde el propio servicio (400, 401, 403, 404, 409...) NO pasan por aquí: se reenvían tal cual.
 * Ninguna excepción se descarta en silencio: todas quedan en el log (las 5xx con su traza).
 * El orden -2 lo ubica antes del manejador por defecto de Spring Boot (-1), que respondería con otro formato.
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        String metodo = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();

        if (response.isCommitted()) {
            // Ya se empezó a enviar la respuesta: no se puede cambiar. Se registra y se deja que el servidor corte.
            log.error("Error con la respuesta ya iniciada en {} {}", metodo, path, ex);
            return Mono.error(ex);
        }

        HttpStatus status = resolverEstado(ex);
        String mensaje = mensajePara(status);
        if (status.is5xxServerError()) {
            log.error("{} {} -> {} ({})", metodo, path, status.value(), mensaje, ex);
        } else {
            log.warn("{} {} -> {} ({}): {}", metodo, path, status.value(), mensaje, ex.toString());
        }

        byte[] cuerpo = objectMapper.writeValueAsBytes(
                ErrorResponseDto.of(status.value(), status.getReasonPhrase(), mensaje, path));
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(cuerpo.length);
        DataBuffer buffer = response.bufferFactory().wrap(cuerpo);
        return response.writeWith(Mono.just(buffer));
    }

    /** Recorre la cadena de causas: Netty y Reactor suelen envolver la excepción original. */
    static HttpStatus resolverEstado(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            // Incluye el 404 de "ninguna ruta coincide" y el 504 que arma el gateway cuando vence response-timeout.
            HttpStatusCode codigo = rse.getStatusCode();
            HttpStatus status = HttpStatus.resolve(codigo.value());
            return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        for (Throwable causa = ex; causa != null; causa = causa.getCause() == causa ? null : causa.getCause()) {
            if (causa instanceof ConnectTimeoutException || causa instanceof TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (causa instanceof IOException) {
                // ConnectException (conexión rechazada), UnknownHostException, conexión cerrada antes de responder...
                return HttpStatus.BAD_GATEWAY;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    static String mensajePara(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "La ruta solicitada no existe";
            case BAD_GATEWAY -> "El servicio no está disponible en este momento. Intente nuevamente en unos segundos";
            case GATEWAY_TIMEOUT -> "El servicio tardó demasiado en responder. Intente nuevamente";
            case SERVICE_UNAVAILABLE -> "El servicio no está disponible en este momento";
            case METHOD_NOT_ALLOWED -> "Método no permitido para esta ruta";
            default -> status.is5xxServerError() ? "Error interno del gateway" : status.getReasonPhrase();
        };
    }
}
