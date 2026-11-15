package cl.ferrefix.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aislamiento de fallas: con ms-arriendos caído, solo sus rutas devuelven 502; la tienda y la autenticación siguen
 * funcionando por el mismo gateway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnServicioCaidoTest {

    static final ServicioFalso tienda = new ServicioFalso("ms-tienda");
    static final ServicioFalso auth = new ServicioFalso("ms-auth");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws IOException {
        int puertoArriendos;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoArriendos = socket.getLocalPort();       // se cierra enseguida: nadie escucha ahí
        }
        registro.add("TIENDA_URL", tienda::url);
        registro.add("AUTH_URL", auth::url);
        registro.add("ARRIENDOS_URL", () -> "http://127.0.0.1:" + puertoArriendos);
    }

    @Value("${local.server.port}")
    int puerto;

    WebTestClient cliente;

    @BeforeEach
    void prepararCliente() {
        tienda.limpiar();
        auth.limpiar();
        cliente = WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto)
                .responseTimeout(Duration.ofSeconds(20)).build();
    }

    @Test
    void soloLasRutasDeMsArriendosFallan() {
        cliente.get().uri("/api/v1/maquinas").exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        cliente.get().uri("/api-docs/ms-arriendos").exchange().expectStatus().isEqualTo(502);
        assertTrue(tienda.recibidas().isEmpty());
        assertTrue(auth.recibidas().isEmpty());
    }

    @Test
    void laTiendaYLaAutenticacionSiguenFuncionando() {
        cliente.get().uri("/api/v1/productos").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.servicio").isEqualTo("ms-tienda");
        cliente.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.servicio").isEqualTo("ms-auth");
        cliente.get().uri("/media/maquinas/maq-bosch-01.webp").exchange().expectStatus().isOk();   // las imágenes son de ms-tienda
    }

    @Test
    void elHealthDelGatewaySigueArriba() {
        cliente.get().uri("/actuator/health").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
