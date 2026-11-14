package cl.ferrefix.seed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee V3__productos_iniciales.sql (el generado) y lo convierte en objetos, para validarlo sin Oracle. */
final class SeedSql {

    static final String RECURSO = "/db/migration/V3__productos_iniciales.sql";

    record Producto(String sku, String nombre, String marca, String categoria, int precio, int stock,
                    String estado, String imagen, String imagenOferta, String descripcion) { }

    record Spec(String sku, String clave, String valor, int orden) { }

    private static final Pattern TOKEN = Pattern.compile(
            "\\s*(?:'((?:[^']|'')*)'|(NULL|-?\\d+))(?:\\s+AS\\s+\\w+)?\\s*(?:,|$)");
    private static final Pattern SKU_WHERE = Pattern.compile("WHERE p\\.SKU = '([^']+)'");

    private SeedSql() {
    }

    static String leer() throws IOException {
        try (InputStream in = SeedSql.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("No existe " + RECURSO);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Sentencias sin comentarios de línea, separadas por ';' al final de línea. */
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

    static List<Producto> productos(String sql) {
        List<Producto> r = new ArrayList<>();
        for (String s : sentencias(sql)) {
            if (!s.startsWith("MERGE INTO PRODUCTOS")) {
                continue;
            }
            int i = s.indexOf("VALUES (") + "VALUES (".length();
            List<String> v = literales(s.substring(i, s.lastIndexOf(')')));
            r.add(new Producto(v.get(0), v.get(1), v.get(2), v.get(3), Integer.parseInt(v.get(4)),
                    Integer.parseInt(v.get(5)), v.get(6), v.get(7), v.get(8), v.get(9)));
        }
        return r;
    }

    static List<Spec> especificaciones(String sql) {
        List<Spec> r = new ArrayList<>();
        for (String s : sentencias(sql)) {
            if (!s.startsWith("INSERT INTO PRODUCTO_ESPECIFICACIONES")) {
                continue;
            }
            Matcher w = SKU_WHERE.matcher(s);
            if (!w.find()) {
                throw new IllegalStateException("Insert de especificaciones sin WHERE p.SKU");
            }
            String sku = w.group(1);
            for (String linea : s.split("\n")) {
                String t = linea.strip();
                if (!t.startsWith("SELECT ") || !t.contains(" FROM DUAL") || t.startsWith("SELECT p.ID")) {
                    continue;
                }
                String contenido = t.substring("SELECT ".length(), t.indexOf(" FROM DUAL"));
                List<String> v = literales(contenido);
                r.add(new Spec(sku, v.get(0), v.get(1), Integer.parseInt(v.get(2))));
            }
        }
        return r;
    }

    static List<String> urlsDeImagen(List<Producto> productos) {
        return productos.stream()
                .flatMap(p -> Arrays.stream(new String[]{p.imagen(), p.imagenOferta()}))
                .filter(u -> u != null)
                .toList();
    }
}
