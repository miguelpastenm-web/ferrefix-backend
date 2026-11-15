package cl.ferrefix.service;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.RefreshRequestDto;
import cl.ferrefix.model.dto.TokenResponseDto;
import cl.ferrefix.model.mapper.UsuarioMapper;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.security.JwtService;
import cl.ferrefix.security.JwtVerificador;
import cl.ferrefix.security.UsuarioToken;
import cl.ferrefix.service.impl.AuthServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Renovación con tokens REALES (emisor y verificador de verdad; solo la base está simulada).
 * Los demás servicios confían en los claims del access token, así que /auth/refresh es el punto donde los datos se
 * ponen al día: nombre, RUT y ROLES se leen de la base en ese momento, nunca del token anterior.
 */
class RenovacionDeTokenTest {

    private static final String SECRET = "clave-de-pruebas-ferrefix-con-mas-de-64-bytes-para-firmar-con-hs512-ok-1234";

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final JwtService jwt = new JwtService(SECRET, 900_000, 604_800_000);
    private final JwtVerificador verificador = new JwtVerificador(SECRET);
    private final AuthServiceImpl service =
            new AuthServiceImpl(repo, mock(PasswordEncoder.class), jwt, verificador, new UsuarioMapper());

    private Usuario enLaBase;
    private String refreshAntiguo;

    @BeforeEach
    void setUp() {
        Usuario alIniciarSesion = usuario("Cliente Demo", EnumSet.of(Rol.CLIENTE));
        refreshAntiguo = jwt.generarRefreshToken(alIniciarSesion);   // emitido cuando solo era CLIENTE
        enLaBase = usuario("Cliente Demo", EnumSet.of(Rol.CLIENTE));
        when(repo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenAnswer(i -> Optional.of(enLaBase));
    }

    private static Usuario usuario(String nombre, EnumSet<Rol> roles) {
        Usuario u = new Usuario();
        u.setId(2L);
        u.setRut("15432890-4");
        u.setNombre(nombre);
        u.setEmail("cliente@ferrefix.cl");
        u.setPasswordHash("$2a$hash");
        u.setActivo(true);
        u.setRoles(roles);
        return u;
    }

    private UsuarioToken identidadDelNuevoAccessToken() {
        TokenResponseDto nuevos = service.refresh(new RefreshRequestDto(refreshAntiguo));
        return UsuarioToken.desde(verificador.validarAccessToken(nuevos.accessToken()));
    }

    @Test
    void unRolAgregadoEnLaBaseApareceEnElAccessTokenRenovado() {
        enLaBase.setRoles(EnumSet.of(Rol.CLIENTE, Rol.ADMIN));
        assertEquals(List.of("ADMIN", "CLIENTE"), identidadDelNuevoAccessToken().roles());
    }

    @Test
    void unRolQuitadoEnLaBaseDesapareceDelAccessTokenRenovado() {
        Usuario eraAdmin = usuario("Cliente Demo", EnumSet.of(Rol.CLIENTE, Rol.ADMIN));
        refreshAntiguo = jwt.generarRefreshToken(eraAdmin);
        enLaBase.setRoles(EnumSet.of(Rol.CLIENTE));                  // se le quitó ADMIN después de iniciar sesión

        UsuarioToken renovado = identidadDelNuevoAccessToken();
        assertEquals(List.of("CLIENTE"), renovado.roles());
        assertFalse(renovado.esAdmin());
    }

    @Test
    void elNombreEditadoEnElPerfilApareceEnElAccessTokenRenovado() {
        enLaBase.setNombre("Juan Maestro");
        UsuarioToken renovado = identidadDelNuevoAccessToken();
        assertEquals("Juan Maestro", renovado.nombre());
        assertEquals("15432890-4", renovado.rut());
        assertEquals(2L, renovado.id());
    }

    @Test
    void laRespuestaDelRefreshTambienTraeLosRolesVigentes() {
        enLaBase.setRoles(EnumSet.of(Rol.CLIENTE, Rol.ADMIN));
        TokenResponseDto nuevos = service.refresh(new RefreshRequestDto(refreshAntiguo));
        assertEquals(List.of("ADMIN", "CLIENTE"), nuevos.usuario().roles());
    }

    @Test
    void elRefreshTokenRenovadoSigueSinLlevarRoles() {
        enLaBase.setRoles(EnumSet.of(Rol.CLIENTE, Rol.ADMIN));
        TokenResponseDto nuevos = service.refresh(new RefreshRequestDto(refreshAntiguo));
        Claims refresh = verificador.validarRefreshToken(nuevos.refreshToken());
        assertNull(refresh.get("roles"));
        assertEquals("cliente@ferrefix.cl", refresh.getSubject());
    }

    @Test
    void siLaCuentaSeDesactivoElRefreshSeRechaza() {
        enLaBase.setActivo(false);
        assertThrows(BadCredentialsException.class, () -> service.refresh(new RefreshRequestDto(refreshAntiguo)));
    }

    @Test
    void unAccessTokenNoSirveParaRenovar() {
        String access = jwt.generarAccessToken(enLaBase);
        assertThrows(BadCredentialsException.class, () -> service.refresh(new RefreshRequestDto(access)));
    }
}
