package cl.ferrefix.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cada servicio tiene su propio esquema. Los usuarios son de ms-auth, así que las migraciones de este servicio no
 * pueden crear ni referenciar la tabla USUARIOS: USUARIO_ID es una columna simple y los datos del cliente que hacen
 * falta (RUT, nombre, email) se guardan como copia, tomados del token.
 */
class EsquemaSinUsuariosTest {

    static final String[] MIGRACIONES = {
            "V2__productos.sql", "V3__productos_iniciales.sql", "V4__maquinas_y_arriendos.sql",
            "V5__maquinas_iniciales.sql", "V6__ordenes_y_cotizaciones.sql"};

    static final Map<String, String> sql = new LinkedHashMap<>();

    @BeforeAll
    static void cargar() throws IOException {
        for (String archivo : MIGRACIONES) {
            try (InputStream in = EsquemaSinUsuariosTest.class.getResourceAsStream("/db/migration/" + archivo)) {
                assertNotNull(in, "No existe " + archivo);
                sql.put(archivo, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    /** Cuerpo del CREATE TABLE de una tabla (lo que va entre paréntesis hasta el ';'). */
    private static String tabla(String archivo, String nombre) {
        Matcher m = Pattern.compile("CREATE TABLE " + nombre + " \\((.*?)\\);", Pattern.DOTALL).matcher(sql.get(archivo));
        assertTrue(m.find(), "No se encontró la tabla " + nombre + " en " + archivo);
        return m.group(1);
    }

    @Test
    void laMigracionDeUsuariosNoViveEnEsteServicio() {
        assertNull(EsquemaSinUsuariosTest.class.getResource("/db/migration/V1__usuarios_y_roles.sql"),
                "V1 (usuarios y roles) pertenece a ms-auth");
    }

    @Test
    void ningunaMigracionCreaNiReferenciaLaTablaDeUsuarios() {
        sql.forEach((archivo, contenido) -> {
            String sinComentarios = contenido.replaceAll("(?m)--.*$", "");
            assertFalse(Pattern.compile("REFERENCES\\s+USUARIOS\\b").matcher(sinComentarios).find(),
                    archivo + " no puede tener una clave foránea hacia USUARIOS (está en otro esquema)");
            assertFalse(Pattern.compile("CREATE\\s+TABLE\\s+USUARIO").matcher(sinComentarios).find(),
                    archivo + " no puede crear tablas de usuarios");
            assertFalse(Pattern.compile("\\b(FROM|JOIN|INTO|UPDATE)\\s+USUARIO").matcher(sinComentarios).find(),
                    archivo + " no puede leer ni escribir tablas de usuarios");
        });
    }

    @Test
    void ordenesYArriendosConservanElIdDelUsuarioComoColumnaObligatoriaEIndexada() {
        assertTrue(Pattern.compile("USUARIO_ID\\s+NUMBER\\(19,0\\)\\s+NOT NULL").matcher(tabla("V6__ordenes_y_cotizaciones.sql", "ORDENES")).find());
        assertTrue(Pattern.compile("USUARIO_ID\\s+NUMBER\\(19,0\\)\\s+NOT NULL").matcher(tabla("V4__maquinas_y_arriendos.sql", "ARRIENDOS")).find());
        // "Mis órdenes" y "mis arriendos" filtran por esta columna: debe seguir indexada aunque ya no sea FK.
        assertTrue(sql.get("V6__ordenes_y_cotizaciones.sql").contains("CREATE INDEX IX_ORDENES_USUARIO ON ORDENES (USUARIO_ID, CREATED_AT)"));
        assertTrue(sql.get("V4__maquinas_y_arriendos.sql").contains("CREATE INDEX IX_ARRIENDOS_USUARIO ON ARRIENDOS (USUARIO_ID)"));
        assertTrue(sql.get("V6__ordenes_y_cotizaciones.sql").contains("CREATE INDEX IX_COTIZACIONES_USUARIO ON COTIZACIONES (USUARIO_ID)"));
    }

    @Test
    void arriendosGuardaLaCopiaDelClienteConLosMismosLargosQueOrdenes() {
        String arriendos = tabla("V4__maquinas_y_arriendos.sql", "ARRIENDOS");
        String ordenes = tabla("V6__ordenes_y_cotizaciones.sql", "ORDENES");
        for (String columna : new String[]{
                "CLIENTE_RUT\\s+VARCHAR2\\(12 CHAR\\)\\s+NOT NULL",
                "CLIENTE_NOMBRE\\s+VARCHAR2\\(120 CHAR\\)\\s+NOT NULL",
                "CLIENTE_EMAIL\\s+VARCHAR2\\(120 CHAR\\)\\s+NOT NULL"}) {
            assertTrue(Pattern.compile(columna).matcher(arriendos).find(), "Falta en ARRIENDOS: " + columna);
            assertTrue(Pattern.compile(columna).matcher(ordenes).find(), "Falta en ORDENES: " + columna);
        }
    }

    @Test
    void lasClavesForaneasQueQuedanSonTodasInternasDelServicio() {
        // Dentro del mismo esquema las FK se mantienen: unidad -> máquina, arriendo -> unidad, ítem -> orden y producto.
        String v4 = sql.get("V4__maquinas_y_arriendos.sql");
        String v6 = sql.get("V6__ordenes_y_cotizaciones.sql");
        assertTrue(v4.contains("CONSTRAINT FK_ARRIENDOS_UNIDAD FOREIGN KEY (UNIDAD_ID) REFERENCES MAQUINA_UNIDADES (ID)"));
        assertTrue(v6.contains("CONSTRAINT FK_ORDEN_ITEMS_ORDEN FOREIGN KEY (ORDEN_ID) REFERENCES ORDENES (ID) ON DELETE CASCADE"));
        assertTrue(v6.contains("CONSTRAINT FK_ORDEN_ITEMS_PRODUCTO FOREIGN KEY (PRODUCTO_ID) REFERENCES PRODUCTOS (ID) ON DELETE SET NULL"));
    }
}
