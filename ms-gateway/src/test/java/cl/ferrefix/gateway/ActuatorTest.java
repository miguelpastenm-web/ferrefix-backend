package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Actuator: solo /actuator/health, público y únicamente con el estado. Nada más queda expuesto. */
class ActuatorTest extends GatewayTestBase {

    @Test
    void healthEsPublicoYSoloDevuelveElEstado() {
        cliente.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components").doesNotExist()
                .jsonPath("$.details").doesNotExist()
                .jsonPath("$.length()").isEqualTo(1);
        assertTrue(servicio.recibidas().isEmpty(), "El health es el del gateway: no se reenvía a ms-tienda");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/gateway/routes",
            "/actuator/heapdump", "/actuator/configprops", "/actuator/mappings", "/actuator/info"})
    void elRestoDeActuatorNoExiste(String ruta) {
        cliente.get().uri(ruta).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.path").isEqualTo(ruta);
        assertTrue(servicio.recibidas().isEmpty());
    }
}
