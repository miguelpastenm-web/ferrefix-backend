package cl.ferrefix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** La identidad de la petición se arma solo con los claims del access token (sin base de datos). */
class UsuarioTokenTest {

    private static io.jsonwebtoken.ClaimsBuilder completos() {
        return Jwts.claims().subject("ana@ferrefix.cl")
                .add("uid", 7)
                .add("rut", "15432890-4")
                .add("nombre", "Ana Pérez")
                .add("roles", List.of("ADMIN", "CLIENTE"))
                .add("type", "access");
    }

    @Test
    void tomaIdEmailRutNombreYRolesDeLosClaims() {
        UsuarioToken u = UsuarioToken.desde(completos().build());
        assertEquals(7L, u.id());
        assertEquals("ana@ferrefix.cl", u.email());
        assertEquals("15432890-4", u.rut());
        assertEquals("Ana Pérez", u.nombre());
        assertEquals(List.of("ADMIN", "CLIENTE"), u.roles());
    }

    @Test
    void elIdPuedeVenirComoEnteroOComoLargo() {
        // JSON no distingue Integer de Long: un id grande llega como Long y uno chico como Integer.
        assertEquals(7L, UsuarioToken.desde(completos().add("uid", 7).build()).id());
        assertEquals(5_000_000_000L, UsuarioToken.desde(completos().add("uid", 5_000_000_000L).build()).id());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uid", "rut", "nombre"})
    void siFaltaUnDatoDeIdentidadElTokenSeTrataComoInvalido(String claim) {
        // Un token antiguo (sin rut ni nombre) no sirve para comprar o arrendar: 401 y el cliente lo renueva.
        Claims incompletos = completos().delete(claim).build();
        assertThrows(JwtException.class, () -> UsuarioToken.desde(incompletos));
    }

    @Test
    void sinSujetoOConDatosEnBlancoTambienEsInvalido() {
        assertThrows(JwtException.class, () -> UsuarioToken.desde(completos().subject(null).build()));
        assertThrows(JwtException.class, () -> UsuarioToken.desde(completos().add("rut", "  ").build()));
        assertThrows(JwtException.class, () -> UsuarioToken.desde(completos().add("nombre", "").build()));
        assertThrows(JwtException.class, () -> UsuarioToken.desde(completos().add("uid", "siete").build()));
    }

    @Test
    void sinRolesQuedaAutenticadoPeroSinPermisos() {
        UsuarioToken u = UsuarioToken.desde(completos().delete("roles").build());
        assertTrue(u.roles().isEmpty());
        assertTrue(u.authorities().isEmpty());
        assertFalse(u.esAdmin());
    }

    @Test
    void losRolesSeExponenConElPrefijoQueUsaSpringSecurity() {
        UsuarioToken u = UsuarioToken.desde(completos().build());
        assertEquals(List.of("ROLE_ADMIN", "ROLE_CLIENTE"), u.authorities().stream().map(GrantedAuthority::getAuthority).toList());
        assertTrue(u.esAdmin());
        assertFalse(new UsuarioToken(3L, "c@x.cl", "1-9", "C", List.of("CLIENTE")).esAdmin());
    }

    @Test
    void elNombreDeLaAutenticacionEsElEmail() {
        UsuarioToken u = UsuarioToken.desde(completos().build());
        assertEquals("ana@ferrefix.cl", u.getName());
        // Authentication.getName() delega en el principal: los controllers que usan auth.getName() reciben el email.
        assertEquals("ana@ferrefix.cl", new UsernamePasswordAuthenticationToken(u, null, u.authorities()).getName());
    }

    @Test
    void laListaDeRolesEsInmutableYNuncaNull() {
        UsuarioToken u = new UsuarioToken(1L, "a@x.cl", "1-9", "A", null);
        assertTrue(u.roles().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> u.roles().add("ADMIN"));
    }
}
