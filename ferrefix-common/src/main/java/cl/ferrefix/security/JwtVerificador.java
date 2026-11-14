package cl.ferrefix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifica los JWT (HS512) que emite ms-auth. Cada servicio lo usa con el MISMO secreto (JWT_SECRET), así valida
 * la firma por su cuenta y no necesita llamar a ms-auth en cada petición.
 * Hay dos tipos de token, distinguidos por el claim "type":
 *  - access  (corto): lleva la identidad y los roles, y se envía en cada petición.
 *  - refresh (largo): solo sirve en /auth/refresh para pedir un par nuevo.
 */
@Component
public class JwtVerificador {

    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_RUT = "rut";
    public static final String CLAIM_NOMBRE = "nombre";
    public static final int MIN_SECRET_BYTES = 64;

    private final SecretKey key;

    public JwtVerificador(@Value("${ferrefix.jwt.secret}") String secret) {
        this.key = clave(secret);
    }

    /** Clave HMAC a partir del secreto (texto, no Base64). Falla si mide menos de 64 bytes: HS512 lo exige. */
    public static SecretKey clave(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe medir al menos " + MIN_SECRET_BYTES + " bytes (UTF-8). "
                            + "Generar con: openssl rand -hex 64 (produce 128 caracteres hexadecimales = 128 bytes)");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /** Verifica firma, expiración y que sea un token de acceso. Lanza JwtException si no. */
    public Claims validarAccessToken(String token) {
        return validar(token, TYPE_ACCESS);
    }

    /** Verifica firma, expiración y que sea un refresh token. Lanza JwtException si no. */
    public Claims validarRefreshToken(String token) {
        return validar(token, TYPE_REFRESH);
    }

    private Claims validar(String token, String tipoEsperado) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!tipoEsperado.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Tipo de token inválido");
        }
        return claims;
    }
}
