package cl.ferrefix.controller;

import cl.ferrefix.config.MediaConfig;
import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.model.dto.ImagenResponseDto;
import cl.ferrefix.security.*;
import cl.ferrefix.service.ImagenService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImagenController.class)
@Import({SecurityConfig.class, MediaConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class ImagenControllerTest {

    private static final Path DIR_UPLOADS;

    static {
        try {
            DIR_UPLOADS = Files.createTempDirectory("ferrefix-media-test");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("ferrefix.upload.dir", DIR_UPLOADS::toString);
    }

    @BeforeAll
    static void crearArchivoDeMedia() throws IOException {
        Files.createDirectories(DIR_UPLOADS.resolve("productos"));
        Files.write(DIR_UPLOADS.resolve("productos/foto.webp"), new byte[]{'R', 'I', 'F', 'F'});
        Files.writeString(DIR_UPLOADS.getParent().resolve("secreto-fuera-de-uploads.txt"), "secreto");
    }

    @Autowired MockMvc mvc;
    @MockitoBean ImagenService imagenService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    private static final RequestPostProcessor ADMIN = user("admin@ferrefix.cl").roles("ADMIN");
    private static final RequestPostProcessor CLIENTE = user("cliente@ferrefix.cl").roles("CLIENTE");

    private static MockMultipartFile archivo() {
        return new MockMultipartFile("archivo", "foto.webp", "image/webp", new byte[]{1, 2, 3});
    }

    // ---------------------------------------------------------------- POST /api/v1/imagenes
    @Test
    void adminSubeImagenDa201() throws Exception {
        when(imagenService.guardar(any(), eq("productos")))
                .thenReturn(new ImagenResponseDto("/media/productos/abc.webp", "productos", "image/webp", 3));
        mvc.perform(multipart("/api/v1/imagenes").file(archivo()).param("destino", "productos").with(ADMIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("/media/productos/abc.webp"))
                .andExpect(jsonPath("$.tipo").value("image/webp"));
    }

    @Test
    void sinTokenDa401Json() throws Exception {
        mvc.perform(multipart("/api/v1/imagenes").file(archivo()).param("destino", "productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(imagenService);
    }

    @Test
    void clienteDa403Json() throws Exception {
        mvc.perform(multipart("/api/v1/imagenes").file(archivo()).param("destino", "productos").with(CLIENTE))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(imagenService);
    }

    @Test
    void archivoGrandeDa413Json() throws Exception {
        when(imagenService.guardar(any(), any())).thenThrow(new MaxUploadSizeExceededException(6_291_456));
        mvc.perform(multipart("/api/v1/imagenes").file(archivo()).param("destino", "productos").with(ADMIN))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("6 MB")));
    }

    @Test
    void archivoQueNoEsImagenDa400() throws Exception {
        when(imagenService.guardar(any(), any())).thenThrow(new BadRequestException("El archivo no es una imagen válida"));
        mvc.perform(multipart("/api/v1/imagenes").file(archivo()).param("destino", "productos").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El archivo no es una imagen válida"));
    }

    @Test
    void sinDestinoDa400() throws Exception {
        mvc.perform(multipart("/api/v1/imagenes").file(archivo()).with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("destino")));
    }

    @Test
    void sinArchivoDa400() throws Exception {
        mvc.perform(multipart("/api/v1/imagenes").param("destino", "productos").with(ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void jsonEnVezDeMultipartDa415() throws Exception {
        mvc.perform(post("/api/v1/imagenes").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void otrosMetodosSobreImagenesTambienExigenAdmin() throws Exception {
        mvc.perform(delete("/api/v1/imagenes/abc").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/imagenes")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- GET /media/** (público)
    @Test
    void mediaEsPublicoYTieneCacheLarga() throws Exception {
        mvc.perform(get("/media/productos/foto.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=31536000")));
    }

    @Test
    void mediaInexistenteDa404Json() throws Exception {
        mvc.perform(get("/media/productos/no-existe.webp"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void mediaNoPermiteSalirseDeLaCarpeta() throws Exception {
        for (String ruta : new String[]{"/media/../secreto-fuera-de-uploads.txt", "/media/%2e%2e/secreto-fuera-de-uploads.txt",
                "/media/productos/../../secreto-fuera-de-uploads.txt"}) {
            mvc.perform(get(ruta)).andExpect(result -> {
                int s = result.getResponse().getStatus();
                if (s == 200) {
                    throw new AssertionError("Se pudo leer un archivo fuera de uploads con " + ruta);
                }
            });
        }
    }
}
