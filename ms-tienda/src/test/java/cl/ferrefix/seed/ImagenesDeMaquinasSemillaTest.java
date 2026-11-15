package cl.ferrefix.seed;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las imágenes de las máquinas iniciales las sirve ms-tienda (seed-media/maquinas), aunque la migración que las
 * referencia sea de ms-arriendos. Estos tests garantizan que cada URL del contrato tenga su archivo y que no sobren.
 */
class ImagenesDeMaquinasSemillaTest {

    @Test
    void cadaUrlDelContratoTieneSuArchivoWebp() throws IOException {
        for (String url : ContratoImagenesMaquinas.URLS) {
            String recurso = "seed-media/" + url.substring("/media/".length());
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(recurso)) {
                assertNotNull(in, "Falta el archivo de " + url);
                byte[] cabecera = in.readNBytes(12);
                assertEquals("RIFF", new String(cabecera, 0, 4, StandardCharsets.ISO_8859_1), url);
                assertEquals("WEBP", new String(cabecera, 8, 4, StandardCharsets.ISO_8859_1), url);
            }
        }
    }

    @Test
    void noHayImagenesDeMaquinasHuerfanas() throws IOException {
        Set<String> enDisco = new TreeSet<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:seed-media/maquinas/*.webp")) {
            String ruta = r.getURL().toString();
            enDisco.add("/media/" + ruta.substring(ruta.indexOf("seed-media/") + "seed-media/".length()));
        }
        assertEquals(new TreeSet<>(ContratoImagenesMaquinas.URLS), enDisco, "seed-media/maquinas y el contrato no coinciden");
    }
}
