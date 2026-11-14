package cl.ferrefix.model.dto;

import java.time.LocalDateTime;

public record UnidadResponseDto(Long id, Long maquinaId, String codigo, String estado, LocalDateTime createdAt) {
}
