package cl.ferrefix.model.dto;

import jakarta.validation.constraints.*;

/**
 * Solicitud de cotización (endpoint público). Los largos son los de las columnas de COTIZACIONES (V6).
 * El cuerpo NO puede fijar estado, notaAdmin ni usuarioId: no existen en este DTO.
 */
public record CrearCotizacionRequestDto(
        @NotBlank(message = "El nombre o razón social es obligatorio")
        @Size(min = 3, max = 80, message = "El nombre o razón social debe tener entre 3 y 80 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9ÁÉÍÓÚÜÑáéíóúüñ'’.,&°/ -]*$", message = "El nombre o razón social contiene caracteres no permitidos")
        @Pattern(regexp = ".*[A-Za-zÁÉÍÓÚÜÑáéíóúüñ].*", message = "El nombre o razón social debe incluir al menos una letra")
        String nombre,

        @NotBlank(message = "El RUT es obligatorio")
        @Size(max = 12, message = "El RUT no puede superar 12 caracteres")
        String rut,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 120, message = "El correo no puede superar 120 caracteres")
        String email,

        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        @Pattern(regexp = "^[0-9+()\\- ]{8,20}$", message = "El teléfono solo admite números, +, espacios, guiones y paréntesis (8 a 20 caracteres)")
        String telefono,

        @NotBlank(message = "El tipo de proyecto es obligatorio")
        @Size(max = 60, message = "El tipo de proyecto no puede superar 60 caracteres")
        String tipoProyecto,

        @NotBlank(message = "La región es obligatoria")
        @Size(max = 80, message = "La región no puede superar 80 caracteres")
        String region,

        @NotBlank(message = "El detalle es obligatorio")
        @Size(max = 2000, message = "El detalle no puede superar 2000 caracteres")
        String detalle,

        /** Opcional: si no viene se asume true (como el formulario y la columna). */
        Boolean despachoObra) {
}
