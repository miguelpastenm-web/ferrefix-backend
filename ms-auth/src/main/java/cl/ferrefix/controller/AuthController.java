package cl.ferrefix.controller;

import cl.ferrefix.model.dto.*;
import cl.ferrefix.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Login, registro, renovación de token (JWT) y datos del usuario autenticado")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirement(name = "")
    @Operation(summary = "Iniciar sesión", description = "Valida correo y contraseña y devuelve accessToken (corto) y refreshToken (largo).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa"),
            @ApiResponse(responseCode = "400", description = "Faltan campos o tienen formato inválido"),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas")
    })
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @SecurityRequirement(name = "")
    @Operation(summary = "Registrar cliente", description = "Crea una cuenta con rol CLIENTE (valida el RUT con módulo 11) y deja la sesión iniciada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o RUT inválido"),
            @ApiResponse(responseCode = "409", description = "Correo o RUT ya registrados")
    })
    public ResponseEntity<TokenResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/refresh")
    @SecurityRequirement(name = "")
    @Operation(summary = "Renovar token", description = "Recibe un refreshToken vigente y devuelve un par nuevo de tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido o vencido")
    })
    public ResponseEntity<TokenResponseDto> refresh(@Valid @RequestBody RefreshRequestDto request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Usuario autenticado", description = "Devuelve los datos del dueño del token. El front lo usa para restaurar la sesión al recargar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actual"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o vencido")
    })
    public ResponseEntity<UsuarioResponseDto> me(Authentication authentication) {
        return ResponseEntity.ok(authService.me(authentication.getName()));
    }
}
