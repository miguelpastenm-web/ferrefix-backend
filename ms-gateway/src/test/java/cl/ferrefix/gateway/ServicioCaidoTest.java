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

/** Si ms-tienda está caído (nadie escucha en su puerto), el gateway responde 502 en JSON en vez de un 500 genérico. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServicioCaidoTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws IOException {
        int puertoTienda;
        int puertoAuth;
        int puertoArriendos;
        try (ServerSocket tienda = new ServerSocket(0); ServerSocket autenticacion = new ServerSocket(0);
             ServerSocket arriendos = new ServerSocket(0)) {
            puertoTienda = tienda.getLocalPort();          // se cierran enseguida: quedan puertos donde nadie escucha
            puertoAuth = autenticacion.getLocalPort();
            puertoArriendos = arriendos.getLocalPort();
        }
        registro.add("TIENDA_URL", () -> "http://127.0.0.1:" + puertoTienda);
        registro.add("AUTH_URL", () -> "http://127.0.0.1:" + puertoAuth);
        registro.add("ARRIENDOS_URL", () -> "http://127.0.0.1:" + puertoArriendos);
    }

    @Value("${local.server.port}")
    int puerto;

    WebTestClient cliente;

    @BeforeEach
    void prepararCliente() {
        cliente = WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto)
                .responseTimeout(Duration.ofSeconds(20)).build();
    }

    @Test
    void conElServicioCaidoDevuelve502EnJson() {
        cliente.get().uri("/api/v1/productos").exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.error").isEqualTo("Bad Gateway")
                .jsonPath("$.message").isEqualTo("El servicio no está disponible en este momento. Intente nuevamente en unos segundos")
                .jsonPath("$.path").isEqualTo("/api/v1/productos")
                .jsonPath("$.details").doesNotExist();
    }

    @Test
    void conMsAuthCaidoLaRutaDeAutenticacionDevuelve502EnJson() {
        cliente.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@x.cl\",\"password\":\"clave1234\"}").exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.path").isEqualTo("/api/v1/auth/login");
    }

    @Test
    void conMsArriendosCaidoLasRutasDeMaquinasArriendosYCotizacionesDevuelven502EnJson() {
        for (String ruta : new String[]{"/api/v1/maquinas", "/api/v1/arriendos/mis-arriendos", "/api/v1/cotizaciones"}) {
            cliente.get().uri(ruta).exchange()
                    .expectStatus().isEqualTo(502)
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.status").isEqualTo(502)
                    .jsonPath("$.path").isEqualTo(ruta);
        }
    }

    @Test
    void conElServicioCaidoElHealthDelGatewaySigueArriba() {
        // El health es el del propio gateway: no depende de ms-tienda.
        cliente.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
