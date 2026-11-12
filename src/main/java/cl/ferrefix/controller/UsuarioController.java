package cl.ferrefix.controller;

import cl.ferrefix.model.dto.ActualizarPerfilRequestDto;
import cl.ferrefix.model.dto.UsuarioResponseDto;
import cl.ferrefix.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Perfil", description = "Datos del propio usuario autenticado (CLIENTE o ADMIN). No existe edición de otros usuarios.")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Editar mi perfil",
            description = "Cualquier usuario autenticado edita SU perfil (la identidad sale del token). Reemplaza nombre, teléfono, "
                    + "dirección, región y comuna: un opcional omitido o en blanco se borra (queda null). "
                    + "RUT, email, contraseña, roles y estado no se pueden cambiar por aquí y se ignoran si vienen en el cuerpo. "
                    + "El token sigue siendo válido (no cambia el email), así que no hay que volver a iniciar sesión.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado (misma forma que GET /auth/me)"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "401", description = "Sin token, token inválido o vencido"),
            @ApiResponse(responseCode = "404", description = "El usuario del token ya no existe")})
    public ResponseEntity<UsuarioResponseDto> actualizarPerfil(Authentication auth, @Valid @RequestBody ActualizarPerfilRequestDto request) {
        return ResponseEntity.ok(usuarioService.actualizarPerfil(auth.getName(), request));
    }
}
