package cl.ferrefix;

import cl.ferrefix.model.Cotizacion;
import cl.ferrefix.repository.ArriendoRepository;
import cl.ferrefix.repository.CotizacionRepository;
import cl.ferrefix.repository.MaquinaRepository;
import cl.ferrefix.repository.MaquinaUnidadRepository;
import cl.ferrefix.security.JwtVerificador;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.ZoneId;
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
 * ms-arriendos los valida por su cuenta, sin llamar a ms-auth y sin tabla de usuarios.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "ferrefix.jwt.secret=" + ServicioArriendosIntegracionTest.SECRET})
@AutoConfigureMockMvc
class ServicioArriendosIntegracionTest {

    static final String SECRET = "clave-de-pruebas-ferrefix-con-mas-de-64-bytes-para-firmar-con-hs512-ok-1234";
    static final String OTRO_SECRET = "otra-clave-distinta-ferrefix-de-mas-de-64-bytes-para-hs512-no-coincide-99";

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext contexto;
    @Autowired ObjectMapper json;
    @MockitoBean ArriendoRepository arriendoRepo;
    @MockitoBean MaquinaRepository maquinaRepo;
    @MockitoBean MaquinaUnidadRepository unidadRepo;
    @MockitoBean CotizacionRepository cotizacionRepo;
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

    private static final String COTIZACION = """
            {"nombre":"Constructora Los Andes SpA","rut":"11.111.111-1","email":"ventas@los-andes.cl",
             "telefono":"+56 9 8765 4321","tipoProyecto":"Arriendo de Maquinaria y Demoledores","region":"Región Metropolitana",
             "detalle":"Necesito arrendar un martillo demoledor por dos semanas","despachoObra":true}""";

    @Test
    void esteServicioNoTieneUsuariosNiEmiteTokens() {
        assertTrue(contexto.getBeansOfType(UserDetailsService.class).isEmpty(), "No hay usuario en memoria ni tabla de usuarios");
        assertTrue(contexto.getBeansOfType(PasswordEncoder.class).isEmpty(), "Las contraseñas son asunto de ms-auth");
        assertFalse(contexto.containsBean("jwtService"), "Los tokens los emite solo ms-auth");
        assertEquals(1, contexto.getBeansOfType(JwtVerificador.class).size());
    }

    @Test
    void elRelojDeNegocioVieneDeFerrefixCommonYEstaEnHoraDeChile() {
        assertEquals(ZoneId.of("America/Santiago"), contexto.getBean(Clock.class).getZone());
    }

    @Test
    void unTokenFirmadoConElSecretoCompartidoAutenticaSinLlamarANadie() throws Exception {
        when(arriendoRepo.findByUsuarioIdOrderByCreatedAtDescIdDesc(3L)).thenReturn(List.of());

        mvc.perform(get("/api/v1/arriendos/mis-arriendos").header("Authorization", bearer(DE_CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(arriendoRepo).findByUsuarioIdOrderByCreatedAtDescIdDesc(3L);   // el id salió del claim "uid" del token
    }

    @SuppressWarnings("unchecked")
    @Test
    void losRolesDelTokenDecidenElAcceso() throws Exception {
        when(arriendoRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        mvc.perform(get("/api/v1/arriendos").header("Authorization", bearer(DE_CLIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/arriendos").header("Authorization", bearer(DE_ADMIN)))
                .andExpect(status().isOk());
        // Un ADMIN no arrienda: POST /arriendos es solo para CLIENTE.
        mvc.perform(post("/api/v1/arriendos").header("Authorization", bearer(DE_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // El detalle de unidades es solo para ADMIN.
        mvc.perform(get("/api/v1/maquinas/5/unidades").header("Authorization", bearer(DE_CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinTokenOConUnTokenQueNoSirveLasRutasProtegidasDan401() throws Exception {
        String ajeno = token(OTRO_SECRET, "access", 3, "cliente@ferrefix.cl", true, "ADMIN");     // firmado por un tercero
        String refresh = token(SECRET, "refresh", 3, "cliente@ferrefix.cl", true, "CLIENTE");     // no es de acceso
        String incompleto = token(SECRET, "access", 3, "cliente@ferrefix.cl", false, "CLIENTE");  // sin rut ni nombre
        String alterado = DE_CLIENTE.substring(0, DE_CLIENTE.length() - 2) + (DE_CLIENTE.endsWith("AA") ? "BB" : "AA");

        mvc.perform(get("/api/v1/arriendos/mis-arriendos"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        for (String token : new String[]{ajeno, refresh, incompleto, alterado, "no.es.un.jwt"}) {
            mvc.perform(get("/api/v1/arriendos/mis-arriendos").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(arriendoRepo);
    }

    @SuppressWarnings("unchecked")
    @Test
    void elCatalogoDeMaquinasEsPublico() throws Exception {
        when(maquinaRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        mvc.perform(get("/api/v1/maquinas")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void cualquieraPuedePedirUnaCotizacionYConTokenSeAsociaAlUsuario() throws Exception {
        when(cotizacionRepo.save(any(Cotizacion.class))).thenAnswer(i -> {
            Cotizacion c = i.getArgument(0);
            c.setId(88L);
            return c;
        });

        mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content(COTIZACION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("COT-000088"));
        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", bearer(DE_CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(COTIZACION))
                .andExpect(status().isCreated());

        ArgumentCaptor<Cotizacion> guardadas = ArgumentCaptor.forClass(Cotizacion.class);
        verify(cotizacionRepo, times(2)).save(guardadas.capture());
        assertNull(guardadas.getAllValues().get(0).getUsuarioId(), "Sin token queda sin usuario");
        assertEquals(3L, guardadas.getAllValues().get(1).getUsuarioId(), "Con token se guarda el uid del token");
    }

    @Test
    void laListaDeCotizacionesEsSoloParaAdmin() throws Exception {
        mvc.perform(get("/api/v1/cotizaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cotizaciones").header("Authorization", bearer(DE_CLIENTE))).andExpect(status().isForbidden());
    }

    @Test
    void lasRutasDeLaTiendaYDeAutenticacionNoSonDeEsteServicio() throws Exception {
        mvc.perform(get("/api/v1/productos")).andExpect(status().isUnauthorized());
        for (String ruta : new String[]{"/api/v1/productos", "/api/v1/ordenes/mis-ordenes", "/api/v1/auth/me", "/media/productos/x.webp"}) {
            mvc.perform(get(ruta).header("Authorization", bearer(DE_ADMIN))).andExpect(status().isNotFound());
        }
    }

    @Test
    void elDocumentoOpenApiSoloDescribeMaquinasArriendosYCotizaciones() throws Exception {
        JsonNode rutas = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("paths");
        for (String ruta : new String[]{"/api/v1/maquinas", "/api/v1/arriendos", "/api/v1/cotizaciones"}) {
            assertTrue(rutas.has(ruta), "Falta en el documento: " + ruta);
        }
        int total = 0;
        for (String ruta : rutas.propertyNames()) {
            total++;
            assertTrue(ruta.startsWith("/api/v1/maquinas") || ruta.startsWith("/api/v1/arriendos") || ruta.startsWith("/api/v1/cotizaciones"),
                    "Esta ruta pertenece a otro servicio: " + ruta);
        }
        assertTrue(total >= 10, "Se esperaban las rutas de los 3 controllers: " + total);
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
