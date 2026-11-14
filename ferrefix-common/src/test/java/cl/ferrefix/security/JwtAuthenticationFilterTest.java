package cl.ferrefix.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * El filtro autentica SOLO con lo que trae el token: no hay UserDetailsService ni base de datos de por medio.
 * Pase lo que pase con el token, la petición sigue su curso (el 401 lo decide después la cadena de seguridad).
 */
class JwtAuthenticationFilterTest {

    private final JwtVerificador verificador = mock(JwtVerificador.class);
    private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(verificador);
    private final FilterChain cadena = mock(FilterChain.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest("GET", "/api/v1/ordenes/mis-ordenes");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void limpiarDespues() {
        SecurityContextHolder.clearContext();
    }

    private static io.jsonwebtoken.ClaimsBuilder claims() {
        return Jwts.claims().subject("cliente@ferrefix.cl")
                .add("uid", 3).add("rut", "15432890-4").add("nombre", "Cliente Demo")
                .add("roles", List.of("CLIENTE")).add("type", "access");
    }

    private Authentication autenticacion() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void conTokenValidoDejaLaIdentidadYLosRolesDelToken() throws Exception {
        when(verificador.validarAccessToken("TOKEN")).thenReturn(claims().build());
        request.addHeader("Authorization", "Bearer TOKEN");

        filtro.doFilter(request, response, cadena);

        Authentication auth = autenticacion();
        assertNotNull(auth);
        assertTrue(auth.isAuthenticated());
        UsuarioToken usuario = assertInstanceOf(UsuarioToken.class, auth.getPrincipal());
        assertEquals(3L, usuario.id());
        assertEquals("15432890-4", usuario.rut());
        assertEquals("Cliente Demo", usuario.nombre());
        assertEquals("cliente@ferrefix.cl", auth.getName());
        assertEquals(List.of("ROLE_CLIENTE"), auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        verify(cadena).doFilter(request, response);
    }

    @Test
    void sinCabeceraNoAutenticaYLaPeticionSigue() throws Exception {
        filtro.doFilter(request, response, cadena);
        assertNull(autenticacion());
        verifyNoInteractions(verificador);
        verify(cadena).doFilter(request, response);
    }

    @Test
    void unaCabeceraQueNoEsBearerSeIgnora() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        filtro.doFilter(request, response, cadena);
        assertNull(autenticacion());
        verifyNoInteractions(verificador);
        verify(cadena).doFilter(request, response);
    }

    @Test
    void tokenInvalidoOVencidoNoAutenticaNiLanzaExcepcion() throws Exception {
        when(verificador.validarAccessToken("BASURA")).thenThrow(new JwtException("mal formado"));
        when(verificador.validarAccessToken("VENCIDO")).thenThrow(new ExpiredJwtException(null, null, "JWT expired"));
        for (String token : new String[]{"BASURA", "VENCIDO"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/ordenes/mis-ordenes");
            req.addHeader("Authorization", "Bearer " + token);
            assertDoesNotThrow(() -> filtro.doFilter(req, response, cadena));
            assertNull(autenticacion(), token);
            verify(cadena).doFilter(req, response);
        }
    }

    @Test
    void unTokenSinRutONombreNoAutentica() throws Exception {
        // Token emitido antes de que existieran esos claims: se rechaza para que el cliente lo renueve.
        when(verificador.validarAccessToken("ANTIGUO")).thenReturn(claims().delete("rut").delete("nombre").build());
        request.addHeader("Authorization", "Bearer ANTIGUO");
        filtro.doFilter(request, response, cadena);
        assertNull(autenticacion());
        verify(cadena).doFilter(request, response);
    }

    @Test
    void siYaHayUnaAutenticacionNoLaPisaNiVerificaElToken() throws Exception {
        Authentication previa = new UsernamePasswordAuthenticationToken("otro", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(previa);
        request.addHeader("Authorization", "Bearer TOKEN");
        filtro.doFilter(request, response, cadena);
        assertSame(previa, autenticacion());
        verifyNoInteractions(verificador);
    }

    @Test
    void elTokenSeEntregaAlVerificadorSinElPrefijoBearer() throws Exception {
        when(verificador.validarAccessToken("abc.def.ghi")).thenReturn(claims().build());
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        filtro.doFilter(request, response, cadena);
        verify(verificador).validarAccessToken("abc.def.ghi");
    }
}
