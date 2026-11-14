package cl.ferrefix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lo que hace cada servicio con un token: verificar firma, vigencia y tipo SOLO con el secreto compartido.
 * Los tokens se arman aquí a mano (como lo haría ms-auth) para no depender de ningún otro módulo.
 */
class JwtVerificadorTest {

    private static final String SECRET = "clave-de-pruebas-ferrefix-con-mas-de-64-bytes-para-firmar-con-hs512-ok-1234";
    private static final String OTRO_SECRET = "otra-clave-distinta-ferrefix-de-mas-de-64-bytes-para-hs512-no-coincide-99";

    private final JwtVerificador verificador = new JwtVerificador(SECRET);

    private static String token(String secreto, String tipo, long duracionMs) {
        SecretKey clave = JwtVerificador.clave(secreto);
        long ahora = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject("ana@ferrefix.cl")
                .claim(JwtVerificador.CLAIM_UID, 7L)
                .claim(JwtVerificador.CLAIM_ROLES, List.of("CLIENTE"))
                .claim(JwtVerificador.CLAIM_RUT, "15432890-4")
                .claim(JwtVerificador.CLAIM_NOMBRE, "Ana Pérez")
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + duracionMs));
        if (tipo != null) {
            builder.claim(JwtVerificador.CLAIM_TYPE, tipo);
        }
        return builder.signWith(clave, Jwts.SIG.HS512).compact();
    }

    @Test
    void unAccessTokenValidoDevuelveSusClaims() {
        Claims c = verificador.validarAccessToken(token(SECRET, "access", 60_000));
        assertEquals("ana@ferrefix.cl", c.getSubject());
        assertEquals(7, c.get("uid", Integer.class));
        assertEquals("15432890-4", c.get("rut", String.class));
        assertEquals("Ana Pérez", c.get("nombre", String.class));
        assertEquals(List.of("CLIENTE"), c.get("roles", List.class));
    }

    @Test
    void unRefreshTokenValidoSoloSirveComoRefresh() {
        String refresh = token(SECRET, "refresh", 60_000);
        assertEquals("ana@ferrefix.cl", verificador.validarRefreshToken(refresh).getSubject());
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(refresh));
    }

    @Test
    void unAccessTokenNoSirveComoRefresh() {
        assertThrows(JwtException.class, () -> verificador.validarRefreshToken(token(SECRET, "access", 60_000)));
    }

    @Test
    void unTokenSinTipoSeRechaza() {
        String sinTipo = token(SECRET, null, 60_000);
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(sinTipo));
        assertThrows(JwtException.class, () -> verificador.validarRefreshToken(sinTipo));
    }

    @Test
    void tokenVencidoSeRechaza() {
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(token(SECRET, "access", -1_000)));
    }

    @Test
    void tokenFirmadoConOtraClaveSeRechaza() {
        // Es lo que impide que un tercero fabrique tokens: sin el secreto compartido la firma no coincide.
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(token(OTRO_SECRET, "access", 60_000)));
    }

    @Test
    void tokenAlteradoSeRechaza() {
        String token = token(SECRET, "access", 60_000);
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThrows(JwtException.class, () -> verificador.validarAccessToken(alterado));
    }

    @Test
    void basuraSeRechaza() {
        assertThrows(JwtException.class, () -> verificador.validarAccessToken("no.es.un.jwt"));
    }

    @Test
    void dosServiciosConElMismoSecretoAceptanElMismoToken() {
        // ms-auth emite y ms-tienda verifica: instancias distintas, mismo JWT_SECRET.
        String token = token(SECRET, "access", 60_000);
        assertEquals("ana@ferrefix.cl", new JwtVerificador(SECRET).validarAccessToken(token).getSubject());
        assertThrows(JwtException.class, () -> new JwtVerificador(OTRO_SECRET).validarAccessToken(token));
    }

    @Test
    void claveCortaImpideArrancar() {
        assertThrows(IllegalStateException.class, () -> new JwtVerificador("corta"));
        assertThrows(IllegalStateException.class, () -> new JwtVerificador(null));
    }

    @Test
    void secretoDe63BytesSeRechazaYDe64SeAcepta() {
        assertThrows(IllegalStateException.class, () -> new JwtVerificador("a".repeat(63)));
        assertDoesNotThrow(() -> new JwtVerificador("a".repeat(64)));
    }

    @Test
    void secretoSeMideEnBytesUtf8() {
        // 33 letras "ñ" = 33 caracteres pero 66 bytes: se acepta; 31 = 62 bytes: se rechaza
        assertDoesNotThrow(() -> new JwtVerificador("ñ".repeat(33)));
        assertThrows(IllegalStateException.class, () -> new JwtVerificador("ñ".repeat(31)));
    }

    @Test
    void mensajeDeErrorIndicaBytesYComoGenerarlo() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new JwtVerificador("corta"));
        assertTrue(e.getMessage().contains("64 bytes"));
        assertTrue(e.getMessage().contains("openssl rand -hex 64"));
    }
}
