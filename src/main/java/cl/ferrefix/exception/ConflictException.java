package cl.ferrefix.exception;

/** Operación que choca con el estado actual del recurso (409): unidad ya arrendada, sin unidades libres, con historial, etc. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
