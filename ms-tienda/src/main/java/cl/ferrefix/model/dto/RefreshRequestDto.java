package cl.ferrefix.model.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDto(
        @NotBlank(message = "El refreshToken es obligatorio")
        String refreshToken) {
}
