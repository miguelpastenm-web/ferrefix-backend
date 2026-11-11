package cl.ferrefix.model;

import java.util.List;
import java.util.Optional;

/** Categorías válidas (las mismas del front y del CHECK de la tabla PRODUCTOS). */
public final class CategoriaProducto {

    public static final List<String> TODAS = List.of(
            "Construcción", "Maderas", "Herramientas", "Fijaciones", "Gasfitería",
            "Electricidad", "Pinturas", "Cerámicas", "Seguridad");

    private CategoriaProducto() {
    }

    /** Devuelve la categoría con su escritura oficial (ignora mayúsculas) o vacío si no existe. */
    public static Optional<String> canonica(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String limpio = valor.trim();
        return TODAS.stream().filter(c -> c.equalsIgnoreCase(limpio)).findFirst();
    }
}
