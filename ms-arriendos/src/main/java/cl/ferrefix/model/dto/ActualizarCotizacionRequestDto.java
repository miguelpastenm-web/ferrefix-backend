package cl.ferrefix.model.dto;

import cl.ferrefix.model.EstadoCotizacion;
import jakarta.validation.constraints.Size;

/**
 * Actualización parcial por un ADMIN. Hay que enviar al menos uno de los dos campos.
 * notaAdmin: si no viene, la nota no cambia; si viene vacía, se borra; si trae texto, reemplaza la anterior.
 */
public record ActualizarCotizacionRequestDto(
        EstadoCotizacion estado,

        @Size(max = 1000, message = "La nota no puede superar 1000 caracteres")
        String notaAdmin) {
}
