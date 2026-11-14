package cl.ferrefix;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.security.JwtService;
import cl.ferrefix.security.JwtVerificador;
import cl.ferrefix.security.UsuarioToken;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arranca el servicio COMPLETO (configuración, seguridad, filtro JWT, controllers y services reales) sin Oracle:
 * solo el repositorio de usuarios está simulado. Los tokens, la firma y el cifrado de claves son los de verdad.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "ferrefix.jwt.secret=" + ServicioAuthIntegracionTest.SECRET,
        "ferrefix.jwt.access-expiration-ms=900000",
        "ferrefix.jwt.refresh-expiration-ms=604800000",
        "ferrefix.seed.admin-password=",
        "ferrefix.seed.client-password="})
@AutoConfigureMockMvc
class ServicioAuthIntegracionTest {

    static final String SECRET = "clave-de-pruebas-ferrefix-con-mas-de-64-bytes-para-firmar-con-hs512-ok-1234";

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext contexto;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwtService;
    @Autowired JwtVerificador verificador;
    @Autowired ObjectMapper json;
    @MockitoBean UsuarioRepository repo;
    @MockitoBean PlatformTransactionManager transacciones;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setId(2L);
        usuario.setRut("15432890-4");
        usuario.setNombre("Cliente Demo");
        usuario.setEmail("cliente@ferrefix.cl");
        usuario.setPasswordHash(encoder.encode("clave1234"));
        usuario.setActivo(true);
        usuario.setRoles(EnumSet.of(Rol.CLIENTE));
        when(repo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenAnswer(i -> Optional.of(usuario));
        when(repo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private JsonNode cuerpo(org.springframework.test.web.servlet.MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    @Test
    void laAutenticacionEsSoloPorJwtSinUsuarioEnMemoria() {
        assertTrue(contexto.getBeansOfType(UserDetailsService.class).isEmpty(),
                "No debe existir el usuario en memoria con clave generada de Spring Boot");
        assertTrue(encoder.matches("clave1234", usuario.getPasswordHash()));
        assertTrue(usuario.getPasswordHash().startsWith("$2"), "Las claves se guardan con BCrypt");
    }

    @Test
    void elLoginRealEntregaUnAccessTokenQueCualquierServicioPuedeVerificar() throws Exception {
        JsonNode r = cuerpo(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cliente@ferrefix.cl\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
                .andReturn());

        // Otro servicio, con el mismo secreto y sin base de usuarios, obtiene la identidad completa del token.
        UsuarioToken identidad = UsuarioToken.desde(new JwtVerificador(SECRET).validarAccessToken(r.get("accessToken").asString()));
        assertEquals(new UsuarioToken(2L, "cliente@ferrefix.cl", "15432890-4", "Cliente Demo", List.of("CLIENTE")), identidad);
        assertEquals("cliente@ferrefix.cl", verificador.validarRefreshToken(r.get("refreshToken").asString()).getSubject());
    }

    @Test
    void conClaveIncorrectaElLoginDa401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cliente@ferrefix.cl\",\"password\":\"otra-clave\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void conElAccessTokenSeConsultaYSeEditaElPerfil() throws Exception {
        String access = jwtService.generarAccessToken(usuario);

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@ferrefix.cl"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"));

        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Juan Maestro\",\"comuna\":\"Providencia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Juan Maestro"))
                .andExpect(jsonPath("$.comuna").value("Providencia"))
                .andExpect(jsonPath("$.rut").value("15432890-4"));
    }

    @Test
    void elRefreshPorHttpEntregaLosRolesQueHayEnLaBaseEnEseMomento() throws Exception {
        String refresh = jwtService.generarRefreshToken(usuario);          // emitido cuando solo era CLIENTE
        usuario.setRoles(EnumSet.of(Rol.CLIENTE, Rol.ADMIN));              // después se le agregó ADMIN en la base
        usuario.setNombre("Juan Maestro");

        JsonNode r = cuerpo(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn());

        UsuarioToken renovado = UsuarioToken.desde(verificador.validarAccessToken(r.get("accessToken").asString()));
        assertEquals(List.of("ADMIN", "CLIENTE"), renovado.roles());
        assertEquals("Juan Maestro", renovado.nombre());
    }

    @Test
    void sinTokenConTokenAjenoOConRefreshTokenLasRutasProtegidasDan401() throws Exception {
        String ajeno = Jwts.builder().subject("cliente@ferrefix.cl").claim("uid", 2).claim("type", "access")
                .claim("rut", "15432890-4").claim("nombre", "Cliente Demo").claim("roles", List.of("ADMIN"))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(JwtVerificador.clave("otra-clave-distinta-ferrefix-de-mas-de-64-bytes-para-hs512-no-coincide-99"), Jwts.SIG.HS512)
                .compact();
        String refresh = jwtService.generarRefreshToken(usuario);

        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + ajeno)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void elDocumentoOpenApiDescribeSoloAutenticacionYPerfil() throws Exception {
        JsonNode rutas = cuerpo(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn()).get("paths");
        for (String ruta : new String[]{"/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
                "/api/v1/auth/me", "/api/v1/users/me"}) {
            assertTrue(rutas.has(ruta), "Falta en el documento: " + ruta);
        }
        assertEquals(5, rutas.size(), "Este servicio no expone nada más: " + rutas.propertyNames());
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
