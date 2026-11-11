package cl.ferrefix.service;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.*;
import cl.ferrefix.model.mapper.UsuarioMapper;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.security.JwtService;
import cl.ferrefix.service.impl.AuthServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UsuarioRepository repo;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Spy UsuarioMapper mapper = new UsuarioMapper();
    @InjectMocks AuthServiceImpl service;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setRut("15432890-4");
        usuario.setNombre("Juan Maestro");
        usuario.setEmail("cliente@ferrefix.cl");
        usuario.setPasswordHash("$2a$hash");
        usuario.setActivo(true);
        usuario.setRoles(Set.of(Rol.CLIENTE));
    }

    private void tokensStub() {
        when(jwt.generarAccessToken(any())).thenReturn("ACCESS");
        when(jwt.generarRefreshToken(any())).thenReturn("REFRESH");
        when(jwt.getAccessExpirationSeconds()).thenReturn(900L);
    }

    private RegisterRequestDto registro(String rut, String email) {
        return new RegisterRequestDto(rut, " Ana Pérez ", email, "clave1234", "+56 9 1234 5678", "Calle 1", "Región Metropolitana", "Providencia");
    }

    // ---- login
    @Test
    void loginOkEmiteTokensYNoExponeHash() {
        tokensStub();
        when(repo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(usuario));
        when(encoder.matches("clave1234", "$2a$hash")).thenReturn(true);

        TokenResponseDto r = service.login(new LoginRequestDto("cliente@ferrefix.cl", "clave1234"));

        assertEquals("ACCESS", r.accessToken());
        assertEquals("REFRESH", r.refreshToken());
        assertEquals("Bearer", r.tokenType());
        assertEquals(900L, r.expiresIn());
        assertEquals(java.util.List.of("CLIENTE"), r.usuario().roles());
        assertFalse(r.toString().contains("$2a$hash"));
    }

    @Test
    void loginClaveIncorrecta() {
        when(repo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(usuario));
        when(encoder.matches(any(), any())).thenReturn(false);
        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequestDto("cliente@ferrefix.cl", "mala")));
        verifyNoInteractions(jwt);
    }

    @Test
    void loginUsuarioInexistenteMismoMensaje() {
        when(repo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequestDto("nadie@x.cl", "x")));
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void loginCuentaInactivaSeRechazaSinComprobarClave() {
        usuario.setActivo(false);
        when(repo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(usuario));
        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequestDto("cliente@ferrefix.cl", "clave1234")));
        verify(encoder, never()).matches(any(), any());
    }

    // ---- register
    @Test
    void registerCreaClienteConHashRutNormalizadoYEmailMinuscula() {
        tokensStub();
        when(repo.existsByEmailIgnoreCase("ana@x.cl")).thenReturn(false);
        when(repo.existsByRut("11111111-1")).thenReturn(false);
        when(encoder.encode("clave1234")).thenReturn("HASH");
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        TokenResponseDto r = service.register(registro("11.111.111-1", " Ana@X.cl "));

        ArgumentCaptor<Usuario> cap = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).save(cap.capture());
        Usuario guardado = cap.getValue();
        assertEquals("11111111-1", guardado.getRut());
        assertEquals("ana@x.cl", guardado.getEmail());
        assertEquals("Ana Pérez", guardado.getNombre());
        assertEquals("HASH", guardado.getPasswordHash());
        assertEquals(Set.of(Rol.CLIENTE), guardado.getRoles());
        assertTrue(guardado.isActivo());
        assertEquals("ACCESS", r.accessToken());
    }

    @Test
    void registerRutInvalidoModulo11() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.register(registro("11111111-2", "a@x.cl")));
        assertTrue(ex.getMessage().contains("RUT"));
        verify(repo, never()).save(any());
    }

    @Test
    void registerRutConFormatoRaro() {
        assertThrows(BadRequestException.class, () -> service.register(registro("hola", "a@x.cl")));
    }

    @Test
    void registerEmailDuplicado() {
        when(repo.existsByEmailIgnoreCase("a@x.cl")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.register(registro("11111111-1", "a@x.cl")));
        verify(repo, never()).save(any());
    }

    @Test
    void registerRutDuplicado() {
        when(repo.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(repo.existsByRut("11111111-1")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.register(registro("11111111-1", "a@x.cl")));
    }

    // ---- refresh
    @Test
    void refreshOkEmiteParNuevo() {
        tokensStub();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("cliente@ferrefix.cl");
        when(jwt.validarRefreshToken("RT")).thenReturn(claims);
        when(repo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(usuario));

        TokenResponseDto r = service.refresh(new RefreshRequestDto("RT"));
        assertEquals("ACCESS", r.accessToken());
        assertEquals("REFRESH", r.refreshToken());
    }

    @Test
    void refreshTokenInvalidoOVencido() {
        when(jwt.validarRefreshToken("RT")).thenThrow(new JwtException("vencido"));
        assertThrows(BadCredentialsException.class, () -> service.refresh(new RefreshRequestDto("RT")));
    }

    @Test
    void refreshDeUsuarioDesactivadoSeRechaza() {
        usuario.setActivo(false);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("cliente@ferrefix.cl");
        when(jwt.validarRefreshToken("RT")).thenReturn(claims);
        when(repo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(usuario));
        assertThrows(BadCredentialsException.class, () -> service.refresh(new RefreshRequestDto("RT")));
    }

    // ---- me
    @Test
    void meDevuelveUsuarioSinHash() {
        when(repo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(usuario));
        UsuarioResponseDto dto = service.me("cliente@ferrefix.cl");
        assertEquals("cliente@ferrefix.cl", dto.email());
        assertFalse(dto.toString().contains("hash"));
    }

    @Test
    void meUsuarioInexistente() {
        when(repo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.me("x@x.cl"));
    }
}
