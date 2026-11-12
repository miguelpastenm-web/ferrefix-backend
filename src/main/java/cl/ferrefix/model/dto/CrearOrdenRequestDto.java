package cl.ferrefix.model.dto;

import cl.ferrefix.model.MetodoPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * El cliente envía solo producto y cantidad por línea, el medio de pago y los datos de envío.
 * Precios, subtotal, envío y total los calcula el servidor; RUT, nombre y email salen del token.
 */
public record CrearOrdenRequestDto(
        @NotEmpty(message = "La orden debe tener al menos un producto")
        @Size(max = 50, message = "Una orden admite hasta 50 productos distintos")
        List<@NotNull(message = "Hay una línea vacía") @Valid LineaOrden> items,

        @NotNull(message = "El método de pago es obligatorio (WEBPAY_DEBITO, TARJETA_CREDITO o TRANSFERENCIA)")
        MetodoPago metodoPago,

        @NotBlank(message = "El teléfono de contacto es obligatorio")
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        @Pattern(regexp = "^[0-9+()\\- ]{8,20}$", message = "El teléfono solo admite números, +, espacios, guiones y paréntesis (8 a 20 caracteres)")
        String telefono,

        @NotBlank(message = "La dirección de envío es obligatoria")
        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String direccionEnvio,

        @NotBlank(message = "La región es obligatoria")
        @Size(max = 80, message = "La región no puede superar 80 caracteres")
        String region,

        @NotBlank(message = "La comuna es obligatoria")
        @Size(max = 80, message = "La comuna no puede superar 80 caracteres")
        String comuna,

        @Size(max = 500, message = "Las indicaciones no pueden superar 500 caracteres")
        String indicaciones) {

    public record LineaOrden(
            @NotNull(message = "El producto es obligatorio") Long productoId,
            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 1, message = "La cantidad mínima por producto es 1")
            @Max(value = 1000, message = "La cantidad máxima por producto es 1000")
            Integer cantidad) { }
}
