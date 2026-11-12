package cl.ferrefix.controller;

import cl.ferrefix.model.dto.*;
import cl.ferrefix.service.CotizacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cotizaciones")
@RequiredArgsConstructor
@Tag(name = "Cotizaciones", description = "Solicitudes de cotización por volumen. Crear es público (cualquier visitante); "
        + "listar, ver, cambiar estado, escribir la nota y eliminar son solo ADMIN. No existe 'mis cotizaciones'.")
public class CotizacionController {

    private final CotizacionService cotizacionService;

    @PostMapping
    @SecurityRequirement(name = "")
    @Operation(summary = "Solicitar una cotización (público)",
            description = "No requiere sesión. Si la petición trae un token VÁLIDO, la cotización queda asociada a ese usuario "
                    + "(cualquier rol); con un token inválido o vencido se trata como anónima (no responde 401). "
                    + "El RUT se valida con módulo 11 y el tipo de proyecto debe ser uno de los 6 textos del formulario. "
                    + "La cotización nace PENDIENTE y la respuesta es solo un acuse (id, código, estado y fecha), sin datos personales. "
                    + "LIMITACIÓN CONOCIDA: este endpoint público no tiene límite de peticiones por IP ni captcha; las únicas defensas "
                    + "son los largos máximos por campo y las validaciones, que se aplican después de leer el JSON.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cotización registrada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (campos, RUT, tipo de proyecto o detalle)")})
    public ResponseEntity<CotizacionCreadaResponseDto> crear(Authentication auth, @Valid @RequestBody CrearCotizacionRequestDto request) {
        // Independiente del tipo de principal que deje el filtro JWT: solo importa si hay una autenticación real.
        String email = (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) ? null : auth.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(cotizacionService.crear(email, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar cotizaciones", description = "Solo ADMIN. De la más nueva a la más antigua. Filtro opcional por estado.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado"),
            @ApiResponse(responseCode = "400", description = "Estado inválido"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN")})
    public ResponseEntity<List<CotizacionResponseDto>> listar(
            @Parameter(description = "PENDIENTE, EN_REVISION, RESPONDIDA o CERRADA") @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(cotizacionService.listar(estado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ver una cotización", description = "Solo ADMIN. Incluye todos los datos de contacto.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Cotización"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe")})
    public ResponseEntity<CotizacionResponseDto> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(cotizacionService.obtener(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar el estado y/o la nota",
            description = "Solo ADMIN. Enviar al menos uno de: estado (PENDIENTE, EN_REVISION, RESPONDIDA, CERRADA; cualquiera a cualquiera) "
                    + "o notaAdmin (si no viene, no cambia; si viene vacía, se borra; si trae texto, reemplaza la anterior).")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Cotización actualizada"),
            @ApiResponse(responseCode = "400", description = "No se envió estado ni nota, estado inválido o nota demasiado larga"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe")})
    public ResponseEntity<CotizacionResponseDto> actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarCotizacionRequestDto request) {
        return ResponseEntity.ok(cotizacionService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar una cotización", description = "Solo ADMIN. En cualquier estado.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe")})
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        cotizacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
