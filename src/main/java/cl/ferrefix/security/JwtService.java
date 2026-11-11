package cl.ferrefix.security;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Genera y verifica JWT firmados con HS256.
 * Hay dos tipos de token, distinguidos por el claim "type":
 *  - access  (corto): lleva roles y se envía en cada petición.
 *  - refresh (largo): solo sirve en /auth/refresh para pedir un par nuevo.
 */
@Service
public class JwtService {

    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(@Value("${ferrefix.jwt.secret}") String secret,
                      @Value("${ferrefix.jwt.access-expiration-ms}") long accessExpirationMs,
                      @Value("${ferrefix.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos " + MIN_SECRET_BYTES + " caracteres. Generar con: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generarAccessToken(Usuario usuario) {
        List<String> roles = usuario.getRoles().stream().map(Rol::name).sorted().toList();
        return construir(usuario, TYPE_ACCESS, accessExpirationMs)
                .claim("roles", roles)
                .compact();
    }

    public String generarRefreshToken(Usuario usuario) {
        return construir(usuario, TYPE_REFRESH, refreshExpirationMs).compact();
    }

    /** Verifica firma, expiración y que sea un token de acceso. Lanza JwtException si no. */
    public Claims validarAccessToken(String token) {
        return validar(token, TYPE_ACCESS);
    }

    /** Verifica firma, expiración y que sea un refresh token. Lanza JwtException si no. */
    public Claims validarRefreshToken(String token) {
        return validar(token, TYPE_REFRESH);
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    private io.jsonwebtoken.JwtBuilder construir(Usuario usuario, String tipo, long duracionMs) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("uid", usuario.getId())
                .claim(CLAIM_TYPE, tipo)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + duracionMs))
                .signWith(key);
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
