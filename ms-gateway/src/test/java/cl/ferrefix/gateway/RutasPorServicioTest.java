package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

/** Reparto de rutas entre servicios: /api/v1/auth/** y /api/v1/users/** son de ms-auth; el resto de la API, de ms-tienda. */
class RutasPorServicioTest extends GatewayTestBase {

    @ParameterizedTest
    @CsvSource({
            "POST,/api/v1/auth/login",
            "POST,/api/v1/auth/register",
            "POST,/api/v1/auth/refresh",
            "GET,/api/v1/auth/me",
            "PUT,/api/v1/users/me"})
    void autenticacionYPerfilVanAMsAuth(String metodo, String ruta) {
        cliente.method(HttpMethod.valueOf(metodo)).uri(ruta)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-auth").jsonPath("$.ruta").isEqualTo(ruta);
        assertEquals(metodo, auth.ultima().metodo());
        assertEquals(ruta, auth.ultima().uri());
        assertTrue(servicio.recibidas().isEmpty(), "Ninguna de estas rutas es de ms-tienda");
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/v1/productos",
            "GET,/api/v1/ordenes/mis-ordenes",
            "POST,/api/v1/ordenes",
            "POST,/api/v1/arriendos",
            "POST,/api/v1/cotizaciones",
            "POST,/api/v1/imagenes",
            "GET,/media/productos/taladro-percutor.webp"})
    void elRestoDeLaApiVaAMsTienda(String metodo, String ruta) {
        cliente.method(HttpMethod.valueOf(metodo)).uri(ruta)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        assertEquals(ruta, servicio.ultima().uri());
        assertTrue(auth.recibidas().isEmpty(), "Ninguna de estas rutas es de ms-auth");
    }

    @Test
    void unPrefijoParecidoPeroDistintoNoVaAMsAuth() {
        // "/api/v1/authors" no es "/api/v1/auth/**": lo atiende la ruta general (ms-tienda).
        cliente.get().uri("/api/v1/authors").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        assertTrue(auth.recibidas().isEmpty());
    }

    @Test
    void elTokenYElCuerpoLlegansAMsAuthSinCambios() {
        String cuerpo = "{\"refreshToken\":\"abc.def.ghi\"}";
        cliente.post().uri("/api/v1/auth/refresh")
                .header("Authorization", "Bearer token-de-prueba")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(cuerpo)
                .exchange().expectStatus().isOk();
        assertEquals(cuerpo, auth.ultima().cuerpoTexto());
        assertEquals("Bearer token-de-prueba", auth.ultima().cabeceras().get("Authorization"));
    }

    @Test
    void loQueRespondeMsAuthSeDevuelveTalCualConSuCodigo() {
        for (int codigo : new int[]{201, 400, 401, 404, 409}) {
            cliente.get().uri("/api/v1/auth/estado/" + codigo).exchange()
                    .expectStatus().isEqualTo(codigo)
                    .expectBody().jsonPath("$.status").isEqualTo(codigo).jsonPath("$.message").isEqualTo("respuesta de ms-tienda");
        }
        assertTrue(servicio.recibidas().isEmpty());
    }

    @Test
    void losDocumentosOpenApiSeLeenDeCadaServicio() {
        cliente.get().uri("/api-docs/ms-auth").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-auth");
        assertEquals("/v3/api-docs", auth.ultima().uri());          // el servicio sigue publicando en su ruta de siempre

        cliente.get().uri("/api-docs/ms-tienda").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        assertEquals("/v3/api-docs", servicio.ultima().uri());
        assertEquals(1, auth.recibidas().size());
        assertEquals(1, servicio.recibidas().size());
    }

    @Test
    void lasRutasDeDocumentosNoAdmitenSubrutas() {
        cliente.get().uri("/api-docs/ms-auth/otra").exchange().expectStatus().isNotFound();
        cliente.get().uri("/api-docs/ms-inexistente").exchange().expectStatus().isNotFound();
        assertTrue(auth.recibidas().isEmpty());
        assertTrue(servicio.recibidas().isEmpty());
    }
}
