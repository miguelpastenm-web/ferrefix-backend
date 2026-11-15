package cl.ferrefix.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj de negocio en hora de Chile: "hoy" para validar fechas de arriendo es el de America/Santiago,
 * no el del contenedor (normalmente UTC). Los tests inyectan un Clock fijo.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA_CHILE);
    }
}
