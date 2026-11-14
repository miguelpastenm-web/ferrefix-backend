package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.security.*;
import cl.ferrefix.service.ArriendoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS es responsabilidad EXCLUSIVA del gateway (ahí viven los tests del preflight y de los orígenes permitidos).
 * Este servicio no debe añadir cabeceras Access-Control-*: si lo hiciera, el navegador recibiría
 * Access-Control-Allow-Origin duplicada (una del gateway y otra de aquí) y rechazaría la respuesta.
 */
@WebMvcTest(ArriendoController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class SinCorsTest {

    @Autowired MockMvc mvc;
    @MockitoBean ArriendoService arriendoService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void unaPeticionConOrigenSeAtiendeSinCabecerasCors() throws Exception {
        mvc.perform(patch("/api/v1/arriendos/77/devolver")
                        .with(user("admin@ferrefix.cl").roles("ADMIN"))
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Methods"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Headers"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().doesNotExist("Access-Control-Max-Age"));
    }
}
