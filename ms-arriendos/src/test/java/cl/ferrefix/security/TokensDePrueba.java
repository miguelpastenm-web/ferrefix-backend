package cl.ferrefix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Identidades de prueba tal como las deja el filtro JWT: un UsuarioToken armado con los claims del access token.
 * En ms-tienda no hay tabla de usuarios; todo lo que se sabe del cliente es lo que trae su token.
 */
public final class TokensDePrueba {

    public static final UsuarioToken CLIENTE =
            new UsuarioToken(3L, "cliente@ferrefix.cl", "15432890-4", "Juan Maestro", List.of("CLIENTE"));
    public static final UsuarioToken OTRO_CLIENTE =
            new UsuarioToken(8L, "otro@ferrefix.cl", "12345678-5", "Otra Persona", List.of("CLIENTE"));
    public static final UsuarioToken ADMIN =
            new UsuarioToken(1L, "admin@ferrefix.cl", "11111111-1", "Administrador Ferrefix", List.of("ADMIN"));

    private TokensDePrueba() {
    }

    /** Petición ya autenticada con esa identidad (equivale a haber pasado el filtro JWT con un token válido). */
    public static RequestPostProcessor como(UsuarioToken usuario) {
        return authentication(new UsernamePasswordAuthenticationToken(usuario, null, usuario.authorities()));
    }

    /** Claims de un access token verificado, para los tests que ejercitan el filtro JWT real. */
    public static Claims claims(UsuarioToken usuario) {
        return Jwts.claims().subject(usuario.email())
                .add(JwtVerificador.CLAIM_UID, usuario.id())
                .add(JwtVerificador.CLAIM_RUT, usuario.rut())
                .add(JwtVerificador.CLAIM_NOMBRE, usuario.nombre())
                .add(JwtVerificador.CLAIM_ROLES, usuario.roles())
                .add(JwtVerificador.CLAIM_TYPE, JwtVerificador.TYPE_ACCESS)
                .build();
    }
}
