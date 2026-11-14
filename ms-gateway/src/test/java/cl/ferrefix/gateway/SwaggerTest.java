package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

/** Swagger UI vive en el gateway (un solo lugar, con selector por servicio) y lee el documento de cada servicio. */
class SwaggerTest extends GatewayTestBase {

    @Test
    void laInterfazLaSirveElGatewaySinPasarPorLosServicios() {
        cliente.get().uri("/swagger-ui/index.html").exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML);
        assertTrue(servicio.recibidas().isEmpty());
        assertTrue(auth.recibidas().isEmpty());
    }

    @Test
    void laRutaCortaRedirigeALaInterfaz() {
        cliente.get().uri("/swagger-ui.html").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", ubicacion -> assertTrue(ubicacion.contains("/swagger-ui/index.html"), ubicacion));
        assertTrue(servicio.recibidas().isEmpty());
    }

    @Test
    void elSelectorListaMsTiendaYMsAuthConSusDocumentos() {
        cliente.get().uri("/v3/api-docs/swagger-config").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls.length()").isEqualTo(2)
                .jsonPath("$.urls[?(@.name=='ms-tienda')].url").isEqualTo("/api-docs/ms-tienda")
                .jsonPath("$.urls[?(@.name=='ms-auth')].url").isEqualTo("/api-docs/ms-auth")
                .jsonPath("$['urls.primaryName']").isEqualTo("ms-tienda");
    }

    @Test
    void elDocumentoPropioDelGatewayEstaVacioYNoSeReenviaAServicioAlguno() {
        // El gateway no tiene endpoints: /v3/api-docs ya no es el documento de la API ni llega a ningún servicio.
        cliente.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.paths").isEmpty();
        assertTrue(servicio.recibidas().isEmpty());
        assertTrue(auth.recibidas().isEmpty());
    }
}
