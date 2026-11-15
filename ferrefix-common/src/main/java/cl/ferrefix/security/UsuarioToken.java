package cl.ferrefix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;

/**
 * Identidad del usuario tal como viene en el access token. Es el "principal" de la petición autenticada: los
 * servicios toman de aquí el id, el RUT, el nombre y los roles, sin consultar la base de usuarios (que es de ms-auth).
 * Los datos son los vigentes al emitirse el token: un cambio de nombre o de rol se ve cuando el token se renueva.
 */
public record UsuarioToken(Long id, String email, String rut, String nombre, List<String> roles) implements Principal {

    public UsuarioToken {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** Arma la identidad con los claims de un access token ya verificado. Lanza JwtException si falta alguno. */
    public static UsuarioToken desde(Claims claims) {
        Object uid = claims.get(JwtVerificador.CLAIM_UID);
        String email = claims.getSubject();
        String rut = claims.get(JwtVerificador.CLAIM_RUT, String.class);
        String nombre = claims.get(JwtVerificador.CLAIM_NOMBRE, String.class);
        if (!(uid instanceof Number) || esVacio(email) || esVacio(rut) || esVacio(nombre)) {
            // Un token sin estos datos no sirve para operar: se trata como inválido y el cliente debe renovarlo.
            throw new JwtException("El token no trae la identidad completa del usuario");
        }
        List<String> roles = claims.get(JwtVerificador.CLAIM_ROLES) instanceof List<?> lista
                ? lista.stream().map(String::valueOf).toList()
                : List.of();
        return new UsuarioToken(((Number) uid).longValue(), email, rut, nombre, roles);
    }

    /** El "nombre" de Spring Security (Authentication.getName()) es el email. */
    @Override
    public String getName() {
        return email;
    }

    public boolean esAdmin() {
        return roles.contains("ADMIN");
    }

    /** Roles con el prefijo que esperan hasRole(...) y @PreAuthorize. */
    public List<GrantedAuthority> authorities() {
        return roles.stream().<GrantedAuthority>map(rol -> new SimpleGrantedAuthority("ROLE_" + rol)).toList();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
