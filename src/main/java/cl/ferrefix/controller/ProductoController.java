package cl.ferrefix.controller;

import cl.ferrefix.model.dto.ProductoRequestDto;
import cl.ferrefix.model.dto.ProductoResponseDto;
import cl.ferrefix.service.ProductoService;
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
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
@Tag(name = "Productos", description = "Catálogo de productos. Lectura pública; crear, editar y eliminar solo ADMIN.")
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @SecurityRequirement(name = "")
    @Operation(summary = "Listar productos", description = "Público. Filtros opcionales por texto (nombre o marca), categoría y marca. "
            + "Orden con sort=campo[,asc|desc]; campos: nombre, marca, categoria, precio, stock, fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de productos"),
            @ApiResponse(responseCode = "400", description = "Categoría o sort inválidos")
    })
    public ResponseEntity<List<ProductoResponseDto>> listar(
            @Parameter(description = "Texto a buscar en nombre o marca") @RequestParam(required = false) String q,
            @Parameter(description = "Categoría exacta (ej. Herramientas)") @RequestParam(required = false) String categoria,
            @Parameter(description = "Marca exacta") @RequestParam(required = false) String marca,
            @Parameter(description = "campo[,asc|desc]", example = "precio,asc") @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(productoService.listar(q, categoria, marca, sort));
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "")
    @Operation(summary = "Obtener producto por id", description = "Público.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado"),
            @ApiResponse(responseCode = "404", description = "No existe")
    })
    public ResponseEntity<ProductoResponseDto> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear producto", description = "Solo ADMIN. El estado se calcula según el stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "409", description = "SKU repetido")
    })
    public ResponseEntity<ProductoResponseDto> crear(@Valid @RequestBody ProductoRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar producto", description = "Solo ADMIN. Reemplaza todos los campos editables y las especificaciones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe"),
            @ApiResponse(responseCode = "409", description = "SKU repetido")
    })
    public ResponseEntity<ProductoResponseDto> actualizar(@PathVariable Long id,
                                                          @Valid @RequestBody ProductoRequestDto request) {
        return ResponseEntity.ok(productoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar producto", description = "Solo ADMIN. Borrado físico.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Producto eliminado"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "404", description = "No existe")
    })
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
