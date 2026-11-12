package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.service.ArriendoService;
import cl.ferrefix.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORS del frontend (Vite en :5173): el navegador hace un preflight OPTIONS antes de cada PATCH/PUT/DELETE. */
@WebMvcTest(ArriendoController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class CorsTest {

    private static final String ORIGEN = "http://localhost:5173";

    @Autowired MockMvc mvc;
    @MockitoBean ArriendoService arriendoService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void elPreflightDePatchSePermiteSinToken() throws Exception {
        mvc.perform(options("/api/v1/arriendos/77/devolver")
                        .header("Origin", ORIGEN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }

    @Test
    void elPreflightPermiteTodosLosMetodosDelCrud() throws Exception {
        for (String metodo : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"}) {
            mvc.perform(options("/api/v1/productos")
                            .header("Origin", ORIGEN)
                            .header("Access-Control-Request-Method", metodo))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void unOrigenNoAutorizadoSeRechaza() throws Exception {
        mvc.perform(options("/api/v1/arriendos/77/devolver")
                        .header("Origin", "http://sitio-malicioso.example")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }

    @Test
    void laRespuestaRealDelPatchTrae_AccessControlAllowOrigin() throws Exception {
        mvc.perform(patch("/api/v1/arriendos/77/devolver")
                        .with(user("admin@ferrefix.cl").roles("ADMIN"))
                        .header("Origin", ORIGEN))
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN));
    }

    @Test
    void unMetodoNoDeclaradoNoSePermiteEnElPreflight() throws Exception {
        mvc.perform(options("/api/v1/productos")
                        .header("Origin", ORIGEN)
                        .header("Access-Control-Request-Method", "TRACE"))
                .andExpect(status().isForbidden());
    }
}
