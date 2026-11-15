package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CORS del frontend (Vite en :5173). Antes vivía en el monolito; ahora es responsabilidad exclusiva del gateway.
 * El navegador hace un preflight OPTIONS antes de cada PATCH/PUT/DELETE y rechaza la respuesta si
 * Access-Control-Allow-Origin no viene exactamente UNA vez.
 */
class CorsTest extends GatewayTestBase {

    private static final String ACAO = "Access-Control-Allow-Origin";

    @Test
    void elPreflightDePatchSePermiteSinToken() {
        cliente.options().uri("/api/v1/arriendos/77/devolver")
                .header("Origin", ORIGEN)
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(ACAO, ORIGEN)
                .expectHeader().value("Access-Control-Allow-Methods", metodos -> assertTrue(metodos.contains("PATCH")))
                .expectHeader().value("Access-Control-Allow-Headers", cabeceras -> assertTrue(cabeceras.contains("Authorization")))
                .expectHeader().valueEquals("Access-Control-Max-Age", "3600");
    }

    @Test
    void elPreflightPermiteTodosLosMetodosDelCrud() {
        for (String metodo : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"}) {
            cliente.options().uri("/api/v1/productos")
                    .header("Origin", ORIGEN)
                    .header("Access-Control-Request-Method", metodo)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(ACAO, ORIGEN);
        }
    }

    @Test
    void elPreflightLoRespondeElGatewayYNoLlegaAlServicio() {
        cliente.options().uri("/api/v1/ordenes/5/estado")
                .header("Origin", ORIGEN)
                .header("Access-Control-Request-Method", "PATCH")
                .exchange()
                .expectStatus().isOk();
        assertTrue(servicio.recibidas().isEmpty(), "El preflight no debe reenviarse a ms-tienda");
    }

    @Test
    void unOrigenNoAutorizadoSeRechaza() {
        cliente.options().uri("/api/v1/arriendos/77/devolver")
                .header("Origin", "http://sitio-malicioso.example")
                .header("Access-Control-Request-Method", "PATCH")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(ACAO);
        assertTrue(servicio.recibidas().isEmpty());
        assertTrue(arriendos.recibidas().isEmpty());
    }

    @Test
    void unaPeticionRealDeUnOrigenNoAutorizadoTampocoLlegaAlServicio() {
        cliente.get().uri("/api/v1/productos")
                .header("Origin", "http://sitio-malicioso.example")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(ACAO);
        assertTrue(servicio.recibidas().isEmpty());
    }

    @Test
    void unMetodoNoDeclaradoNoSePermiteEnElPreflight() {
        cliente.options().uri("/api/v1/productos")
                .header("Origin", ORIGEN)
                .header("Access-Control-Request-Method", "TRACE")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void laRespuestaRealDelPatchTraeAccessControlAllowOriginUnaSolaVez() {
        EntityExchangeResult<byte[]> respuesta = cliente.patch().uri("/api/v1/arriendos/77/devolver")
                .header("Origin", ORIGEN)
                .header("Authorization", "Bearer token-de-prueba")
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();
        assertEquals(List.of(ORIGEN), respuesta.getResponseHeaders().get(ACAO));
        assertEquals(1, arriendos.recibidas().size(), "La petición real sí se reenvía (a ms-arriendos)");
        assertTrue(servicio.recibidas().isEmpty());
    }

    @Test
    void laRespuestaDeCadaUnoDeLosTresServiciosTraeLaCabeceraCorsUnaSolaVez() {
        // CORS se resuelve solo en el gateway, vaya la petición a ms-tienda, ms-auth o ms-arriendos.
        for (String ruta : new String[]{"/api/v1/productos", "/api/v1/auth/me", "/api/v1/maquinas"}) {
            EntityExchangeResult<byte[]> respuesta = cliente.get().uri(ruta)
                    .header("Origin", ORIGEN)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().returnResult();
            assertEquals(List.of(ORIGEN), respuesta.getResponseHeaders().get(ACAO), ruta);
        }
        assertEquals(1, servicio.recibidas().size());
        assertEquals(1, auth.recibidas().size());
        assertEquals(1, arriendos.recibidas().size());
    }

    @Test
    void elSegundoOrigenConfiguradoTambienSePermite() {
        cliente.get().uri("/api/v1/productos")
                .header("Origin", "http://front.ferrefix.example")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(ACAO, "http://front.ferrefix.example");
    }

    @Test
    void siElServicioEnviaSuPropiaCabeceraCorsNoSeDuplica() {
        // Defensa ante un servicio mal configurado: se conserva solo la cabecera del gateway.
        EntityExchangeResult<byte[]> respuesta = cliente.get().uri("/api/v1/con-cors")
                .header("Origin", ORIGEN)
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();
        assertEquals(List.of(ORIGEN), respuesta.getResponseHeaders().get(ACAO));
    }

    @Test
    void losErroresDelGatewayTambienLlevanLaCabeceraCors() {
        // Sin ella el navegador oculta el cuerpo del error y el front solo vería un "error de red".
        EntityExchangeResult<byte[]> respuesta = cliente.get().uri("/ruta-que-no-existe")
                .header("Origin", ORIGEN)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().returnResult();
        assertEquals(List.of(ORIGEN), respuesta.getResponseHeaders().get(ACAO));
    }

    @Test
    void unaPeticionSinOrigenNoRecibeCabecerasCors() {
        HttpHeaders cabeceras = cliente.get().uri("/api/v1/productos").exchange()
                .expectStatus().isOk()
                .expectBody().returnResult().getResponseHeaders();
        assertNull(cabeceras.get(ACAO));
    }
}
