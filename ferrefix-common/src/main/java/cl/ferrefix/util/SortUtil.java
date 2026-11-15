package cl.ferrefix.util;

import cl.ferrefix.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** Convierte el parámetro "campo[,asc|desc]" en un Sort, aceptando solo campos de una lista cerrada. */
public final class SortUtil {

    private SortUtil() {
    }

    /** @param camposPermitidos alias público -> propiedad de la entidad. Orden por defecto: nombre asc. */
    public static Sort parsear(String sort, Map<String, String> camposPermitidos) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.asc("nombre"), Sort.Order.asc("id"));
        }
        String[] partes = sort.split(",", -1);
        String propiedad = camposPermitidos.get(partes[0].trim().toLowerCase(Locale.ROOT));
        String dir = partes.length == 2 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (partes.length > 2 || propiedad == null || !(dir.equals("asc") || dir.equals("desc"))) {
            throw new BadRequestException("Parámetro sort inválido. Use campo[,asc|desc] con campo en "
                    + new TreeSet<>(camposPermitidos.keySet()));
        }
        Sort.Direction direccion = dir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(new Sort.Order(direccion, propiedad), Sort.Order.asc("id"));
    }
}
