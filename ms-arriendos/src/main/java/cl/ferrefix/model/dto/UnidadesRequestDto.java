package cl.ferrefix.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UnidadesRequestDto(
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 20, message = "La cantidad máxima por alta es 20")
        Integer cantidad) {
}
