package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.dto.MaquinaResponseDto;
import cl.ferrefix.model.dto.UnidadResponseDto;
import cl.ferrefix.security.*;
import cl.ferrefix.service.MaquinaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
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

@WebMvcTest(MaquinaController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class MaquinaControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean MaquinaService maquinaService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    private static final RequestPostProcessor ADMIN = user("admin@ferrefix.cl").roles("ADMIN");
    private static final RequestPostProcessor CLIENTE = user("cliente@ferrefix.cl").roles("CLIENTE");

    private static final String BODY = """
            {"codigo":"MAQ-BET-02","nombre":"Betonera","marca":"Ingco","categoria":"Hormigón y Mezcla",
             "tarifaDia":18990,"incluye":"Cable","imagenUrl":"/media/maquinas/maq-bet-02.webp",
             "especificaciones":{"Motor":"0.75 HP","Ruedas":"10 pulgadas"}}""";

    private static MaquinaResponseDto maquina() {
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("Motor", "0.75 HP");
        return new MaquinaResponseDto(1L, "MAQ-BET-02", "Betonera", "Ingco", "Hormigón y Mezcla", 18990, "DISPONIBLE",
                "Cable", "/media/maquinas/maq-bet-02.webp", specs, 2, 1, LocalDateTime.now(), LocalDateTime.now());
    }

    private static UnidadResponseDto unidad() {
        return new UnidadResponseDto(10L, 1L, "MAQ-BET-02-U01", "DISPONIBLE", LocalDateTime.now());
    }

    // ---------------------------------------------------------------- catálogo público
    @Test
    void listarEsPublicoYTraeConteos() throws Exception {
        when(maquinaService.listar(null, null, false, null)).thenReturn(List.of(maquina()));
        mvc.perform(get("/api/v1/maquinas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("MAQ-BET-02"))
                .andExpect(jsonPath("$[0].unidadesTotales").value(2))
                .andExpect(jsonPath("$[0].unidadesDisponibles").value(1))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"));
    }

    @Test
    void listarPasaFiltros() throws Exception {
        when(maquinaService.listar("bet", "Hormigón y Mezcla", true, "tarifa,desc")).thenReturn(List.of());
        mvc.perform(get("/api/v1/maquinas").param("q", "bet").param("categoria", "Hormigón y Mezcla")
                        .param("soloDisponibles", "true").param("sort", "tarifa,desc"))
                .andExpect(status().isOk());
        verify(maquinaService).listar("bet", "Hormigón y Mezcla", true, "tarifa,desc");
    }

    @Test
    void sortInvalidoDa400() throws Exception {
        when(maquinaService.listar(any(), any(), eq(false), eq("hack"))).thenThrow(new BadRequestException("sort inválido"));
        mvc.perform(get("/api/v1/maquinas").param("sort", "hack")).andExpect(status().isBadRequest());
    }

    @Test
    void obtenerEsPublicoY404() throws Exception {
        when(maquinaService.obtener(1L)).thenReturn(maquina());
        when(maquinaService.obtener(9L)).thenThrow(new ResourceNotFoundException("no"));
        mvc.perform(get("/api/v1/maquinas/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get("/api/v1/maquinas/9")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- escritura de modelos
    @Test
    void sinTokenDa401() throws Exception {
        mvc.perform(post("/api/v1/maquinas").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/maquinas/1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/maquinas/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(maquinaService);
    }

    @Test
    void clienteDa403AunConBodyVacio() throws Exception {
        mvc.perform(post("/api/v1/maquinas").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(put("/api/v1/maquinas/1").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/maquinas/1").with(CLIENTE)).andExpect(status().isForbidden());
        verifyNoInteractions(maquinaService);
    }

    @Test
    void crearComoAdminDa201() throws Exception {
        when(maquinaService.crear(any())).thenReturn(maquina());
        mvc.perform(post("/api/v1/maquinas").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void crearConDatosInvalidosDa400() throws Exception {
        String malo = """
                {"codigo":"con espacio","nombre":"","marca":"M","categoria":"C","tarifaDia":0}""";
        mvc.perform(post("/api/v1/maquinas").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(malo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(3));
        verifyNoInteractions(maquinaService);
    }

    @Test
    void crearCodigoDuplicadoDa409() throws Exception {
        when(maquinaService.crear(any())).thenThrow(new DuplicateResourceException("dup"));
        mvc.perform(post("/api/v1/maquinas").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void editarYEliminarComoAdmin() throws Exception {
        when(maquinaService.actualizar(eq(1L), any())).thenReturn(maquina());
        mvc.perform(put("/api/v1/maquinas/1").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/maquinas/1").with(ADMIN)).andExpect(status().isNoContent());
        verify(maquinaService).eliminar(1L);
    }

    @Test
    void eliminarConArriendosDa409Json() throws Exception {
        doThrow(new ConflictException("La máquina tiene arriendos registrados")).when(maquinaService).eliminar(1L);
        mvc.perform(delete("/api/v1/maquinas/1").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La máquina tiene arriendos registrados"));
    }

    @Test
    void bloqueoOcupadoDa409() throws Exception {
        doThrow(new CannotAcquireLockException("timeout")).when(maquinaService).eliminar(1L);
        mvc.perform(delete("/api/v1/maquinas/1").with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Intente nuevamente")));
    }

    // ---------------------------------------------------------------- unidades (solo ADMIN)
    @Test
    void listarUnidadesSoloAdmin() throws Exception {
        when(maquinaService.listarUnidades(1L)).thenReturn(List.of(unidad()));
        mvc.perform(get("/api/v1/maquinas/1/unidades")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/maquinas/1/unidades").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/maquinas/1/unidades").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("MAQ-BET-02-U01"));
    }

    @Test
    void agregarUnidades() throws Exception {
        when(maquinaService.agregarUnidades(1L, 2)).thenReturn(List.of(unidad(), unidad()));
        mvc.perform(post("/api/v1/maquinas/1/unidades").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/v1/maquinas/1/unidades").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/maquinas/1/unidades").contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":2}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void agregarUnidadesCantidadInvalidaDa400() throws Exception {
        for (String body : new String[]{"{\"cantidad\":0}", "{\"cantidad\":21}", "{}"}) {
            mvc.perform(post("/api/v1/maquinas/1/unidades").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(maquinaService, never()).agregarUnidades(anyLong(), anyInt());
    }

    @Test
    void eliminarUnidad() throws Exception {
        mvc.perform(delete("/api/v1/maquinas/1/unidades/10").with(ADMIN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/maquinas/1/unidades/10").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/maquinas/1/unidades/10")).andExpect(status().isUnauthorized());
        verify(maquinaService, times(1)).eliminarUnidad(1L, 10L);
    }

    @Test
    void eliminarUnidadArrendadaDa409() throws Exception {
        doThrow(new ConflictException("arrendada")).when(maquinaService).eliminarUnidad(1L, 10L);
        mvc.perform(delete("/api/v1/maquinas/1/unidades/10").with(ADMIN)).andExpect(status().isConflict());
    }
}
