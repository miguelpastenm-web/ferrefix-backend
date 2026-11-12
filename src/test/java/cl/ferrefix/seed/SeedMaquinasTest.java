package cl.ferrefix.seed;

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

/** Valida el V5 generado (máquinas, especificaciones, unidades e imágenes) sin necesitar Oracle. */
class SeedMaquinasTest {

    /** Tarifas por día tal como están en ferrefix-ui/src/data/maquinarias.js. */
    static final Map<String, Integer> TARIFAS_DEL_FRONT = Map.of(
            "MAQ-BOSCH-01", 28990,
            "MAQ-BET-02", 18990,
            "MAQ-GEN-03", 24990,
            "MAQ-PLAC-04", 22990);

    static String sql;
    static List<SeedMaquinasSql.Modelo> modelos;
    static List<SeedMaquinasSql.Spec> specs;
    static List<SeedMaquinasSql.Unidad> unidades;

    @BeforeAll
    static void cargar() throws IOException {
        sql = SeedMaquinasSql.leer();
        modelos = SeedMaquinasSql.modelos(sql);
        specs = SeedMaquinasSql.especificaciones(sql);
        unidades = SeedMaquinasSql.unidades(sql);
    }

    // ---------------------------------------------------------------- cantidades y datos del front
    @Test
    void siembraCuatroModelosConDieciochoEspecificacionesYOchoUnidades() {
        assertEquals(4, modelos.size());
        assertEquals(18, specs.size());
        assertEquals(8, unidades.size());
    }

    @Test
    void tarifasYCodigosSonLosDelFront() {
        Map<String, Integer> sembradas = modelos.stream()
                .collect(Collectors.toMap(SeedMaquinasSql.Modelo::codigo, SeedMaquinasSql.Modelo::tarifaDia));
        assertEquals(TARIFAS_DEL_FRONT, sembradas);
    }

    @Test
    void datosDeUnModeloCoincidenConElFront() {
        SeedMaquinasSql.Modelo bet = modelos.stream().filter(m -> m.codigo().equals("MAQ-BET-02")).findFirst().orElseThrow();
        assertEquals("Betonera Trompo Eléctrica 150 Litros 0.75HP", bet.nombre());
        assertEquals("Ingco Heavy", bet.marca());
        assertEquals("Hormigón y Mezcla", bet.categoria());
        assertEquals("Cable con enchufe industrial Legrand y manubrio de volteo reforzado", bet.incluye());
        assertEquals(List.of("Capacidad de Tambor", "Motor", "Ruedas", "Espesor Tambor"),
                specs.stream().filter(s -> s.codigo().equals("MAQ-BET-02")).map(SeedMaquinasSql.Spec::clave).toList());
        assertEquals("Goma maciza 10\" para fácil traslado en obra",
                specs.stream().filter(s -> s.codigo().equals("MAQ-BET-02") && s.clave().equals("Ruedas")).findFirst().orElseThrow().valor());
    }

    @Test
    void cadaModeloCumpleLasReglasDelBackend() {
        Set<String> codigos = new HashSet<>();
        for (SeedMaquinasSql.Modelo m : modelos) {
            assertTrue(codigos.add(m.codigo()), "Código repetido " + m.codigo());
            assertTrue(m.codigo().matches("^[A-Za-z0-9._-]{1,30}$"), m.codigo());
            assertTrue(m.tarifaDia() > 0, m.codigo());
            assertEquals("DISPONIBLE", m.estado(), m.codigo());
            assertTrue(m.nombre().length() <= 150 && m.marca().length() <= 60 && m.categoria().length() <= 60, m.codigo());
            assertTrue(m.incluye() == null || m.incluye().length() <= 500, m.codigo());
        }
    }

    // ---------------------------------------------------------------- especificaciones
    @Test
    void especificacionesConOrdenConsecutivoDesdeCero() {
        Map<String, List<SeedMaquinasSql.Spec>> porModelo = specs.stream()
                .collect(Collectors.groupingBy(SeedMaquinasSql.Spec::codigo, LinkedHashMap::new, Collectors.toList()));
        assertEquals(TARIFAS_DEL_FRONT.keySet(), porModelo.keySet(), "Cada modelo debe tener especificaciones");
        assertEquals(6, porModelo.get("MAQ-BOSCH-01").size());
        assertEquals(4, porModelo.get("MAQ-BET-02").size());
        porModelo.forEach((codigo, lista) -> {
            for (int i = 0; i < lista.size(); i++) {
                assertEquals(i, lista.get(i).orden(), "ORDEN no consecutivo en " + codigo);
            }
            assertEquals(lista.size(), lista.stream().map(SeedMaquinasSql.Spec::clave).distinct().count(), "Clave repetida en " + codigo);
            lista.forEach(s -> assertTrue(!s.clave().isBlank() && !s.valor().isBlank()
                    && s.clave().length() <= 80 && s.valor().length() <= 300, codigo));
        });
    }

