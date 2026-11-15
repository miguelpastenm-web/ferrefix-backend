package cl.ferrefix.security;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * Emite los JWT (HS512). Solo existe en ms-auth: los demás servicios no generan tokens, únicamente los verifican
 * con JwtVerificador y el mismo secreto.
 *  - access  (corto): lleva la identidad completa (uid, email, RUT, nombre y roles) para que los otros servicios
 *    no tengan que consultar la base de usuarios.
 *  - refresh (largo): solo identifica al usuario; al renovarse, los datos y los roles se leen de nuevo de la base.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(@Value("${ferrefix.jwt.secret}") String secret,
                      @Value("${ferrefix.jwt.access-expiration-ms}") long accessExpirationMs,
                      @Value("${ferrefix.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = JwtVerificador.clave(secret);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generarAccessToken(Usuario usuario) {
        List<String> roles = usuario.getRoles().stream().map(Rol::name).sorted().toList();
        return construir(usuario, JwtVerificador.TYPE_ACCESS, accessExpirationMs)
                .claim(JwtVerificador.CLAIM_ROLES, roles)
                .claim(JwtVerificador.CLAIM_RUT, usuario.getRut())
                .claim(JwtVerificador.CLAIM_NOMBRE, usuario.getNombre())
                .compact();
    }

    public String generarRefreshToken(Usuario usuario) {
        return construir(usuario, JwtVerificador.TYPE_REFRESH, refreshExpirationMs).compact();
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    private io.jsonwebtoken.JwtBuilder construir(Usuario usuario, String tipo, long duracionMs) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(JwtVerificador.CLAIM_UID, usuario.getId())
                .claim(JwtVerificador.CLAIM_TYPE, tipo)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + duracionMs))
                .signWith(key, Jwts.SIG.HS512);
    }
}
