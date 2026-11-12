package cl.ferrefix.controller;

import cl.ferrefix.model.dto.*;
import cl.ferrefix.service.MaquinaService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/maquinas")
@RequiredArgsConstructor
@Tag(name = "Máquinas", description = "Catálogo de máquinas para arriendo y sus unidades. Catálogo público; "
        + "crear, editar, eliminar y gestionar unidades solo ADMIN.")
public class MaquinaController {

    private final MaquinaService maquinaService;

    @GetMapping
    @SecurityRequirement(name = "")
    @Operation(summary = "Listar máquinas", description = "Público. Incluye unidadesTotales y unidadesDisponibles. "
            + "Orden con sort=campo[,asc|desc]; campos: nombre, marca, categoria, tarifa, fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de modelos"),
            @ApiResponse(responseCode = "400", description = "sort inválido")
    })
    public ResponseEntity<List<MaquinaResponseDto>> listar(
            @Parameter(description = "Texto a buscar en nombre, marca o código") @RequestParam(required = false) String q,
            @Parameter(description = "Categoría") @RequestParam(required = false) String categoria,
            @Parameter(description = "Solo modelos con al menos una unidad libre") @RequestParam(defaultValue = "false") boolean soloDisponibles,
            @Parameter(description = "campo[,asc|desc]", example = "tarifa,asc") @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(maquinaService.listar(q, categoria, soloDisponibles, sort));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "")
    @Operation(summary = "Obtener máquina por id", description = "Público.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Encontrada"),
            @ApiResponse(responseCode = "404", description = "No existe")})
    public ResponseEntity<MaquinaResponseDto> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(maquinaService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear máquina (modelo)", description = "Solo ADMIN. Nace sin unidades, por lo que queda NO_DISPONIBLE hasta dar de alta unidades.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Sin token o inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "409", description = "Código repetido")})
    public ResponseEntity<MaquinaResponseDto> crear(@Valid @RequestBody MaquinaRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maquinaService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar máquina", description = "Solo ADMIN. Reemplaza los campos y las especificaciones; no cambia las unidades ni el estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe"),
            @ApiResponse(responseCode = "409", description = "Código repetido")})
    public ResponseEntity<MaquinaResponseDto> actualizar(@PathVariable Long id, @Valid @RequestBody MaquinaRequestDto request) {
        return ResponseEntity.ok(maquinaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar máquina", description = "Solo ADMIN. Borra el modelo y sus unidades; 409 si alguna unidad tiene arriendos registrados.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe"),
            @ApiResponse(responseCode = "409", description = "Tiene arriendos registrados")})
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        maquinaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- unidades (ADMIN)
    @GetMapping("/{id}/unidades")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar unidades de una máquina", description = "Solo ADMIN. El público solo ve los conteos en el modelo.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Unidades"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe la máquina")})
    public ResponseEntity<List<UnidadResponseDto>> listarUnidades(@PathVariable Long id) {
        return ResponseEntity.ok(maquinaService.listarUnidades(id));
    }

    @PostMapping("/{id}/unidades")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dar de alta unidades", description = "Solo ADMIN. Crea de 1 a 20 unidades DISPONIBLES con código autogenerado (<código>-U01, ...).")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Unidades creadas"),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe la máquina")})
    public ResponseEntity<List<UnidadResponseDto>> agregarUnidades(@PathVariable Long id, @Valid @RequestBody UnidadesRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(maquinaService.agregarUnidades(id, request.cantidad()));
    }

    @DeleteMapping("/{id}/unidades/{unidadId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar una unidad", description = "Solo ADMIN. Solo si está DISPONIBLE y sin arriendos en su historial; si no, 409.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe la máquina o la unidad"),
            @ApiResponse(responseCode = "409", description = "Arrendada o con historial")})
    public ResponseEntity<Void> eliminarUnidad(@PathVariable Long id, @PathVariable Long unidadId) {
        maquinaService.eliminarUnidad(id, unidadId);
        return ResponseEntity.noContent().build();
    }
}
