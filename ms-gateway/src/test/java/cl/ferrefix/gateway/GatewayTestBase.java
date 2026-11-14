package cl.ferrefix.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * Base de los tests de integración: levanta el gateway COMPLETO en un puerto libre y lo apunta a un ServicioFalso
 * que hace de ms-tienda. Las peticiones viajan por HTTP real (cliente -> gateway -> servicio), igual que en producción.
 * El response-timeout se baja a 2 s solo para poder probar el 504 sin esperar 60 s (el valor real se comprueba en
 * ConfiguracionGatewayTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class GatewayTestBase {

    static final String ORIGEN = "http://localhost:5173";
    static final ServicioFalso servicio = new ServicioFalso();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("TIENDA_URL", servicio::url);
        registro.add("CORS_ALLOWED_ORIGINS", () -> ORIGEN + ", http://front.ferrefix.example");
        registro.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "2s");
    }

    @Value("${local.server.port}")
    int puerto;

    WebTestClient cliente;

    @BeforeEach
    void prepararCliente() {
        servicio.limpiar();
        cliente = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + puerto)
                .responseTimeout(Duration.ofSeconds(20))
                .build();
    }
}
