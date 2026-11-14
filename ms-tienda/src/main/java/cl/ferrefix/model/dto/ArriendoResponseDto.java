package cl.ferrefix.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ArriendoResponseDto(
        Long id,
        String codigo,
        String estado,
        MaquinaResumen maquina,
        String unidadCodigo,
        Integer tarifaDia,
        Integer dias,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Long total,
        String telefonoContacto,
        String direccionFaena,
        String regionFaena,
        String comunaFaena,
        LocalDateTime fechaDevolucion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** Solo lo ven los ADMIN: RUT, nombre y email salen del usuario dueño del arriendo. */
        @JsonInclude(JsonInclude.Include.NON_NULL) ClienteResumen cliente) {

    public record MaquinaResumen(Long id, String codigo, String nombre, String marca, String imagenUrl) { }

    public record ClienteResumen(Long id, String rut, String nombre, String email) { }
}
