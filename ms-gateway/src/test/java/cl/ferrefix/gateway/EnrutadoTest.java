package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** El gateway reenvía a ms-tienda todo el contrato público sin alterarlo: ruta, método, query, cabeceras y cuerpo. */
class EnrutadoTest extends GatewayTestBase {

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/productos", "/api/v1/productos/15", "/api/v1/ordenes/mis-ordenes", "/api/v1/ordenes",
            "/media/productos/taladro-percutor.webp", "/media/maquinas/maq-bosch-01.webp"})
    void lasRutasDeLaTiendaLleganAMsTienda(String ruta) {
        cliente.get().uri(ruta).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda").jsonPath("$.ruta").isEqualTo(ruta);
        assertEquals("GET", servicio.ultima().metodo());
        assertEquals(ruta, servicio.ultima().uri());
        assertTrue(auth.recibidas().isEmpty(), "Estas rutas no son de ms-auth");
        assertTrue(arriendos.recibidas().isEmpty(), "Estas rutas no son de ms-arriendos");
    }

    @Test
    void laQueryViajaIntacta() {
        cliente.get().uri("/api/v1/productos?q=taladro&categoria=Herramientas&sort=precio,desc").exchange()
                .expectStatus().isOk();
        assertEquals("/api/v1/productos?q=taladro&categoria=Herramientas&sort=precio,desc", servicio.ultima().uri());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void losMetodosDeEscrituraLleganConSuCuerpoYElToken(String metodo) {
        String cuerpo = "{\"nombre\":\"Taladro percutor\",\"stock\":5,\"descripción\":\"ñandú áéíóú\"}";
        cliente.method(org.springframework.http.HttpMethod.valueOf(metodo)).uri("/api/v1/productos/15")
                .header("Authorization", "Bearer token-de-prueba")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cuerpo)
                .exchange()
                .expectStatus().isOk();
        ServicioFalso.Peticion recibida = servicio.ultima();
        assertEquals(metodo, recibida.metodo());
        assertEquals(cuerpo, recibida.cuerpoTexto());
        assertEquals("Bearer token-de-prueba", recibida.cabeceras().get("Authorization"));
        assertTrue(recibida.cabeceras().get("Content-Type").startsWith("application/json"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 413, 500})
    void elCodigoYElCuerpoDelServicioSeDevuelvenTalCual(int codigo) {
        // Un error del servicio (incluido su propio 404) NO se reescribe: el front sigue leyendo el mensaje del servicio.
        cliente.get().uri("/api/v1/estado/" + codigo).exchange()
                .expectStatus().isEqualTo(codigo)
                .expectBody()
                .jsonPath("$.status").isEqualTo(codigo)
                .jsonPath("$.message").isEqualTo("respuesta de ms-tienda");
    }

    @Test
    void el201YLasCabecerasDeRespuestaSeConservan() {
        cliente.post().uri("/api/v1/creado").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/v1/creado/15")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    }

    @Test
    void elServicioRecibeElHostPublicoDelGateway() {
        // Swagger arma la URL del servidor con el Host: debe ser el del gateway, no el interno (ms-tienda:8082).
        cliente.get().uri("/api-docs/ms-tienda").exchange().expectStatus().isOk();
        assertEquals("localhost:" + puerto, servicio.ultima().cabeceras().get("Host"));
        cliente.get().uri("/api-docs/ms-auth").exchange().expectStatus().isOk();
        assertEquals("localhost:" + puerto, auth.ultima().cabeceras().get("Host"));
        cliente.get().uri("/api-docs/ms-arriendos").exchange().expectStatus().isOk();
        assertEquals("localhost:" + puerto, arriendos.ultima().cabeceras().get("Host"));
    }

    @Test
    void unaSubidaMultipartDe6MbLlegaCompleta() {
        byte[] imagen = new byte[6 * 1024 * 1024];
        new Random(42).nextBytes(imagen);
        MultipartBodyBuilder partes = new MultipartBodyBuilder();
        partes.part("archivo", new ByteArrayResource(imagen) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        }).contentType(MediaType.IMAGE_PNG);
        partes.part("destino", "productos");

        cliente.post().uri("/api/v1/imagenes")
                .header("Authorization", "Bearer token-de-prueba")
                .body(BodyInserters.fromMultipartData(partes.build()))
                .exchange()
                .expectStatus().isOk();

        ServicioFalso.Peticion recibida = servicio.ultima();
        String tipo = recibida.cabeceras().get("Content-Type");
        assertTrue(tipo.startsWith("multipart/form-data") && tipo.contains("boundary="), tipo);
        assertTrue(recibida.cuerpo().length > imagen.length, "El cuerpo debe traer la imagen completa más las partes");
        String cuerpo = new String(recibida.cuerpo(), StandardCharsets.ISO_8859_1);
        assertTrue(cuerpo.contains(new String(imagen, StandardCharsets.ISO_8859_1)), "Los 6 MB deben llegar byte a byte");
        assertTrue(cuerpo.contains("name=\"destino\""));
        assertTrue(cuerpo.contains("filename=\"foto.png\""));
    }
}
