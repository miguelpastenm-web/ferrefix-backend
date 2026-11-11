package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.model.dto.*;
import cl.ferrefix.security.*;
import cl.ferrefix.service.AuthService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    private static final UsuarioResponseDto USUARIO = new UsuarioResponseDto(1L, "15432890-4", "Juan", "cliente@ferrefix.cl",
            null, null, null, null, true, List.of("CLIENTE"), LocalDateTime.now());
    private static final TokenResponseDto TOKENS = new TokenResponseDto("AT", "RT", "Bearer", 900, USUARIO);

    // ---- login
    @Test
    void loginOk() throws Exception {
        when(authService.login(any())).thenReturn(TOKENS);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cliente@ferrefix.cl\",\"password\":\"clave1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("AT"))
                .andExpect(jsonPath("$.refreshToken").value("RT"))
                .andExpect(jsonPath("$.usuario.roles[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.usuario.password").doesNotExist());
    }

    @Test
    void loginSinCamposDa400ConDetalle() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void loginCredencialesInvalidasDa401Json() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Credenciales inválidas"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.cl\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    // ---- register
    private static final String REGISTRO = "{\"rut\":\"11.111.111-1\",\"nombre\":\"Ana\",\"email\":\"ana@x.cl\",\"password\":\"clave1234\"}";

    @Test
    void registerDa201() throws Exception {
        when(authService.register(any())).thenReturn(TOKENS);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("AT"));
    }

    @Test
    void registerClaveCortaDa400() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRO.replace("clave1234", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("password")));
    }

    @Test
    void registerRutInvalidoDa400() throws Exception {
        when(authService.register(any())).thenThrow(new BadRequestException("El RUT ingresado no es válido"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El RUT ingresado no es válido"));
    }

    @Test
    void registerDuplicadoDa409() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateResourceException("Ya existe una cuenta"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---- refresh
    @Test
    void refreshOk() throws Exception {
        when(authService.refresh(any())).thenReturn(TOKENS);
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"RT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("AT"));
    }

    @Test
    void refreshInvalidoDa401() throws Exception {
        when(authService.refresh(any())).thenThrow(new BadCredentialsException("Refresh token inválido o vencido"));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- me / seguridad
    @Test
    void meSinTokenDa401Json() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
    }

    // ---- errores de Spring MVC (antes caían en el handler genérico y devolvían 500)
    @Test
    void rutaInexistenteConTokenDa404Json() throws Exception {
        mvc.perform(get("/api/v1/nada").with(user("cliente@ferrefix.cl").roles("CLIENTE")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/nada"));
    }

    @Test
    void metodoNoSoportadoDa405JsonConAllow() throws Exception {
        // /auth/login solo acepta POST
        mvc.perform(get("/api/v1/auth/login").with(user("cliente@ferrefix.cl").roles("CLIENTE")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void contentTypeNoSoportadoDa415Json() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void meConTokenInvalidoDa401NoDa500() throws Exception {
        when(jwtService.validarAccessToken("basura")).thenThrow(new JwtException("mal"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meAutenticadoDa200SinHash() throws Exception {
        when(authService.me("cliente@ferrefix.cl")).thenReturn(USUARIO);
        mvc.perform(get("/api/v1/auth/me").with(user("cliente@ferrefix.cl").roles("CLIENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@ferrefix.cl"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
