package cl.ferrefix.model.dto;

import java.time.LocalDateTime;

/**
 * Acuse de recibo del POST público. A propósito NO devuelve RUT, email ni teléfono: el endpoint es abierto y
 * esos datos solo los puede leer un ADMIN.
 */
public record CotizacionCreadaResponseDto(Long id, String codigo, String estado, LocalDateTime createdAt) {
}
