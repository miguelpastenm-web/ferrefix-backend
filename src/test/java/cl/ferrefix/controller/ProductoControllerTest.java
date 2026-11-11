package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.dto.ProductoResponseDto;
import cl.ferrefix.security.*;
import cl.ferrefix.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductoController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class ProductoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductoService productoService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    private static final RequestPostProcessor ADMIN = user("admin@ferrefix.cl").roles("ADMIN");
    private static final RequestPostProcessor CLIENTE = user("cliente@ferrefix.cl").roles("CLIENTE");

    private static final String BODY = """
            {"sku":"5422223214","nombre":"Taladro","marca":"DeWalt","categoria":"Herramientas",
             "precioVenta":129990,"stock":12,"descripcion":"d","imagenUrl":"/media/productos/a.webp",
             "especificaciones":{"Peso":"1.6 kg","Garantía":"3 años"}}""";

    private static ProductoResponseDto producto() {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("Peso", "1.6 kg");
        specs.put("Garantía", "3 años");
        return new ProductoResponseDto(1L, "5422223214", "Taladro", "DeWalt", "Herramientas", 129990, 12, "DISPONIBLE",
                "/media/productos/a.webp", null, "d", specs, LocalDateTime.now(), LocalDateTime.now());
    }

    // ---------------------------------------------------------------- lectura pública
    @Test
    void listarEsPublico() throws Exception {
        when(productoService.listar(null, null, null, null)).thenReturn(List.of(producto()));
        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("5422223214"))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[0].especificaciones.Peso").value("1.6 kg"));
    }

    @Test
    void listarPasaFiltrosYSort() throws Exception {
        when(productoService.listar("tal", "Herramientas", "DeWalt", "precio,desc")).thenReturn(List.of());
        mvc.perform(get("/api/v1/productos").param("q", "tal").param("categoria", "Herramientas")
                        .param("marca", "DeWalt").param("sort", "precio,desc"))
                .andExpect(status().isOk());
        verify(productoService).listar("tal", "Herramientas", "DeWalt", "precio,desc");
    }

    @Test
    void sortInvalidoDa400Json() throws Exception {
        when(productoService.listar(any(), any(), any(), eq("hack"))).thenThrow(new BadRequestException("Parámetro sort inválido"));
        mvc.perform(get("/api/v1/productos").param("sort", "hack"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void obtenerEsPublico() throws Exception {
        when(productoService.obtener(1L)).thenReturn(producto());
        mvc.perform(get("/api/v1/productos/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void obtenerInexistenteDa404() throws Exception {
        when(productoService.obtener(9L)).thenThrow(new ResourceNotFoundException("Producto no encontrado: 9"));
        mvc.perform(get("/api/v1/productos/9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void idNoNumericoDa400() throws Exception {
        mvc.perform(get("/api/v1/productos/abc")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- escritura: roles
    @Test
    void crearSinTokenDa401() throws Exception {
        mvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(productoService);
    }

    @Test
    void crearComoClienteDa403Json() throws Exception {
        mvc.perform(post("/api/v1/productos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(productoService);
    }

    // La autorización por URL se resuelve antes que la validación del body: body inválido NO debe dar 400.
    @Test
    void clienteConBodyVacioDa403NoDa400() throws Exception {
        mvc.perform(post("/api/v1/productos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(put("/api/v1/productos/1").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productoService);
    }

    @Test
    void sinTokenConBodyVacioDa401NoDa400() throws Exception {
        mvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(put("/api/v1/productos/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(productoService);
    }

    @Test
    void editarComoClienteDa403() throws Exception {
        mvc.perform(put("/api/v1/productos/1").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminarComoClienteDa403() throws Exception {
        mvc.perform(delete("/api/v1/productos/1").with(CLIENTE)).andExpect(status().isForbidden());
        verifyNoInteractions(productoService);
    }

    @Test
    void eliminarSinTokenDa401() throws Exception {
        mvc.perform(delete("/api/v1/productos/1")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- escritura: ADMIN
    @Test
    void crearComoAdminDa201() throws Exception {
        when(productoService.crear(any())).thenReturn(producto());
        mvc.perform(post("/api/v1/productos").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void crearConDatosInvalidosDa400ConDetalles() throws Exception {
        String malo = """
                {"sku":"con espacio","nombre":"","marca":"M","categoria":"Herramientas","precioVenta":0,"stock":-1}""";
        mvc.perform(post("/api/v1/productos").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details.length()").value(4));
        verifyNoInteractions(productoService);
    }

    @Test
    void crearConImagenJavascriptDa400() throws Exception {
        mvc.perform(post("/api/v1/productos").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("/media/productos/a.webp", "javascript:alert(1)")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearSkuDuplicadoDa409() throws Exception {
        when(productoService.crear(any())).thenThrow(new DuplicateResourceException("Ya existe un producto con el SKU 5422223214"));
        mvc.perform(post("/api/v1/productos").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void editarComoAdminDa200() throws Exception {
        when(productoService.actualizar(eq(1L), any())).thenReturn(producto());
        mvc.perform(put("/api/v1/productos/1").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void editarInexistenteDa404() throws Exception {
        when(productoService.actualizar(eq(9L), any())).thenThrow(new ResourceNotFoundException("no"));
        mvc.perform(put("/api/v1/productos/9").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarComoAdminDa204() throws Exception {
        mvc.perform(delete("/api/v1/productos/1").with(ADMIN)).andExpect(status().isNoContent());
        verify(productoService).eliminar(1L);
    }

    @Test
    void eliminarInexistenteDa404() throws Exception {
        doThrow(new ResourceNotFoundException("no")).when(productoService).eliminar(9L);
        mvc.perform(delete("/api/v1/productos/9").with(ADMIN)).andExpect(status().isNotFound());
    }
}
