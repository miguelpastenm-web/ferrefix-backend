package cl.ferrefix.service.impl;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.*;
import cl.ferrefix.model.mapper.UsuarioMapper;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.security.JwtService;
import cl.ferrefix.security.JwtVerificador;
import cl.ferrefix.service.AuthService;
import cl.ferrefix.util.RutValidator;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtVerificador jwtVerificador;
    private final UsuarioMapper usuarioMapper;

    @Override
    @Transactional(readOnly = true)
    public TokenResponseDto login(LoginRequestDto request) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException(CREDENCIALES_INVALIDAS));

        // Mismo mensaje para usuario inexistente, clave incorrecta o cuenta inactiva (no filtra qué falló).
        if (!usuario.isActivo() || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        return emitirTokens(usuario);
    }

    @Override
    @Transactional
    public TokenResponseDto register(RegisterRequestDto request) {
        String rut = RutValidator.normalizar(request.rut());
        if (rut == null || !RutValidator.esValido(rut)) {
            throw new BadRequestException("El RUT ingresado no es válido");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Ya existe una cuenta registrada con este correo electrónico");
        }
        if (usuarioRepository.existsByRut(rut)) {
            throw new DuplicateResourceException("Ya existe una cuenta registrada con este RUT");
        }

        Usuario usuario = usuarioMapper.toEntity(request, rut, email);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setRoles(Set.of(Rol.CLIENTE)); // el registro público nunca crea ADMIN
        return emitirTokens(usuarioRepository.save(usuario));
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponseDto refresh(RefreshRequestDto request) {
        String email;
        try {
            email = jwtVerificador.validarRefreshToken(request.refreshToken()).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("Refresh token inválido o vencido");
        }
        // El refresh token solo identifica al usuario: nombre, RUT y ROLES se leen de la base en este momento, así
        // el access token nuevo refleja los datos vigentes (un rol quitado o una cuenta desactivada dejan de valer aquí).
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o vencido"));
        return emitirTokens(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponseDto me(String email) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (!usuario.isActivo()) {
            // El token puede seguir vigente unos minutos tras desactivar la cuenta: aquí, que sí hay base, se rechaza.
            throw new BadCredentialsException("Token ausente, inválido o vencido");
        }
        return usuarioMapper.toResponse(usuario);
    }

    private TokenResponseDto emitirTokens(Usuario usuario) {
        return new TokenResponseDto(
                jwtService.generarAccessToken(usuario),
                jwtService.generarRefreshToken(usuario),
                "Bearer",
                jwtService.getAccessExpirationSeconds(),
                usuarioMapper.toResponse(usuario));
    }
}
