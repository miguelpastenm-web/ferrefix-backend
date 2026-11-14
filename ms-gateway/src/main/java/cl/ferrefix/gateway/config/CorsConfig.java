package cl.ferrefix.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS de toda la API. Vive SOLO en el gateway: los servicios no añaden cabeceras Access-Control-*.
 *  - El preflight (OPTIONS) se responde aquí y no se reenvía al servicio.
 *  - A las respuestas reales (también a los errores) se les agrega Access-Control-Allow-Origin.
 *  - Un origen o un método que no está en la lista recibe 403.
 * Misma política que tenía el monolito: orígenes de CORS_ALLOWED_ORIGINS, los métodos del CRUD y las cabeceras
 * Authorization y Content-Type.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(@Value("${ferrefix.cors.allowed-origins}") String allowedOrigins) {
        List<String> origenes = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList();
        if (origenes.isEmpty()) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS no puede quedar vacío: indique el origen del frontend (ej. http://localhost:5173)");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
