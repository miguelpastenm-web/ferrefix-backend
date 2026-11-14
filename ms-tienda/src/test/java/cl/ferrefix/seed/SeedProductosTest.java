package cl.ferrefix.seed;

import cl.ferrefix.model.CategoriaProducto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Valida el V3 generado contra las reglas del backend, sin necesitar Oracle. */
class SeedProductosTest {

    static String sql;
    static List<SeedSql.Producto> productos;
    static List<SeedSql.Spec> specs;

    @BeforeAll
    static void cargar() throws IOException {
        sql = SeedSql.leer();
        productos = SeedSql.productos(sql);
        specs = SeedSql.especificaciones(sql);
    }

    @Test
    void siembraLosDieciochoProductosDelFront() {
        assertEquals(18, productos.size());
        assertEquals(119, specs.size());
    }

    @Test
    void cadaProductoCumpleLasReglasDelBackend() {
        Set<String> skus = new HashSet<>();
        for (SeedSql.Producto p : productos) {
            assertTrue(skus.add(p.sku()), "SKU repetido " + p.sku());
            assertTrue(p.sku().matches("^[A-Za-z0-9._-]{1,30}$"), "SKU inválido " + p.sku());
            assertTrue(CategoriaProducto.TODAS.contains(p.categoria()), "Categoría fuera de las 9: " + p.categoria());
            assertTrue(p.precio() > 0, "Precio inválido " + p.sku());
            assertTrue(p.stock() >= 0, "Stock negativo " + p.sku());
            assertEquals(p.stock() == 0 ? "AGOTADO" : "DISPONIBLE", p.estado(), "Estado incoherente con el stock " + p.sku());
            assertFalse(p.nombre().isBlank());
            assertTrue(p.nombre().length() <= 150 && p.marca().length() <= 60 && p.descripcion().length() <= 2000, p.sku());
        }
    }

    // ---------------------------------------------------------------- imágenes
    private static String recurso(String url) {
        assertTrue(url.matches("^/media/(productos|ofertas)/[a-z0-9-]+\\.webp$"), "URL con formato inesperado: " + url);
        return "seed-media/" + url.substring("/media/".length());
    }

    @Test
    void todasLasImagenesReferenciadasExistenYSonWebp() throws IOException {
        for (SeedSql.Producto p : productos) {
            assertTrue(p.imagen().startsWith("/media/productos/"), p.sku());
            if (p.imagenOferta() != null) {
                assertTrue(p.imagenOferta().startsWith("/media/ofertas/"), p.sku());
            }
        }
        for (String url : SeedSql.urlsDeImagen(productos)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(recurso(url))) {
                assertNotNull(in, "Falta el archivo de " + url);
                byte[] cab = in.readNBytes(12);
                assertEquals("RIFF", new String(cab, 0, 4, StandardCharsets.ISO_8859_1), url);
                assertEquals("WEBP", new String(cab, 8, 4, StandardCharsets.ISO_8859_1), url);
            }
        }
    }

    @Test
    void noHayImagenesHuerfanasNiRepetidas() throws IOException {
        List<String> urls = SeedSql.urlsDeImagen(productos);
        assertEquals(urls.size(), new HashSet<>(urls).size(), "Dos productos comparten la misma imagen");
        assertEquals(26, urls.size());

        // Solo las carpetas de productos; las de máquinas (seed-media/maquinas) las cubre SeedMaquinasTest.
        Set<String> enDisco = new TreeSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (String patron : new String[]{"classpath*:seed-media/productos/*.webp", "classpath*:seed-media/ofertas/*.webp"}) {
            for (Resource r : resolver.getResources(patron)) {
                String ruta = r.getURL().toString();
                enDisco.add("/media/" + ruta.substring(ruta.indexOf("seed-media/") + "seed-media/".length()));
            }
        }
        assertEquals(new TreeSet<>(urls), enDisco, "seed-media/productos|ofertas y V3 no coinciden");
    }

    @Test
    void soloOchoProductosTienenTarjetaDeOferta() {
        assertEquals(8, productos.stream().filter(p -> p.imagenOferta() != null).count());
        assertEquals(18, productos.stream().filter(p -> p.imagen() != null).count());
    }

    // ---------------------------------------------------------------- especificaciones
    @Test
    void especificacionesConOrdenConsecutivoDesdeCero() {
        Set<String> skus = productos.stream().map(SeedSql.Producto::sku).collect(Collectors.toSet());
        Map<String, List<SeedSql.Spec>> porSku = specs.stream().collect(Collectors.groupingBy(SeedSql.Spec::sku, LinkedHashMap::new, Collectors.toList()));

        assertEquals(skus, porSku.keySet(), "Cada producto debe tener especificaciones y no sobrar SKUs");
        porSku.forEach((sku, lista) -> {
            assertTrue(lista.size() >= 5, "Pocas especificaciones en " + sku);
            for (int i = 0; i < lista.size(); i++) {
                assertEquals(i, lista.get(i).orden(), "ORDEN no consecutivo en " + sku);
            }
            assertEquals(lista.size(), lista.stream().map(SeedSql.Spec::clave).distinct().count(), "Clave repetida en " + sku);
            lista.forEach(s -> {
                assertFalse(s.clave().isBlank() || s.valor().isBlank(), sku);
                assertTrue(s.clave().length() <= 80 && s.valor().length() <= 300, sku);
            });
        });
    }

    @Test
    void elTaladroConservaElOrdenOriginalDelFront() {
        List<String> claves = specs.stream().filter(s -> s.sku().equals("5422223214")).map(SeedSql.Spec::clave).toList();
        assertEquals(List.of("Potencia / Voltaje", "Tipo de Motor", "Velocidad variable", "Impactos por minuto",
                "Mandril", "Peso", "Garantía", "Incluye"), claves);
    }

    // ---------------------------------------------------------------- idempotencia
    @Test
    void laMigracionEsIdempotenteYNoDestructiva() {
        // Solo se permiten MERGE (WHEN NOT MATCHED) e INSERT con NOT EXISTS: nada de DELETE/UPDATE/DDL.
        for (String s : SeedSql.sentencias(sql)) {
            if (s.startsWith("MERGE INTO PRODUCTOS")) {
                assertTrue(s.contains("WHEN NOT MATCHED THEN INSERT"), s);
                assertFalse(s.contains("WHEN MATCHED"), "MERGE no debe actualizar productos existentes");
            } else {
                assertTrue(s.startsWith("INSERT INTO PRODUCTO_ESPECIFICACIONES"), "Sentencia inesperada: " + s.substring(0, 40));
                assertTrue(s.contains("NOT EXISTS (SELECT 1 FROM PRODUCTO_ESPECIFICACIONES"), "Falta NOT EXISTS");
            }
        }
        assertFalse(sql.contains("${"), "Flyway interpretaría ${...} como placeholder");
    }
}