    // ---------------------------------------------------------------- unidades
    @Test
    void dosUnidadesDisponiblesPorModeloConCodigoDelModelo() {
        Map<String, List<SeedMaquinasSql.Unidad>> porModelo = unidades.stream()
                .collect(Collectors.groupingBy(SeedMaquinasSql.Unidad::modeloCodigo));
        assertEquals(TARIFAS_DEL_FRONT.keySet(), porModelo.keySet());
        porModelo.forEach((codigo, lista) -> {
            assertEquals(List.of(codigo + "-U01", codigo + "-U02"),
                    lista.stream().map(SeedMaquinasSql.Unidad::codigo).sorted().toList());
            assertTrue(lista.stream().allMatch(u -> u.estado().equals("DISPONIBLE")), codigo);
            assertTrue(lista.stream().allMatch(u -> u.codigo().length() <= 40), codigo);
        });
        assertEquals(8, unidades.stream().map(SeedMaquinasSql.Unidad::codigo).distinct().count(), "Códigos de unidad repetidos");
    }

    @Test
    void cadaUnidadSoloSeCreaSiNoExisteYSiElModeloEstaDisponible() {
        for (SeedMaquinasSql.Unidad u : unidades) {
            assertTrue(u.tieneNotExists(), "Falta NOT EXISTS en " + u.codigo());
            assertTrue(u.exigeModeloDisponible(), "Falta exigir modelo DISPONIBLE en " + u.codigo());
        }
    }

    // ---------------------------------------------------------------- imágenes
    private static String recurso(String url) {
        assertTrue(url.matches("^/media/maquinas/maq-[a-z0-9-]+\\.webp$"), "URL con formato inesperado: " + url);
        return "seed-media/" + url.substring("/media/".length());
    }

    @Test
    void todasLasImagenesReferenciadasExistenYSonWebp() throws IOException {
        for (SeedMaquinasSql.Modelo m : modelos) {
            assertEquals("/media/maquinas/" + m.codigo().toLowerCase() + ".webp", m.imagen());
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(recurso(m.imagen()))) {
                assertNotNull(in, "Falta el archivo de " + m.imagen());
                byte[] cab = in.readNBytes(12);
                assertEquals("RIFF", new String(cab, 0, 4, StandardCharsets.ISO_8859_1), m.imagen());
                assertEquals("WEBP", new String(cab, 8, 4, StandardCharsets.ISO_8859_1), m.imagen());
            }
        }
    }

    @Test
    void noHayImagenesHuerfanasNiRepetidas() throws IOException {
        List<String> urls = modelos.stream().map(SeedMaquinasSql.Modelo::imagen).toList();
        assertEquals(4, new HashSet<>(urls).size(), "Dos modelos comparten imagen");

        Set<String> enDisco = new TreeSet<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:seed-media/maquinas/*.webp")) {
            String ruta = r.getURL().toString();
            enDisco.add("/media/" + ruta.substring(ruta.indexOf("seed-media/") + "seed-media/".length()));
        }
        assertEquals(new TreeSet<>(urls), enDisco, "seed-media/maquinas y V5 no coinciden");
    }

    // ---------------------------------------------------------------- idempotencia y alcance
    @Test
    void laMigracionSoloTieneMergeEInsertIdempotentesSinArriendos() {
        for (String s : SeedSql.sentencias(sql)) {
            if (s.startsWith("MERGE INTO MAQUINAS")) {
                assertTrue(s.contains("WHEN NOT MATCHED THEN INSERT"), s);
                assertFalse(s.contains("WHEN MATCHED"), "MERGE no debe actualizar modelos existentes");
            } else {
                assertTrue(s.startsWith("INSERT INTO MAQUINA_ESPECIFICACIONES") || s.startsWith("INSERT INTO MAQUINA_UNIDADES"),
                        "Sentencia inesperada: " + s.substring(0, Math.min(50, s.length())));
                assertTrue(s.contains("NOT EXISTS"), "Falta NOT EXISTS: " + s.substring(0, Math.min(60, s.length())));
            }
        }
        assertEquals(4 + 4 + 8, SeedSql.sentencias(sql).size());
        assertFalse(sql.toUpperCase().contains("ARRIENDOS"), "No se siembran arriendos");
        assertFalse(sql.contains("${"), "Flyway interpretaría ${...} como placeholder");
    }
}
