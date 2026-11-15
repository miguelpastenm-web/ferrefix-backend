package cl.ferrefix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.util.List;

/** Claims de un access token ya verificado, para los tests que usan el filtro JWT real con el verificador simulado. */
public final class TokensDePrueba {

    private TokensDePrueba() {
    }

    public static Claims claims(long id, String email, String rut, String nombre, String... roles) {
        return Jwts.claims().subject(email)
                .add(JwtVerificador.CLAIM_UID, id)
                .add(JwtVerificador.CLAIM_RUT, rut)
                .add(JwtVerificador.CLAIM_NOMBRE, nombre)
                .add(JwtVerificador.CLAIM_ROLES, List.of(roles))
                .add(JwtVerificador.CLAIM_TYPE, JwtVerificador.TYPE_ACCESS)
                .build();
    }

    public static Claims claimsDeCliente() {
        return claims(2L, "cliente@ferrefix.cl", "15432890-4", "Cliente Demo", "CLIENTE");
    }
}
