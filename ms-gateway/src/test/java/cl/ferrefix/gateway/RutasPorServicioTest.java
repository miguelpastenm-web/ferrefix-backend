package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reparto de rutas entre servicios: /api/v1/auth/** y /api/v1/users/** son de ms-auth; /api/v1/maquinas/**, /arriendos/** y
 * /cotizaciones/** son de ms-arriendos; el resto de la API y /media/**, de ms-tienda.
 */
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
        assertTrue(arriendos.recibidas().isEmpty(), "Ninguna de estas rutas es de ms-arriendos");
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/maquinas",
            "GET,/api/v1/maquinas/5",
            "POST,/api/v1/maquinas",
            "PUT,/api/v1/maquinas/5",
            "DELETE,/api/v1/maquinas/5",
            "GET,/api/v1/maquinas/5/unidades",
            "POST,/api/v1/maquinas/5/unidades",
            "DELETE,/api/v1/maquinas/5/unidades/11",
            "POST,/api/v1/arriendos",
            "GET,/api/v1/arriendos",
            "GET,/api/v1/arriendos/mis-arriendos",
            "GET,/api/v1/arriendos/77",
            "PUT,/api/v1/arriendos/77",
            "PATCH,/api/v1/arriendos/77/devolver",
            "DELETE,/api/v1/arriendos/77",
            "POST,/api/v1/cotizaciones",
            "GET,/api/v1/cotizaciones",
            "GET,/api/v1/cotizaciones/88",
            "PATCH,/api/v1/cotizaciones/88",
            "DELETE,/api/v1/cotizaciones/88"})
    void maquinasArriendosYCotizacionesVanAMsArriendos(String metodo, String ruta) {
        cliente.method(HttpMethod.valueOf(metodo)).uri(ruta)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-arriendos").jsonPath("$.ruta").isEqualTo(ruta);
        assertEquals(metodo, arriendos.ultima().metodo());
        assertEquals(ruta, arriendos.ultima().uri());
        assertTrue(servicio.recibidas().isEmpty(), "Ninguna de estas rutas es de ms-tienda");
        assertTrue(auth.recibidas().isEmpty(), "Ninguna de estas rutas es de ms-auth");
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/v1/productos",
            "GET,/api/v1/ordenes/mis-ordenes",
            "POST,/api/v1/ordenes",
            "POST,/api/v1/imagenes",
            "GET,/media/productos/taladro-percutor.webp",
            "GET,/media/maquinas/maq-bosch-01.webp"})
    void elRestoDeLaApiVaAMsTienda(String metodo, String ruta) {
        cliente.method(HttpMethod.valueOf(metodo)).uri(ruta)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        assertEquals(ruta, servicio.ultima().uri());
        assertTrue(auth.recibidas().isEmpty(), "Ninguna de estas rutas es de ms-auth");
        assertTrue(arriendos.recibidas().isEmpty(), "Ninguna de estas rutas es de ms-arriendos");
    }

    @Test
    void lasImagenesDeLasMaquinasSeSubenYSeSirvenDesdeMsTienda() {
        // Decisión de diseño: las imágenes siguen en ms-tienda aunque la máquina sea de ms-arriendos. La subida usa un
        // único endpoint (el destino viaja en el formulario) y /media/** no distingue servicios.
        cliente.post().uri("/api/v1/imagenes").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        cliente.get().uri("/media/maquinas/maq-gen-03.webp").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        assertTrue(arriendos.recibidas().isEmpty());
    }

    @Test
    void unPrefijoParecidoPeroDistintoNoVaAMsAuth() {
        // "/api/v1/authors" no es "/api/v1/auth/**": lo atiende la ruta general (ms-tienda).
        cliente.get().uri("/api/v1/authors").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        assertTrue(auth.recibidas().isEmpty());
    }

    @Test
    void unPrefijoParecidoPeroDistintoNoVaAMsArriendos() {
        // "/api/v1/maquinaria", "/arriendo" y "/cotizacion" no son rutas de ms-arriendos: las atiende la ruta general.
        for (String ruta : new String[]{"/api/v1/maquinaria", "/api/v1/arriendo", "/api/v1/cotizacion"}) {
            cliente.get().uri(ruta).exchange().expectStatus().isOk()
                    .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        }
        assertTrue(arriendos.recibidas().isEmpty());
    }

    @Test
    void elTokenYElCuerpoLlegansAMsArriendosSinCambios() {
        String cuerpo = "{\"maquinaId\":5,\"diasArriendo\":3}";
        cliente.post().uri("/api/v1/arriendos")
                .header("Authorization", "Bearer token-de-prueba")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(cuerpo)
                .exchange().expectStatus().isOk();
        assertEquals(cuerpo, arriendos.ultima().cuerpoTexto());
        assertEquals("Bearer token-de-prueba", arriendos.ultima().cabeceras().get("Authorization"));
    }

    @Test
    void loQueRespondeMsArriendosSeDevuelveTalCualConSuCodigo() {
        for (int codigo : new int[]{201, 400, 403, 404, 409}) {
            cliente.get().uri("/api/v1/arriendos/estado/" + codigo).exchange()
                    .expectStatus().isEqualTo(codigo)
                    .expectBody().jsonPath("$.status").isEqualTo(codigo);
        }
        assertTrue(servicio.recibidas().isEmpty());
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
    void elDocumentoDeMsArriendosSeLeeDeEseServicio() {
        cliente.get().uri("/api-docs/ms-arriendos").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-arriendos");
        assertEquals("/v3/api-docs", arriendos.ultima().uri());
        assertTrue(auth.recibidas().isEmpty());
        assertTrue(servicio.recibidas().isEmpty());
    }

    @Test
    void lasRutasDeDocumentosNoAdmitenSubrutas() {
        cliente.get().uri("/api-docs/ms-auth/otra").exchange().expectStatus().isNotFound();
        cliente.get().uri("/api-docs/ms-arriendos/otra").exchange().expectStatus().isNotFound();
        cliente.get().uri("/api-docs/ms-inexistente").exchange().expectStatus().isNotFound();
        assertTrue(auth.recibidas().isEmpty());
        assertTrue(arriendos.recibidas().isEmpty());
        assertTrue(servicio.recibidas().isEmpty());
    }
}
