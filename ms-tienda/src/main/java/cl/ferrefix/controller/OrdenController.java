package cl.ferrefix.controller;

import cl.ferrefix.model.dto.CambiarEstadoOrdenRequestDto;
import cl.ferrefix.model.dto.CrearOrdenRequestDto;
import cl.ferrefix.model.dto.OrdenResponseDto;
import cl.ferrefix.service.OrdenService;
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
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@Tag(name = "Órdenes", description = "Compra de productos (carrito → orden). Comprar y ver las propias: CLIENTE (ADMIN no compra). "
        + "Gestionar estado y eliminar: ADMIN. El servidor calcula precios y total y descuenta el stock sin sobreventa.")
public class OrdenController {

    private final OrdenService ordenService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Crear una orden de compra",
            description = "Solo CLIENTE. Se envía producto y cantidad por línea, el medio de pago (simulado) y los datos de envío. "
                    + "Precios, subtotal, envío (gratis si el subtotal supera $50.000; si no $3.990) y total los calcula el servidor; "
                    + "RUT, nombre y email salen del token. Todo o nada: si una línea no tiene stock, no se descuenta ninguna.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada en estado PAGADA"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o producto repetido"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es CLIENTE (un ADMIN no puede comprar)"),
            @ApiResponse(responseCode = "404", description = "Algún producto no existe"),
            @ApiResponse(responseCode = "409", description = "Producto agotado, stock insuficiente, o recurso ocupado (reintente)")})
    public ResponseEntity<OrdenResponseDto> crear(Authentication auth, @Valid @RequestBody CrearOrdenRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenService.crear(auth.getName(), request));
    }

    @GetMapping("/mis-ordenes")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Mis órdenes", description = "Solo CLIENTE. Las órdenes del usuario del token, de la más reciente a la más antigua.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es CLIENTE")})
    public ResponseEntity<List<OrdenResponseDto>> misOrdenes(Authentication auth) {
        return ResponseEntity.ok(ordenService.misOrdenes(auth.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener una orden (boleta)",
            description = "CLIENTE: solo las suyas (una ajena responde 404). ADMIN: cualquiera, con RUT, nombre y email del cliente.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Orden"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "404", description = "No existe o no es suya")})
    public ResponseEntity<OrdenResponseDto> obtener(Authentication auth, @PathVariable Long id) {
        boolean esAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(ordenService.obtener(id, auth.getName(), esAdmin));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todas las órdenes", description = "Solo ADMIN. Filtros opcionales: estado y usuarioId.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado"),
            @ApiResponse(responseCode = "400", description = "Estado inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN")})
    public ResponseEntity<List<OrdenResponseDto>> listar(
            @Parameter(description = "PAGADA, EN_PREPARACION, DESPACHADA, ENTREGADA o CANCELADA") @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long usuarioId) {
        return ResponseEntity.ok(ordenService.listar(estado, usuarioId));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar el estado de una orden",
            description = "Solo ADMIN. Avanza un paso por vez: PAGADA → EN_PREPARACION → DESPACHADA → ENTREGADA. "
                    + "CANCELADA se permite desde PAGADA o EN_PREPARACION y DEVUELVE el stock de la orden.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400", description = "Estado inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe"),
            @ApiResponse(responseCode = "409", description = "Transición no permitida")})
    public ResponseEntity<OrdenResponseDto> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoOrdenRequestDto request) {
        return ResponseEntity.ok(ordenService.cambiarEstado(id, request.estado()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar una orden", description = "Solo ADMIN y solo si está ENTREGADA o CANCELADA. Nunca modifica el stock.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Eliminada"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe"),
            @ApiResponse(responseCode = "409", description = "La orden aún no está en un estado final")})
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ordenService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
