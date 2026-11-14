package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.dto.ArriendoResponseDto;
import cl.ferrefix.model.dto.CrearArriendoRequestDto;
import cl.ferrefix.security.*;
import cl.ferrefix.service.ArriendoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArriendoController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class ArriendoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ArriendoService arriendoService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    private static final RequestPostProcessor ADMIN = user("admin@ferrefix.cl").roles("ADMIN");
    private static final RequestPostProcessor CLIENTE = user("cliente@ferrefix.cl").roles("CLIENTE");

    private static final String BODY = """
            {"maquinaId":5,"diasArriendo":5,"fechaInicio":"2026-11-25","telefono":"+56 9 8765 4321",
             "direccionFaena":"Av. Faena 123","region":"Región Metropolitana","comuna":"Providencia"}""";

    private static ArriendoResponseDto arriendo(boolean conCliente) {
        return new ArriendoResponseDto(77L, "ARR-000077", "VIGENTE",
                new ArriendoResponseDto.MaquinaResumen(5L, "MAQ-BET-02", "Betonera", "Ingco", "/media/maquinas/maq-bet-02.webp"),
                "MAQ-BET-02-U01", 18990, 5, LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 29), 94950L,
                "+56 9 8765 4321", "Av. Faena 123", "Región Metropolitana", "Providencia", null,
                LocalDateTime.now(), LocalDateTime.now(),
                conCliente ? new ArriendoResponseDto.ClienteResumen(3L, "15432890-4", "Juan Maestro", "cliente@ferrefix.cl") : null);
    }

    // ---------------------------------------------------------------- POST /arriendos
    @Test
    void clienteArrendaYRecibe201() throws Exception {
        when(arriendoService.crear(eq("cliente@ferrefix.cl"), any())).thenReturn(arriendo(false));
        mvc.perform(post("/api/v1/arriendos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("ARR-000077"))
                .andExpect(jsonPath("$.unidadCodigo").value("MAQ-BET-02-U01"))
                .andExpect(jsonPath("$.total").value(94950))
                .andExpect(jsonPath("$.maquina.codigo").value("MAQ-BET-02"))
                .andExpect(jsonPath("$.cliente").doesNotExist());
    }

    @Test
    void adminNoPuedeArrendarDa403Json() throws Exception {
        mvc.perform(post("/api/v1/arriendos").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/v1/arriendos").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());   // el rol se evalúa antes que la validación del body
        verifyNoInteractions(arriendoService);
    }

    @Test
    void sinTokenDa401() throws Exception {
        mvc.perform(post("/api/v1/arriendos").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(arriendoService);
    }

    @Test
    void elCuerpoNoPuedeImponerTotalTarifaNiDatosDelCliente() throws Exception {
        when(arriendoService.crear(any(), any())).thenReturn(arriendo(false));
        String conIntrusos = BODY.replace("\"maquinaId\":5,",
                "\"maquinaId\":5,\"total\":1,\"tarifaDia\":1,\"rut\":\"1-9\",\"nombre\":\"Otro\",\"email\":\"otro@x.cl\",\"usuarioId\":99,\"unidadId\":3,");
        mvc.perform(post("/api/v1/arriendos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(conIntrusos))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearArriendoRequestDto> dto = ArgumentCaptor.forClass(CrearArriendoRequestDto.class);
        verify(arriendoService).crear(eq("cliente@ferrefix.cl"), dto.capture());   // el cliente es el del token
        assertEquals(5L, dto.getValue().maquinaId());
        assertEquals(5, dto.getValue().diasArriendo());
    }

    @Test
    void datosInvalidosDan400ConDetalle() throws Exception {
        String[] malos = {
                BODY.replace("\"diasArriendo\":5", "\"diasArriendo\":0"),
                BODY.replace("\"diasArriendo\":5", "\"diasArriendo\":91"),
                BODY.replace("\"fechaInicio\":\"2026-11-25\",", ""),
                BODY.replace("\"maquinaId\":5,", ""),
                BODY.replace("+56 9 8765 4321", "abc"),
                BODY.replace("Av. Faena 123", ""),
                "{}"
        };
        for (String malo : malos) {
            mvc.perform(post("/api/v1/arriendos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(malo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details").isArray());
        }
        verifyNoInteractions(arriendoService);
    }

    @Test
    void fechaConFormatoInvalidoDa400() throws Exception {
        mvc.perform(post("/api/v1/arriendos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("2026-11-25", "25/11/2026")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(arriendoService);
    }

    @Test
    void reglasDeNegocioSeTraducenEnErroresJson() throws Exception {
        when(arriendoService.crear(any(), any()))
                .thenThrow(new BadRequestException("La fecha de inicio no puede ser anterior a hoy (2026-11-20)"))
                .thenThrow(new ResourceNotFoundException("Máquina no encontrada: 5"))
                .thenThrow(new ConflictException("No hay unidades disponibles de esta máquina"));
        mvc.perform(post("/api/v1/arriendos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("anterior a hoy")));
        mvc.perform(post("/api/v1/arriendos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/arriendos").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("No hay unidades disponibles de esta máquina"));
    }

    // ---------------------------------------------------------------- GET /arriendos/mis-arriendos
    @Test
    void misArriendosSoloCliente() throws Exception {
        when(arriendoService.misArriendos("cliente@ferrefix.cl")).thenReturn(List.of(arriendo(false)));
        mvc.perform(get("/api/v1/arriendos/mis-arriendos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/arriendos/mis-arriendos").with(ADMIN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/arriendos/mis-arriendos").with(CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("ARR-000077"));
    }

    // ---------------------------------------------------------------- GET /arriendos/{id}
    @Test
    void obtenerRequiereTokenYPasaQuienPideYSuRol() throws Exception {
        when(arriendoService.obtener(77L, "cliente@ferrefix.cl", false)).thenReturn(arriendo(false));
        when(arriendoService.obtener(77L, "admin@ferrefix.cl", true)).thenReturn(arriendo(true));

        mvc.perform(get("/api/v1/arriendos/77")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/arriendos/77").with(CLIENTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cliente").doesNotExist());
        mvc.perform(get("/api/v1/arriendos/77").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.rut").value("15432890-4"))
                .andExpect(jsonPath("$.cliente.email").value("cliente@ferrefix.cl"))
                .andExpect(jsonPath("$.cliente.passwordHash").doesNotExist());
    }

    @Test
    void obtenerAjenoOInexistenteDa404() throws Exception {
        when(arriendoService.obtener(eq(5L), any(), eq(false))).thenThrow(new ResourceNotFoundException("Arriendo no encontrado: 5"));
        mvc.perform(get("/api/v1/arriendos/5").with(CLIENTE)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void idNoNumericoDa400() throws Exception {
        mvc.perform(get("/api/v1/arriendos/abc").with(CLIENTE)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- GET /arriendos (ADMIN)
    @Test
    void listarSoloAdminConFiltros() throws Exception {
        when(arriendoService.listar("VIGENTE", 3L, 5L)).thenReturn(List.of(arriendo(true)));
        mvc.perform(get("/api/v1/arriendos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/arriendos").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/arriendos").with(ADMIN).param("estado", "VIGENTE").param("usuarioId", "3").param("maquinaId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cliente.nombre").value("Juan Maestro"));
    }

    @Test
    void listarConEstadoInvalidoDa400() throws Exception {
        when(arriendoService.listar(eq("CANCELADO"), any(), any())).thenThrow(new BadRequestException("Estado inválido"));
        mvc.perform(get("/api/v1/arriendos").with(ADMIN).param("estado", "CANCELADO")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- PUT /arriendos/{id} (ADMIN)
    private static final String PUT_BODY = """
            {"telefono":"+56 2 2222 2222","direccionFaena":"Nueva 456","region":"Valparaíso","comuna":"Viña del Mar"}""";

    @Test
    void editarSoloAdmin() throws Exception {
        when(arriendoService.actualizar(eq(77L), any())).thenReturn(arriendo(true));
        mvc.perform(put("/api/v1/arriendos/77").contentType(MediaType.APPLICATION_JSON).content(PUT_BODY)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/arriendos/77").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/arriendos/77").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(PUT_BODY)).andExpect(status().isOk());
    }

    @Test
    void editarConDatosInvalidosDa400YFinalizadoDa409() throws Exception {
        mvc.perform(put("/api/v1/arriendos/77").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(arriendoService.actualizar(eq(77L), any())).thenThrow(new ConflictException("Solo se pueden editar arriendos vigentes"));
        mvc.perform(put("/api/v1/arriendos/77").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(PUT_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void editarNoAceptaCambiarDiasFechasNiTotal() throws Exception {
        when(arriendoService.actualizar(eq(77L), any())).thenReturn(arriendo(true));
        mvc.perform(put("/api/v1/arriendos/77").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(PUT_BODY.replace("{", "{\"diasArriendo\":1,\"total\":1,\"tarifaDia\":1,\"fechaInicio\":\"2030-01-01\",\"unidadId\":9,")))
                .andExpect(status().isOk());
        // El DTO de edición solo tiene teléfono, dirección, región y comuna: lo demás ni siquiera llega al service.
        verify(arriendoService).actualizar(eq(77L), argThat(r -> r.telefono().equals("+56 2 2222 2222") && r.comuna().equals("Viña del Mar")));
    }

    // ---------------------------------------------------------------- PATCH /arriendos/{id}/devolver (ADMIN)
    @Test
    void devolverSoloAdmin() throws Exception {
        when(arriendoService.devolver(77L)).thenReturn(arriendo(true));
        mvc.perform(patch("/api/v1/arriendos/77/devolver")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/arriendos/77/devolver").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/arriendos/77/devolver").with(ADMIN)).andExpect(status().isOk());
        verify(arriendoService, times(1)).devolver(77L);
    }

    @Test
    void devolverDosVecesYInexistente() throws Exception {
        when(arriendoService.devolver(77L)).thenThrow(new ConflictException("El arriendo ya fue devuelto"));
        when(arriendoService.devolver(9L)).thenThrow(new ResourceNotFoundException("no"));
        mvc.perform(patch("/api/v1/arriendos/77/devolver").with(ADMIN)).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/arriendos/9/devolver").with(ADMIN)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- DELETE /arriendos/{id} (ADMIN)
    @Test
    void eliminarSoloAdmin() throws Exception {
        mvc.perform(delete("/api/v1/arriendos/77")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/arriendos/77").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/arriendos/77").with(ADMIN)).andExpect(status().isNoContent());
        verify(arriendoService, times(1)).eliminar(77L);
    }

    @Test
    void eliminarInexistenteDa404() throws Exception {
        doThrow(new ResourceNotFoundException("no")).when(arriendoService).eliminar(9L);
        mvc.perform(delete("/api/v1/arriendos/9").with(ADMIN)).andExpect(status().isNotFound());
    }
}
