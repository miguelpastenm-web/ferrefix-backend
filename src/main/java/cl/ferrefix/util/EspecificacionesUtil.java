package cl.ferrefix.util;

import cl.ferrefix.exception.BadRequestException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Validación común de especificaciones (clave-valor ordenadas) de productos y máquinas. */
public final class EspecificacionesUtil {

    private EspecificacionesUtil() {
    }

    /**
     * Valida y limpia las especificaciones conservando el orden de llegada.
     * Clave y valor no vacíos, hasta 80 y 300 caracteres, y sin claves repetidas tras recortar espacios.
     */
    public static Map<String, String> normalizar(Map<String, String> entrada) {
        Map<String, String> resultado = new LinkedHashMap<>();
        if (entrada == null) {
            return resultado;
        }
        for (Map.Entry<String, String> e : entrada.entrySet()) {
            String clave = e.getKey() == null ? "" : e.getKey().trim();
            String valor = e.getValue() == null ? "" : e.getValue().trim();
            if (clave.isEmpty() || valor.isEmpty()) {
                throw new BadRequestException("Las especificaciones no pueden tener clave ni valor vacíos");
            }
            if (clave.length() > 80) {
                throw new BadRequestException("La clave de una especificación no puede superar 80 caracteres");
            }
            if (valor.length() > 300) {
                throw new BadRequestException("El valor de la especificación '" + clave + "' supera 300 caracteres");
            }
            if (resultado.put(clave, valor) != null) {
                throw new BadRequestException("Especificación repetida: " + clave);
            }
        }
        return resultado;
    }
}
