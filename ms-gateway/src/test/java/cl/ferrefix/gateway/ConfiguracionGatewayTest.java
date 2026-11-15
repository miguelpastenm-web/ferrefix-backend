package cl.ferrefix.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Valores REALES de application.yml (sin sobrescribir nada): timeouts, ruta por defecto y ausencia de secretos. */
@SpringBootTest
class ConfiguracionGatewayTest {

    @Autowired HttpClientProperties httpClient;
    @Autowired RouteLocator rutas;
    @Autowired Environment entorno;

    @Test
    void elTiempoMaximoDeRespuestaEsDe60Segundos() {
        // Margen para las subidas de imágenes (multipart de hasta 6 MB) por conexiones lentas.
        assertEquals(Duration.ofSeconds(60), httpClient.getResponseTimeout());
        assertEquals(5000, httpClient.getConnectTimeout());
    }

    @Test
    void hayRutasParaAuthTiendaYLosDocumentosDeAmbos() {
        List<Route> definidas = rutas.getRoutes().collectList().block();
        assertNotNull(definidas);
        // Orden de evaluación: la ruta de ms-auth va antes que la general de /api/v1/**.
        assertEquals(List.of("ms-auth", "ms-tienda", "docs-ms-auth", "docs-ms-tienda"),
                definidas.stream().map(Route::getId).toList());
        assertEquals("http://localhost:8081", definidas.get(0).getUri().toString());   // AUTH_URL por defecto
        assertEquals("http://localhost:8082", definidas.get(1).getUri().toString());   // TIENDA_URL por defecto
        assertEquals("http://localhost:8081", definidas.get(2).getUri().toString());
        assertEquals("http://localhost:8082", definidas.get(3).getUri().toString());
    }

    @Test
    void elGatewayNoConoceSecretos() {
        // No valida JWT ni se conecta a la base: no debe haber secreto alguno configurado, ni siquiera por defecto.
        assertNull(entorno.getProperty("ferrefix.jwt.secret"));
        assertNull(entorno.getProperty("spring.datasource.password"));
    }
}
