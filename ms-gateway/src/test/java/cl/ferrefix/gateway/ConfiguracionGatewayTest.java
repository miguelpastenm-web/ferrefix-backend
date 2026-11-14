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
    void hayUnaSolaRutaHaciaMsTienda() {
        List<Route> definidas = rutas.getRoutes().collectList().block();
        assertNotNull(definidas);
        assertEquals(1, definidas.size());
        assertEquals("ms-tienda", definidas.get(0).getId());
        assertEquals("http://localhost:8082", definidas.get(0).getUri().toString());
    }

    @Test
    void elGatewayNoConoceSecretos() {
        // No valida JWT ni se conecta a la base: no debe haber secreto alguno configurado, ni siquiera por defecto.
        assertNull(entorno.getProperty("ferrefix.jwt.secret"));
        assertNull(entorno.getProperty("spring.datasource.password"));
    }
}
