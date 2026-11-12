package cl.ferrefix.exception;

import java.util.List;

/** Operación que choca con el estado actual del recurso (409): sin stock, unidad arrendada, transición inválida, etc. */
public class ConflictException extends RuntimeException {

    private final List<String> detalles;

    public ConflictException(String message) {
        this(message, List.of());
    }

    public ConflictException(String message, List<String> detalles) {
        super(message);
        this.detalles = List.copyOf(detalles);
    }

    /** Detalle línea a línea (por ejemplo, qué productos no tienen stock). Vacío si no aplica. */
    public List<String> getDetalles() {
        return detalles;
    }
}
