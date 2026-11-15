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

/**
 * Emisión de tokens (solo ms-auth). Se verifican con JwtVerificador, que es lo que usan los demás servicios:
 * así se prueba el recorrido real "ms-auth emite, otro servicio verifica con el mismo secreto".
 */
class JwtServiceTest {

    private static final String SECRET = "clave-de-pruebas-ferrefix-con-mas-de-64-bytes-para-firmar-con-hs512-ok-1234";
    private final JwtVerificador verificador = new JwtVerificador(SECRET);
    private JwtService jwt;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(SECRET, 60_000, 3_600_000);
        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setRut("15432890-4");
        usuario.setNombre("Ana Pérez");
        usuario.setEmail("ana@ferrefix.cl");
        usuario.setRoles(Set.of(Rol.CLIENTE, Rol.ADMIN));
    }

    @Test
    void accessTokenLlevaSujetoRolesYTipo() {
        Claims c = verificador.validarAccessToken(jwt.generarAccessToken(usuario));
        assertEquals("ana@ferrefix.cl", c.getSubject());
        assertEquals(7, c.get("uid", Integer.class));
        assertEquals("access", c.get("type", String.class));
        assertEquals(List.of("ADMIN", "CLIENTE"), c.get("roles", List.class));
    }

    @Test
    void accessTokenLlevaRutYNombreParaLosDemasServicios() {
        // Con estos claims ms-tienda arma la orden o el arriendo sin consultar la base de usuarios.
        Claims c = verificador.validarAccessToken(jwt.generarAccessToken(usuario));
        assertEquals("15432890-4", c.get("rut", String.class));
        assertEquals("Ana Pérez", c.get("nombre", String.class));
    }

    @Test
    void elAccessTokenAlcanzaParaArmarLaIdentidadCompleta() {
        UsuarioToken u = UsuarioToken.desde(verificador.validarAccessToken(jwt.generarAccessToken(usuario)));
        assertEquals(new UsuarioToken(7L, "ana@ferrefix.cl", "15432890-4", "Ana Pérez", List.of("ADMIN", "CLIENTE")), u);
    }

    @Test
    void elAccessTokenNoLlevaDatosSensibles() {
        usuario.setPasswordHash("$2a$10$hash-secreto");
        usuario.setTelefono("+56 9 1234 5678");
        usuario.setDireccion("Calle 1");
        Claims c = verificador.validarAccessToken(jwt.generarAccessToken(usuario));
        assertEquals(Set.of("sub", "uid", "type", "roles", "rut", "nombre", "iat", "exp"), c.keySet());
    }

    @Test
    void refreshTokenNoLlevaRoles() {
        Claims c = verificador.validarRefreshToken(jwt.generarRefreshToken(usuario));
        assertEquals("refresh", c.get("type", String.class));
        assertNull(c.get("roles"));
        // Tampoco rut ni nombre: al renovar, todo se vuelve a leer de la base.
        assertNull(c.get("rut"));
        assertNull(c.get("nombre"));
    }

    @Test
    void refreshTokenNoSirveComoAccess() {
        String refresh = jwt.generarRefreshToken(usuario);
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(refresh));
    }

    @Test
    void accessTokenNoSirveComoRefresh() {
        String access = jwt.generarAccessToken(usuario);
        assertThrows(JwtException.class, () -> verificador.validarRefreshToken(access));
    }

    @Test
    void tokenVencidoSeRechaza() {
        JwtService corto = new JwtService(SECRET, -1_000, -1_000);
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(corto.generarAccessToken(usuario)));
    }

    @Test
    void tokenFirmadoConOtraClaveSeRechaza() {
        JwtService otro = new JwtService("otra-clave-distinta-ferrefix-de-mas-de-64-bytes-para-hs512-no-coincide-99", 60_000, 60_000);
        String token = otro.generarAccessToken(usuario);
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(token));
    }

    @Test
    void tokenAlteradoSeRechaza() {
        String token = jwt.generarAccessToken(usuario);
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(alterado));
    }

    @Test
    void claveCortaImpideArrancar() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corta", 1, 1));
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 1, 1));
    }

    @Test
    void secretoDe63BytesSeRechazaYDe64SeAcepta() {
        assertThrows(IllegalStateException.class, () -> new JwtService("a".repeat(63), 1, 1));
        assertDoesNotThrow(() -> new JwtService("a".repeat(64), 1, 1));
    }

    @Test
    void secretoSeMideEnBytesUtf8() {
        // 33 letras "ñ" = 33 caracteres pero 66 bytes: se acepta
        assertDoesNotThrow(() -> new JwtService("ñ".repeat(33), 1, 1));
        // 32 letras "ñ" = 64 bytes exactos: se acepta; 31 = 62 bytes: se rechaza
        assertThrows(IllegalStateException.class, () -> new JwtService("ñ".repeat(31), 1, 1));
    }

    @Test
    void mensajeDeErrorIndicaBytesYComoGenerarlo() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new JwtService("corta", 1, 1));
        assertTrue(e.getMessage().contains("64 bytes"));
        assertTrue(e.getMessage().contains("openssl rand -hex 64"));
    }

    @Test
    void tokenSeFirmaConHS512() throws Exception {
        String header = new String(java.util.Base64.getUrlDecoder().decode(jwt.generarAccessToken(usuario).split("\\.")[0]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(header.contains("\"alg\":\"HS512\""), header);
    }

    @Test
    void expiresInEnSegundos() {
        assertEquals(60, jwt.getAccessExpirationSeconds());
    }
}
