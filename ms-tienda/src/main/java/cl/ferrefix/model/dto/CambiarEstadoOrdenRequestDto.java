package cl.ferrefix.model.dto;

import cl.ferrefix.model.EstadoOrden;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoOrdenRequestDto(
        @NotNull(message = "El estado es obligatorio (EN_PREPARACION, DESPACHADA, ENTREGADA o CANCELADA)")
        EstadoOrden estado) {
}
