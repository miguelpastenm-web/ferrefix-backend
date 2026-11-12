package cl.ferrefix.controller;

import cl.ferrefix.model.dto.ActualizarArriendoRequestDto;
import cl.ferrefix.model.dto.ArriendoResponseDto;
import cl.ferrefix.model.dto.CrearArriendoRequestDto;
import cl.ferrefix.service.ArriendoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/arriendos")
@RequiredArgsConstructor
@Tag(name = "Arriendos", description = "Arriendo de máquinas. Arrendar y ver los propios: CLIENTE. Gestión: ADMIN. "
        + "El servidor asigna la unidad, la tarifa y el total; RUT, nombre y email salen del token.")
public class ArriendoController {

    private final ArriendoService arriendoService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Arrendar una máquina",
            description = "Solo CLIENTE (ADMIN recibe 403). Se elige el modelo; el servidor asigna la primera unidad libre por código, "
                    + "copia la tarifa del modelo y calcula total = tarifa × días y fin = inicio + días − 1. "
                    + "Días 1 a 90; la fecha de inicio no puede ser anterior a hoy (hora de Chile) ni superar 365 días.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Arriendo creado"),
            @ApiResponse(responseCode = "400", description = "Datos o fechas inválidos"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es CLIENTE"),
            @ApiResponse(responseCode = "404", description = "La máquina no existe"),
            @ApiResponse(responseCode = "409", description = "No quedan unidades disponibles")})
    public ResponseEntity<ArriendoResponseDto> crear(Authentication auth, @Valid @RequestBody CrearArriendoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(arriendoService.crear(auth.getName(), request));
    }

    @GetMapping("/mis-arriendos")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Mis arriendos", description = "Solo CLIENTE. Los arriendos del usuario del token, del más reciente al más antiguo.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es CLIENTE")})
    public ResponseEntity<List<ArriendoResponseDto>> misArriendos(Authentication auth) {
        return ResponseEntity.ok(arriendoService.misArriendos(auth.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un arriendo",
            description = "CLIENTE: solo los suyos (uno ajeno responde 404). ADMIN: cualquiera, incluyendo RUT, nombre y email del cliente.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Arriendo"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "404", description = "No existe o no es suyo")})
    public ResponseEntity<ArriendoResponseDto> obtener(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(arriendoService.obtener(id, auth.getName(), esAdmin(auth)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todos los arriendos", description = "Solo ADMIN. Filtros opcionales: estado (VIGENTE o FINALIZADO), usuarioId y maquinaId.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado"),
            @ApiResponse(responseCode = "400", description = "Estado inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN")})
    public ResponseEntity<List<ArriendoResponseDto>> listar(
            @Parameter(description = "VIGENTE o FINALIZADO") @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Long maquinaId) {
        return ResponseEntity.ok(arriendoService.listar(estado, usuarioId, maquinaId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar contacto y faena", description = "Solo ADMIN y solo arriendos VIGENTES. Edita teléfono, dirección, región y comuna; "
            + "no cambia unidad, días, fechas, tarifa ni total.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe"),
            @ApiResponse(responseCode = "409", description = "El arriendo ya fue devuelto")})
    public ResponseEntity<ArriendoResponseDto> actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarArriendoRequestDto request) {
        return ResponseEntity.ok(arriendoService.actualizar(id, request));
    }

    @PatchMapping("/{id}/devolver")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar la devolución", description = "Solo ADMIN. Marca el arriendo FINALIZADO con la fecha de devolución y deja la unidad DISPONIBLE.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Devuelto"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe"),
            @ApiResponse(responseCode = "409", description = "Ya estaba devuelto")})
    public ResponseEntity<ArriendoResponseDto> devolver(@PathVariable Long id) {
        return ResponseEntity.ok(arriendoService.devolver(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar un arriendo", description = "Solo ADMIN. Si el arriendo estaba VIGENTE, libera la unidad.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Eliminado"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe")})
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        arriendoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private static boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
