package cl.ferrefix.util;

import java.util.regex.Pattern;

/** Validación de RUT chileno (dígito verificador, módulo 11). */
public final class RutValidator {

    private static final Pattern FORMATO = Pattern.compile("^\\d{7,8}-[0-9K]$");

    private RutValidator() {
    }

    /**
     * Quita puntos y espacios, pasa a mayúsculas y deja el formato "12345678-5".
     * Devuelve null si el texto no tiene forma de RUT.
     */
    public static String normalizar(String rut) {
        if (rut == null) {
            return null;
        }
        String limpio = rut.replace(".", "").replace(" ", "").toUpperCase();
        if (limpio.length() < 2) {
            return null;
        }
        if (!limpio.contains("-")) {
            limpio = limpio.substring(0, limpio.length() - 1) + "-" + limpio.charAt(limpio.length() - 1);
        }
        return FORMATO.matcher(limpio).matches() ? limpio : null;
    }

    public static boolean esValido(String rut) {
        String normalizado = normalizar(rut);
        if (normalizado == null) {
            return false;
        }
        String cuerpo = normalizado.substring(0, normalizado.indexOf('-'));
        char dv = normalizado.charAt(normalizado.length() - 1);
        return calcularDv(cuerpo) == dv;
    }

    static char calcularDv(String cuerpo) {
        int suma = 0;
        int factor = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        if (resto == 11) {
            return '0';
        }
        if (resto == 10) {
            return 'K';
        }
        return (char) ('0' + resto);
    }
}
