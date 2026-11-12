package cl.ferrefix.seed;

import cl.ferrefix.config.MediaConfig;
import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.ImagenController;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.security.*;
import cl.ferrefix.service.ImagenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Las imágenes sembradas se sirven en /media/** SIN token, aunque la carpeta de uploads esté vacía. */
@WebMvcTest(ImagenController.class)
@Import({SecurityConfig.class, MediaConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class SeedMediaTest {

    private static final Path UPLOADS_VACIO;

    static {
        try {
            UPLOADS_VACIO = Files.createTempDirectory("ferrefix-uploads-vacio");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("ferrefix.upload.dir", UPLOADS_VACIO::toString);
    }

    @Autowired MockMvc mvc;
    @MockitoBean ImagenService imagenService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void cadaImagenDelV3SeSirvePublicaComoWebp() throws Exception {
        List<String> urls = SeedSql.urlsDeImagen(SeedSql.productos(SeedSql.leer()));
        assertEquals(26, urls.size());
        for (String url : urls) {
            mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/webp"))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")));
        }
    }

    @Test
    void imagenSembradaInexistenteDa404() throws Exception {
        mvc.perform(get("/media/productos/no-existe.webp")).andExpect(status().isNotFound());
    }
}
