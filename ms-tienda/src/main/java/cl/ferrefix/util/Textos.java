package cl.ferrefix.util;

public final class Textos {

    private Textos() {
    }

    /** Recorta espacios; un texto vacío o en blanco pasa a null. */
    public static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
