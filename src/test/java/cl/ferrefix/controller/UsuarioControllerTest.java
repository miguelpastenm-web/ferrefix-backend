package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.dto.ActualizarPerfilRequestDto;
import cl.ferrefix.model.dto.UsuarioResponseDto;
import cl.ferrefix.security.*;
import cl.ferrefix.service.UsuarioService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** El filtro JWT es el REAL (JwtService y UserDetailsService mockeados), como en CotizacionControllerTest. */
@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class UsuarioControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    private static final RequestPostProcessor ADMIN = user("admin@ferrefix.cl").roles("ADMIN");
    private static final RequestPostProcessor CLIENTE = user("cliente@ferrefix.cl").roles("CLIENTE");

    private static final String BODY = """
            {"nombre":"Juan Maestro","telefono":"+56 9 8765 4321","direccion":"Av. Providencia 1240",
             "region":"Región Metropolitana","comuna":"Providencia"}""";

    private static UsuarioResponseDto perfil(String email) {
        return new UsuarioResponseDto(2L, "15432890-4", "Juan Maestro", email, "+56 9 8765 4321", "Av. Providencia 1240",
                "Región Metropolitana", "Providencia", true, List.of("CLIENTE"), LocalDateTime.now());
    }

    // ---------------------------------------------------------------- acceso
    @Test
    void anonimoDa401Json() throws Exception {
        mvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void clienteYAdminEditanSuPropioPerfil() throws Exception {
        when(usuarioService.actualizarPerfil(eq("cliente@ferrefix.cl"), any())).thenReturn(perfil("cliente@ferrefix.cl"));
        when(usuarioService.actualizarPerfil(eq("admin@ferrefix.cl"), any())).thenReturn(perfil("admin@ferrefix.cl"));

        mvc.perform(put("/api/v1/users/me").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@ferrefix.cl"))
                .andExpect(jsonPath("$.nombre").value("Juan Maestro"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(put("/api/v1/users/me").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@ferrefix.cl"));
    }

    @Test
    void noExisteEdicionDeOtrosUsuarios() throws Exception {
        mvc.perform(put("/api/v1/users/5").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/users/5").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
        verifyNoInteractions(usuarioService);
    }

    // ---------------------------------------------------------------- filtro JWT real
    private void tokenValidoDeCliente() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("cliente@ferrefix.cl");
        when(jwtService.validarAccessToken("TOKEN-VALIDO")).thenReturn(claims);
        when(userDetailsService.loadUserByUsername("cliente@ferrefix.cl"))
                .thenReturn(User.withUsername("cliente@ferrefix.cl").password("x").roles("CLIENTE").build());
    }

    @Test
    void conTokenValidoDelFiltroRealElServiceRecibeElEmailDelToken() throws Exception {
        tokenValidoDeCliente();
        when(usuarioService.actualizarPerfil(eq("cliente@ferrefix.cl"), any())).thenReturn(perfil("cliente@ferrefix.cl"));

        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer TOKEN-VALIDO")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(jwtService).validarAccessToken("TOKEN-VALIDO");
        verify(usuarioService).actualizarPerfil(eq("cliente@ferrefix.cl"), any());
    }

    @Test
    void tokenInvalidoOVencidoDa401EnEstaRutaProtegida() throws Exception {
        when(jwtService.validarAccessToken("BASURA")).thenThrow(new MalformedJwtException("mal formado"));
        when(jwtService.validarAccessToken("VENCIDO")).thenThrow(new ExpiredJwtException(null, null, "JWT expired"));
        for (String token : new String[]{"BASURA", "VENCIDO"}) {
            mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
            verify(jwtService).validarAccessToken(token);       // el filtro sí se ejecutó y rechazó el token
        }
        verifyNoInteractions(usuarioService);
    }

    // ---------------------------------------------------------------- el cuerpo no puede cambiar lo inmutable
    @Test
    void emailRutRolesPasswordYActivoDelCuerpoSeIgnoran() throws Exception {
        when(usuarioService.actualizarPerfil(any(), any())).thenReturn(perfil("cliente@ferrefix.cl"));
        String conIntrusos = BODY.replace("{", "{\"id\":99,\"email\":\"hack@x.cl\",\"rut\":\"1-9\",\"roles\":[\"ADMIN\"],"
                + "\"password\":\"nueva12345\",\"passwordHash\":\"x\",\"activo\":false,\"createdAt\":\"2000-01-01T00:00:00\",");
        mvc.perform(put("/api/v1/users/me").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(conIntrusos))
                .andExpect(status().isOk());

        ArgumentCaptor<ActualizarPerfilRequestDto> dto = ArgumentCaptor.forClass(ActualizarPerfilRequestDto.class);
        verify(usuarioService).actualizarPerfil(eq("cliente@ferrefix.cl"), dto.capture());   // el email es el del token, no "hack@x.cl"
        assertEquals("Juan Maestro", dto.getValue().nombre());
        assertEquals("Providencia", dto.getValue().comuna());
    }

    // ---------------------------------------------------------------- validación
    @Test
    void camposInvalidosDan400ConDetalleYNoLlegaAlService() throws Exception {
        String[] malos = {
                "{}",
                BODY.replace("Juan Maestro", ""),
                BODY.replace("Juan Maestro", "   "),
                BODY.replace("Juan Maestro", "x".repeat(121)),
                BODY.replace("+56 9 8765 4321", "abc"),
                BODY.replace("+56 9 8765 4321", "1".repeat(21)),
                BODY.replace("Av. Providencia 1240", "x".repeat(201)),
                BODY.replace("Región Metropolitana", "x".repeat(81)),
                BODY.replace("Providencia\"}", "x".repeat(81) + "\"}")
        };
        for (String malo : malos) {
            mvc.perform(put("/api/v1/users/me").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(malo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.details").isArray());
        }
        verifyNoInteractions(usuarioService);
    }

    @Test
    void losOpcionalesPuedenOmitirseONullOVacios() throws Exception {
        when(usuarioService.actualizarPerfil(any(), any())).thenReturn(perfil("cliente@ferrefix.cl"));
        for (String cuerpo : new String[]{
                "{\"nombre\":\"Juan\"}",
                "{\"nombre\":\"Juan\",\"telefono\":null,\"direccion\":null,\"region\":null,\"comuna\":null}",
                "{\"nombre\":\"Juan\",\"telefono\":\"\",\"direccion\":\"\",\"region\":\"\",\"comuna\":\"\"}"}) {
            mvc.perform(put("/api/v1/users/me").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isOk());
        }
        ArgumentCaptor<ActualizarPerfilRequestDto> dto = ArgumentCaptor.forClass(ActualizarPerfilRequestDto.class);
        verify(usuarioService, times(3)).actualizarPerfil(eq("cliente@ferrefix.cl"), dto.capture());
        assertNull(dto.getAllValues().get(0).telefono());
        assertNull(dto.getAllValues().get(1).comuna());
        assertEquals("", dto.getAllValues().get(2).region());     // el service lo convierte en null
    }

    @Test
    void jsonMalFormadoYContentTypeIncorrecto() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{no es json")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/users/me").with(CLIENTE).contentType(MediaType.TEXT_PLAIN).content("hola")).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(usuarioService);
    }

    @Test
    void usuarioQueYaNoExisteDa404Json() throws Exception {
        when(usuarioService.actualizarPerfil(any(), any())).thenThrow(new ResourceNotFoundException("Usuario no encontrado"));
        mvc.perform(put("/api/v1/users/me").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
