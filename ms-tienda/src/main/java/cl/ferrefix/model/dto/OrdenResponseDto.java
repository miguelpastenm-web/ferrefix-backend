package cl.ferrefix.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

public record OrdenResponseDto(
        Long id,
        String codigo,
        String boleta,
        String estado,
        String metodoPago,
        List<Item> items,
        Long subtotal,
        Integer costoEnvio,
        Long total,
        Envio envio,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Solo lo ven los ADMIN (RUT, nombre y email que se copiaron del token al comprar). */
        @JsonInclude(JsonInclude.Include.NON_NULL) Cliente cliente) {

    /** productoId es null si el producto se eliminó después; sku, nombre y precio siempre están. */
    public record Item(Long productoId, String sku, String nombre, Integer precioUnitario, Integer cantidad, Long subtotal) { }

    public record Envio(String telefono, String direccion, String region, String comuna, String indicaciones) { }

    public record Cliente(Long usuarioId, String rut, String nombre, String email) { }
}
