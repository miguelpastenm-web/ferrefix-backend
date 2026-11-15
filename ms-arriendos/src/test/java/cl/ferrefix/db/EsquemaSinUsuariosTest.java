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

    static final String[] MIGRACIONES = {"V1__maquinas_y_arriendos.sql", "V2__maquinas_iniciales.sql", "V3__cotizaciones.sql"};

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
    void losArriendosConservanElIdDelUsuarioComoColumnaObligatoriaEIndexada() {
        assertTrue(Pattern.compile("USUARIO_ID\\s+NUMBER\\(19,0\\)\\s+NOT NULL").matcher(tabla("V1__maquinas_y_arriendos.sql", "ARRIENDOS")).find());
        // "Mis arriendos" filtra por esta columna: debe seguir indexada aunque ya no sea FK.
        assertTrue(sql.get("V1__maquinas_y_arriendos.sql").contains("CREATE INDEX IX_ARRIENDOS_USUARIO ON ARRIENDOS (USUARIO_ID)"));
    }

    @Test
    void lasCotizacionesPuedenQuedarSinUsuarioPeroConElIndiceParaBuscarlasPorUsuario() {
        assertTrue(Pattern.compile("USUARIO_ID\\s+NUMBER\\(19,0\\),").matcher(tabla("V3__cotizaciones.sql", "COTIZACIONES")).find(),
                "USUARIO_ID de las cotizaciones debe ser nulable (visitantes sin sesión)");
        assertTrue(sql.get("V3__cotizaciones.sql").contains("CREATE INDEX IX_COTIZACIONES_USUARIO ON COTIZACIONES (USUARIO_ID)"));
    }

    @Test
    void arriendosGuardaLaCopiaDelClienteTomadaDelToken() {
        String arriendos = tabla("V1__maquinas_y_arriendos.sql", "ARRIENDOS");
        for (String columna : new String[]{
                "CLIENTE_RUT\\s+VARCHAR2\\(12 CHAR\\)\\s+NOT NULL",
                "CLIENTE_NOMBRE\\s+VARCHAR2\\(120 CHAR\\)\\s+NOT NULL",
                "CLIENTE_EMAIL\\s+VARCHAR2\\(120 CHAR\\)\\s+NOT NULL"}) {
            assertTrue(Pattern.compile(columna).matcher(arriendos).find(), "Falta en ARRIENDOS: " + columna);
        }
    }

    @Test
    void elEsquemaDeArriendosNoTieneLasTablasDeLaTienda() {
        // Productos y órdenes son de ms-tienda (otro esquema).
        sql.forEach((archivo, contenido) -> assertFalse(
                Pattern.compile("CREATE\\s+TABLE\\s+(PRODUCTO|ORDEN)").matcher(contenido).find(),
                archivo + " no puede crear tablas de ms-tienda"));
    }

    @Test
    void lasClavesForaneasQueQuedanSonTodasInternasDelServicio() {
        // Dentro del mismo esquema las FK se mantienen: unidad -> máquina y arriendo -> unidad.
        String v1 = sql.get("V1__maquinas_y_arriendos.sql");
        assertTrue(v1.contains("CONSTRAINT FK_UNIDADES_MAQUINA FOREIGN KEY (MAQUINA_ID) REFERENCES MAQUINAS (ID) ON DELETE CASCADE"));
        assertTrue(v1.contains("CONSTRAINT FK_ARRIENDOS_UNIDAD FOREIGN KEY (UNIDAD_ID) REFERENCES MAQUINA_UNIDADES (ID)"));
    }
}
