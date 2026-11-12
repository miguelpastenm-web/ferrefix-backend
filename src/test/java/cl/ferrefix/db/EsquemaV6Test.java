package cl.ferrefix.db;

import cl.ferrefix.model.EstadoOrden;
import cl.ferrefix.model.MetodoPago;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Mantiene alineados el esquema de V6 y el código: si alguien agrega un valor a un enum y olvida el CHECK, falla aquí. */
class EsquemaV6Test {

    static String sql;

    @BeforeAll
    static void cargar() throws IOException {
        try (InputStream in = EsquemaV6Test.class.getResourceAsStream("/db/migration/V6__ordenes_y_cotizaciones.sql")) {
            assertNotNull(in, "No existe V6__ordenes_y_cotizaciones.sql");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Valores entre comillas de un CHECK "CONSTRAINT <nombre> CHECK (<col> IN (...))". */
    private static Set<String> valoresDelCheck(String constraint) {
        Matcher m = Pattern.compile("CONSTRAINT " + constraint + " CHECK \\(\\s*\\w+ IN \\(([^)]*)\\)").matcher(sql);
        assertTrue(m.find(), "No se encontró el CHECK " + constraint);
        Set<String> valores = new TreeSet<>();
        Matcher v = Pattern.compile("'([^']+)'").matcher(m.group(1));
        while (v.find()) {
            valores.add(v.group(1));
        }
        return valores;
    }

    @Test
    void elCheckDeEstadoDeOrdenCoincideConElEnum() {
        assertEquals(Arrays.stream(EstadoOrden.values()).map(Enum::name).collect(Collectors.toCollection(TreeSet::new)),
                valoresDelCheck("CK_ORDENES_ESTADO"));
    }

    @Test
    void elCheckDeMetodoDePagoCoincideConElEnum() {
        assertEquals(Arrays.stream(MetodoPago.values()).map(Enum::name).collect(Collectors.toCollection(TreeSet::new)),
                valoresDelCheck("CK_ORDENES_METODO_PAGO"));
    }

    @Test
    void elTotalSiempreEsSubtotalMasEnvioYCadaItemPrecioPorCantidad() {
        assertTrue(sql.contains("CHECK (TOTAL = SUBTOTAL + COSTO_ENVIO)"));
        assertTrue(sql.contains("CHECK (SUBTOTAL = PRECIO_UNITARIO * CANTIDAD)"));
        assertTrue(sql.contains("CHECK (CANTIDAD BETWEEN 1 AND 1000)"));
    }

    @Test
    void elProductoDeUnItemQuedaEnNullSiSeEliminaElProducto() {
        Matcher m = Pattern.compile("FK_ORDEN_ITEMS_PRODUCTO FOREIGN KEY \\(PRODUCTO_ID\\) REFERENCES PRODUCTOS \\(ID\\) ON DELETE SET NULL").matcher(sql);
        assertTrue(m.find(), "ORDEN_ITEMS.PRODUCTO_ID debe ser FK con ON DELETE SET NULL");
        assertTrue(Pattern.compile("\\bPRODUCTO_ID\\s+NUMBER\\(19,0\\),").matcher(sql).find(), "PRODUCTO_ID debe ser nullable");
        assertTrue(sql.contains("FK_ORDEN_ITEMS_ORDEN FOREIGN KEY (ORDEN_ID) REFERENCES ORDENES (ID) ON DELETE CASCADE"));
        assertTrue(sql.contains("UNIQUE (ORDEN_ID, SKU)"));
    }

    @Test
    void laTablaDeCotizacionesTieneLosEstadosYLosSeisTiposDelFormulario() {
        assertEquals(Set.of("PENDIENTE", "EN_REVISION", "RESPONDIDA", "CERRADA"), valoresDelCheck("CK_COTIZACIONES_ESTADO"));
        assertEquals(Set.of("Herramientas de Alto Rendimiento", "Materiales de Obra Gruesa", "Fijaciones y Tornillería por Mayor",
                        "Arriendo de Maquinaria y Demoledores", "Equipamiento de Seguridad y Faena", "Otro Requerimiento Especial"),
                valoresDelCheck("CK_COTIZACIONES_TIPO"));
        assertTrue(sql.contains("FK_COTIZACIONES_USUARIO FOREIGN KEY (USUARIO_ID) REFERENCES USUARIOS (ID) ON DELETE SET NULL"));
    }

    @Test
    void laMigracionNoSiembraDatos() {
        assertFalse(Pattern.compile("(?im)^\\s*(INSERT|MERGE|UPDATE|DELETE)\\b").matcher(sql).find(), "V6 es solo esquema");
    }
}
