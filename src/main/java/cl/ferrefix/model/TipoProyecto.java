package cl.ferrefix.model;

import java.util.List;
import java.util.Optional;

/**
 * Los 6 textos del selector "Tipo de proyecto" del formulario de ferrefix-ui. Son los mismos del CHECK
 * CK_COTIZACIONES_TIPO de V6 (EsquemaV6Test lo comprueba): si cambia uno hay que cambiar el otro con una migración nueva.
 */
public final class TipoProyecto {

    public static final List<String> TODOS = List.of(
            "Herramientas de Alto Rendimiento",
            "Materiales de Obra Gruesa",
            "Fijaciones y Tornillería por Mayor",
            "Arriendo de Maquinaria y Demoledores",
            "Equipamiento de Seguridad y Faena",
            "Otro Requerimiento Especial");

    private TipoProyecto() {
    }

    /** Devuelve el texto oficial (ignora mayúsculas y espacios alrededor) o vacío si no es uno de los 6. */
    public static Optional<String> canonica(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String limpio = valor.trim();
        return TODOS.stream().filter(t -> t.equalsIgnoreCase(limpio)).findFirst();
    }
}
