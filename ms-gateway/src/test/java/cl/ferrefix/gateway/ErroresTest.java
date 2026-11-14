package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los errores que nacen en el gateway salen en JSON con el mismo formato que los de los servicios (ErrorResponseDto). */
class ErroresTest extends GatewayTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"/ruta-que-no-existe", "/api/v2/productos", "/api", "/"})
    void unaRutaQueNoEsDeNingunServicioDevuelve404EnJson(String ruta) {
        cliente.get().uri(ruta).exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.message").isEqualTo("La ruta solicitada no existe")
                .jsonPath("$.path").isEqualTo(ruta)
                .jsonPath("$.details").doesNotExist();
        assertTrue(servicio.recibidas().isEmpty());
    }

    @Test
    void siElServicioNoRespondeATiempoDevuelve504EnJson() {
        // El servicio falso tarda 5 s y en los tests el response-timeout es de 2 s.
        cliente.get().uri("/api/v1/lento").exchange()
                .expectStatus().isEqualTo(504)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.error").isEqualTo("Gateway Timeout")
                .jsonPath("$.message").isEqualTo("El servicio tardó demasiado en responder. Intente nuevamente")
                .jsonPath("$.path").isEqualTo("/api/v1/lento");
    }

    @Test
    void elErrorNoFiltraDetallesInternos() {
        // Ni el nombre de la excepción ni la dirección interna del servicio deben llegar al cliente.
        String cuerpo = new String(cliente.get().uri("/api/v1/lento").exchange()
                .expectStatus().isEqualTo(504)
                .expectBody().returnResult().getResponseBody());
        assertTrue(!cuerpo.contains("Exception") && !cuerpo.contains("127.0.0.1") && !cuerpo.contains("trace"), cuerpo);
    }
}
