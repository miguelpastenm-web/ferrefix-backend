package cl.ferrefix;

import cl.ferrefix.model.Orden;
import cl.ferrefix.repository.OrdenRepository;
import cl.ferrefix.repository.ProductoRepository;
import cl.ferrefix.security.JwtVerificador;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arranca el servicio COMPLETO (configuración, seguridad, filtro JWT, controllers y services reales) sin Oracle:
 * solo los repositorios están simulados. Los tokens se firman aquí como lo haría ms-auth, con el secreto compartido:
 * ms-tienda los valida por su cuenta, sin llamar a ms-auth y sin tabla de usuarios.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "ferrefix.jwt.secret=" + ServicioTiendaIntegracionTest.SECRET})
@AutoConfigureMockMvc
class ServicioTiendaIntegracionTest {

    static final String SECRET = "clave-de-pruebas-ferrefix-con-mas-de-64-bytes-para-firmar-con-hs512-ok-1234";
    static final String OTRO_SECRET = "otra-clave-distinta-ferrefix-de-mas-de-64-bytes-para-hs512-no-coincide-99";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws IOException {
        String uploads = Files.createTempDirectory("ferrefix-uploads-integracion").toString();
        registro.add("ferrefix.upload.dir", () -> uploads);
    }

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext contexto;
    @Autowired ObjectMapper json;
    @MockitoBean ProductoRepository productoRepo;
    @MockitoBean OrdenRepository ordenRepo;
    @MockitoBean PlatformTransactionManager transacciones;

    /** Access token como los que emite ms-auth: mismos claims, firmado con HS512. */
    private static String token(String secreto, String tipo, long id, String email, boolean conIdentidad, String... roles) {
        var builder = Jwts.builder().subject(email)
                .claim("uid", id).claim("type", tipo).claim("roles", List.of(roles))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000));
        if (conIdentidad) {
            builder.claim("rut", "15432890-4").claim("nombre", "Juan Maestro");
        }
        return builder.signWith(JwtVerificador.clave(secreto), Jwts.SIG.HS512).compact();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static final String DE_CLIENTE = token(SECRET, "access", 3, "cliente@ferrefix.cl", true, "CLIENTE");
    private static final String DE_ADMIN = token(SECRET, "access", 1, "admin@ferrefix.cl", true, "ADMIN");

    @Test
    void esteServicioNoTieneUsuariosNiEmiteTokens() {
        assertTrue(contexto.getBeansOfType(UserDetailsService.class).isEmpty(), "No hay usuario en memoria ni tabla de usuarios");
        assertTrue(contexto.getBeansOfType(PasswordEncoder.class).isEmpty(), "Las contraseñas son asunto de ms-auth");
        assertFalse(contexto.containsBean("jwtService"), "Los tokens los emite solo ms-auth");
        assertEquals(1, contexto.getBeansOfType(JwtVerificador.class).size());
    }

    @Test
    void unTokenFirmadoConElSecretoCompartidoAutenticaSinLlamarANadie() throws Exception {
        when(ordenRepo.findByUsuarioIdOrderByCreatedAtDescIdDesc(3L)).thenReturn(List.<Orden>of());

        mvc.perform(get("/api/v1/ordenes/mis-ordenes").header("Authorization", bearer(DE_CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(ordenRepo).findByUsuarioIdOrderByCreatedAtDescIdDesc(3L);   // el id salió del claim "uid" del token
    }

    @SuppressWarnings("unchecked")
    @Test
    void losRolesDelTokenDecidenElAcceso() throws Exception {
        when(ordenRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        mvc.perform(get("/api/v1/ordenes").header("Authorization", bearer(DE_CLIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/ordenes").header("Authorization", bearer(DE_ADMIN)))
                .andExpect(status().isOk());
        // Un ADMIN no compra: POST /ordenes es solo para CLIENTE.
        mvc.perform(post("/api/v1/ordenes").header("Authorization", bearer(DE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenOConUnTokenQueNoSirveLasRutasProtegidasDan401() throws Exception {
        String ajeno = token(OTRO_SECRET, "access", 3, "cliente@ferrefix.cl", true, "ADMIN");     // firmado por un tercero
        String refresh = token(SECRET, "refresh", 3, "cliente@ferrefix.cl", true, "CLIENTE");     // no es de acceso
        String incompleto = token(SECRET, "access", 3, "cliente@ferrefix.cl", false, "CLIENTE");  // sin rut ni nombre
        String alterado = DE_CLIENTE.substring(0, DE_CLIENTE.length() - 2) + (DE_CLIENTE.endsWith("AA") ? "BB" : "AA");

        mvc.perform(get("/api/v1/ordenes/mis-ordenes"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        for (String token : new String[]{ajeno, refresh, incompleto, alterado, "no.es.un.jwt"}) {
            mvc.perform(get("/api/v1/ordenes/mis-ordenes").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(ordenRepo);
    }

    @SuppressWarnings("unchecked")
    @Test
    void elCatalogoSigueSiendoPublico() throws Exception {
        when(productoRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        mvc.perform(get("/api/v1/productos")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void lasRutasDeAutenticacionYaNoSonDeEsteServicio() throws Exception {
        // login, register y refresh viven en ms-auth: aquí no son públicas ni existen.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cliente@ferrefix.cl\",\"password\":\"clave1234\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(DE_CLIENTE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void elDocumentoOpenApiSoloDescribeProductosOrdenesEImagenes() throws Exception {
        JsonNode rutas = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("paths");
        for (String ruta : new String[]{"/api/v1/productos", "/api/v1/ordenes", "/api/v1/imagenes"}) {
            assertTrue(rutas.has(ruta), "Falta en el documento: " + ruta);
        }
        rutas.propertyNames().forEach(ruta -> assertFalse(
                ruta.startsWith("/api/v1/auth") || ruta.startsWith("/api/v1/users") || ruta.startsWith("/api/v1/maquinas")
                        || ruta.startsWith("/api/v1/arriendos") || ruta.startsWith("/api/v1/cotizaciones"),
                "Esta ruta pertenece a otro servicio: " + ruta));
    }

    @Test
    void lasRutasDeArriendosCotizacionesYMaquinasYaNoSonDeEsteServicio() throws Exception {
        // Viven en ms-arriendos: aquí no existen (ni siquiera la lectura pública de máquinas ni crear cotizaciones).
        mvc.perform(get("/api/v1/maquinas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        for (String ruta : new String[]{"/api/v1/maquinas", "/api/v1/arriendos/mis-arriendos", "/api/v1/cotizaciones"}) {
            mvc.perform(get(ruta).header("Authorization", bearer(DE_ADMIN))).andExpect(status().isNotFound());
        }
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
