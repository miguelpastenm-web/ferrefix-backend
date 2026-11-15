package cl.ferrefix.seed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee V2__maquinas_iniciales.sql (el generado) y lo convierte en objetos, para validarlo sin Oracle. */
final class SeedMaquinasSql {

    static final String RECURSO = "/db/migration/V2__maquinas_iniciales.sql";

    record Modelo(String codigo, String nombre, String marca, String categoria, int tarifaDia, String estado,
                  String incluye, String imagen) { }

    record Spec(String codigo, String clave, String valor, int orden) { }

    record Unidad(String modeloCodigo, String codigo, String estado, boolean exigeModeloDisponible, boolean tieneNotExists) { }

    private static final Pattern TOKEN = Pattern.compile(
            "\\s*(?:'((?:[^']|'')*)'|(NULL|-?\\d+))(?:\\s+AS\\s+\\w+)?\\s*(?:,|$)");
    private static final Pattern WHERE_MODELO = Pattern.compile("WHERE m\\.CODIGO = '([^']+)'");

    private SeedMaquinasSql() {
    }

    /** Sentencias del script sin los comentarios de línea, separadas por ';' al final de línea. */
    static List<String> sentencias(String sql) {
        StringBuilder sinComentarios = new StringBuilder();
        for (String linea : sql.split("\n")) {
            if (!linea.stripLeading().startsWith("--")) {
                sinComentarios.append(linea).append('\n');
            }
        }
        List<String> r = new ArrayList<>();
        for (String s : sinComentarios.toString().split(";[ \\t]*\\n")) {
            if (!s.isBlank()) {
                r.add(s.strip());
            }
        }
        return r;
    }

    /** Lista de literales SQL separados por coma. Devuelve null para NULL; resuelve '' como '. */
    static List<String> literales(String texto) {
        List<String> valores = new ArrayList<>();
        Matcher m = TOKEN.matcher(texto);
        int pos = 0;
        while (pos < texto.length()) {
            m.region(pos, texto.length());
            if (!m.lookingAt()) {
                throw new IllegalStateException("Literal no reconocido cerca de: " + texto.substring(pos, Math.min(pos + 40, texto.length())));
            }
            if (m.group(1) != null) {
                valores.add(m.group(1).replace("''", "'"));
            } else {
                valores.add(m.group(2).equals("NULL") ? null : m.group(2));
            }
            pos = m.end();
        }
        return valores;
    }

    static String leer() throws IOException {
        try (InputStream in = SeedMaquinasSql.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("No existe " + RECURSO);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<Modelo> modelos(String sql) {
        List<Modelo> r = new ArrayList<>();
        for (String s : sentencias(sql)) {
            if (!s.startsWith("MERGE INTO MAQUINAS")) {
                continue;
            }
            int i = s.indexOf("VALUES (") + "VALUES (".length();
            List<String> v = literales(s.substring(i, s.lastIndexOf(')')));
            r.add(new Modelo(v.get(0), v.get(1), v.get(2), v.get(3), Integer.parseInt(v.get(4)), v.get(5), v.get(6), v.get(7)));
        }
        return r;
    }

    static List<Spec> especificaciones(String sql) {
        List<Spec> r = new ArrayList<>();
        for (String s : sentencias(sql)) {
            if (!s.startsWith("INSERT INTO MAQUINA_ESPECIFICACIONES")) {
                continue;
            }
            Matcher w = WHERE_MODELO.matcher(s);
            if (!w.find()) {
                throw new IllegalStateException("Insert de especificaciones sin WHERE m.CODIGO");
            }
            for (String linea : s.split("\n")) {
                String t = linea.strip();
                if (!t.startsWith("SELECT ") || !t.contains(" FROM DUAL") || t.startsWith("SELECT m.ID")) {
                    continue;
                }
                List<String> v = literales(t.substring("SELECT ".length(), t.indexOf(" FROM DUAL")));
                r.add(new Spec(w.group(1), v.get(0), v.get(1), Integer.parseInt(v.get(2))));
            }
        }
        return r;
    }

    static List<Unidad> unidades(String sql) {
        List<Unidad> r = new ArrayList<>();
        for (String s : sentencias(sql)) {
            if (!s.startsWith("INSERT INTO MAQUINA_UNIDADES")) {
                continue;
            }
            Matcher w = WHERE_MODELO.matcher(s);
            if (!w.find()) {
                throw new IllegalStateException("Insert de unidades sin WHERE m.CODIGO");
            }
            int desde = s.indexOf("SELECT m.ID, ") + "SELECT m.ID, ".length();
            int hasta = s.indexOf(" FROM MAQUINAS m");
            List<String> v = literales(s.substring(desde, hasta));
            r.add(new Unidad(w.group(1), v.get(0), v.get(1),
                    s.contains("m.ESTADO = 'DISPONIBLE'"), s.contains("NOT EXISTS (SELECT 1 FROM MAQUINA_UNIDADES u WHERE u.CODIGO = '" + v.get(0) + "')")));
        }
        return r;
    }
}
