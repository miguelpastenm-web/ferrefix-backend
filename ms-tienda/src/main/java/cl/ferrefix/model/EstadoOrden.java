package cl.ferrefix.model;

/** Ciclo de vida de una orden. Solo se avanza un paso por vez; se puede cancelar antes del despacho. */
public enum EstadoOrden {
    PAGADA,
    EN_PREPARACION,
    DESPACHADA,
    ENTREGADA,
    CANCELADA;

    public boolean puedePasarA(EstadoOrden destino) {
        return switch (this) {
            case PAGADA -> destino == EN_PREPARACION || destino == CANCELADA;
            case EN_PREPARACION -> destino == DESPACHADA || destino == CANCELADA;
            case DESPACHADA -> destino == ENTREGADA;
            case ENTREGADA, CANCELADA -> false;
        };
    }

    /** Estados finales: ya no cambian y son los únicos en los que se permite eliminar la orden. */
    public boolean esFinal() {
        return this == ENTREGADA || this == CANCELADA;
    }
}
