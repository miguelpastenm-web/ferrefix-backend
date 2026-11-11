package cl.ferrefix.security;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "clave-de-pruebas-con-mas-de-32-caracteres-ok";
    private JwtService jwt;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRET, 60_000, 3_600_000);
        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setEmail("ana@ferrefix.cl");
        usuario.setRoles(Set.of(Rol.CLIENTE, Rol.ADMIN));
    }

    @Test
    void accessTokenLlevaSujetoRolesYTipo() {
        Claims c = jwt.validarAccessToken(jwt.generarAccessToken(usuario));
        assertEquals("ana@ferrefix.cl", c.getSubject());
        assertEquals(7, c.get("uid", Integer.class));
        assertEquals("access", c.get("type", String.class));
        assertEquals(List.of("ADMIN", "CLIENTE"), c.get("roles", List.class));
    }

    @Test
    void refreshTokenNoLlevaRoles() {
        Claims c = jwt.validarRefreshToken(jwt.generarRefreshToken(usuario));
        assertEquals("refresh", c.get("type", String.class));
        assertNull(c.get("roles"));
    }

    @Test
    void refreshTokenNoSirveComoAccess() {
        String refresh = jwt.generarRefreshToken(usuario);
        assertThrows(JwtException.class, () -> jwt.validarAccessToken(refresh));
    }

    @Test
    void accessTokenNoSirveComoRefresh() {
        String access = jwt.generarAccessToken(usuario);
        assertThrows(JwtException.class, () -> jwt.validarRefreshToken(access));
    }

    @Test
    void tokenVencidoSeRechaza() {
        JwtService corto = new JwtService(SECRET, -1_000, -1_000);
        assertThrows(JwtException.class, () -> corto.validarAccessToken(corto.generarAccessToken(usuario)));
    }

    @Test
    void tokenFirmadoConOtraClaveSeRechaza() {
        JwtService otro = new JwtService("otra-clave-distinta-de-mas-de-32-caracteres!!", 60_000, 60_000);
        String token = otro.generarAccessToken(usuario);
        assertThrows(JwtException.class, () -> jwt.validarAccessToken(token));
    }

    @Test
    void tokenAlteradoSeRechaza() {
        String token = jwt.generarAccessToken(usuario);
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThrows(JwtException.class, () -> jwt.validarAccessToken(alterado));
    }

    @Test
    void basuraSeRechaza() {
        assertThrows(JwtException.class, () -> jwt.validarAccessToken("no.es.un.jwt"));
    }

    @Test
    void claveCortaImpideArrancar() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corta", 1, 1));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 1, 1));
    }

    @Test
    void expiresInEnSegundos() {
        assertEquals(60, jwt.getAccessExpirationSeconds());
    }
}
