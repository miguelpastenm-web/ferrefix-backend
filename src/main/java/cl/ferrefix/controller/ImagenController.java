package cl.ferrefix.controller;

import cl.ferrefix.model.dto.ImagenResponseDto;
import cl.ferrefix.service.ImagenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imagenes")
@RequiredArgsConstructor
@Tag(name = "Imágenes", description = "Subida de imágenes (solo ADMIN). Se sirven públicamente en /media/**.")
public class ImagenController {

    private final ImagenService imagenService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Subir imagen",
            description = "Solo ADMIN. JPEG, PNG, WEBP o AVIF de hasta 6 MB. El tipo se valida por el contenido del archivo. "
                    + "Devuelve la URL relativa (/media/<destino>/<uuid>.<ext>) para guardarla en imagenUrl del producto.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Imagen guardada"),
            @ApiResponse(responseCode = "400", description = "Destino inválido, archivo vacío o no es una imagen"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN"),
            @ApiResponse(responseCode = "413", description = "Supera el tamaño máximo (6 MB)")
    })
    public ResponseEntity<ImagenResponseDto> subir(
            @RequestPart("archivo") MultipartFile archivo,
            @Parameter(description = "productos, ofertas o banners", example = "productos")
            @RequestParam("destino") String destino) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imagenService.guardar(archivo, destino));
    }
}
