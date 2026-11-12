package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.EstadoOrden;
import cl.ferrefix.model.dto.CrearOrdenRequestDto;
import cl.ferrefix.model.dto.OrdenResponseDto;
import cl.ferrefix.security.*;
import cl.ferrefix.service.OrdenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrdenController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class OrdenControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrdenService ordenService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

    private static final RequestPostProcessor ADMIN = user("admin@ferrefix.cl").roles("ADMIN");
    private static final RequestPostProcessor CLIENTE = user("cliente@ferrefix.cl").roles("CLIENTE");

    private static final String BODY = """
            {"items":[{"productoId":3,"cantidad":2},{"productoId":5,"cantidad":1}],"metodoPago":"WEBPAY_DEBITO",
             "telefono":"+56 9 8765 4321","direccionEnvio":"Av. Providencia 1240","region":"Región Metropolitana",
             "comuna":"Providencia","indicaciones":"Dejar con conserje"}""";

    private static OrdenResponseDto orden(boolean conCliente) {
        return new OrdenResponseDto(501L, "ORD-000501", "BOL-000501", "PAGADA", "WEBPAY_DEBITO",
                List.of(new OrdenResponseDto.Item(3L, "SKU-3", "Taladro", 1000, 2, 2000L),
                        new OrdenResponseDto.Item(5L, "SKU-5", "Esmeril", 2500, 1, 2500L)),
                4500L, 3990, 8490L,
                new OrdenResponseDto.Envio("+56 9 8765 4321", "Av. Providencia 1240", "Región Metropolitana", "Providencia", "Dejar con conserje"),
                LocalDateTime.now(), LocalDateTime.now(),
                conCliente ? new OrdenResponseDto.Cliente(3L, "15432890-4", "Juan Maestro", "cliente@ferrefix.cl") : null);
    }

    // ---------------------------------------------------------------- POST /ordenes
    @Test
    void clienteCompraYRecibe201() throws Exception {
        when(ordenService.crear(eq("cliente@ferrefix.cl"), any())).thenReturn(orden(false));
        mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("ORD-000501"))
                .andExpect(jsonPath("$.boleta").value("BOL-000501"))
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.subtotal").value(4500))
                .andExpect(jsonPath("$.costoEnvio").value(3990))
                .andExpect(jsonPath("$.total").value(8490))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-3"))
                .andExpect(jsonPath("$.envio.comuna").value("Providencia"))
                .andExpect(jsonPath("$.cliente").doesNotExist());
    }

    @Test
    void adminNoPuedeComprarDa403Json() throws Exception {
        mvc.perform(post("/api/v1/ordenes").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/v1/ordenes").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());   // el rol se evalúa antes que la validación del body
        verifyNoInteractions(ordenService);
    }

    @Test
    void sinTokenDa401() throws Exception {
        mvc.perform(post("/api/v1/ordenes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(ordenService);
    }

    @Test
    void elCuerpoNoPuedeImponerPreciosTotalNiDatosDelCliente() throws Exception {
        when(ordenService.crear(any(), any())).thenReturn(orden(false));
        String conIntrusos = BODY
                .replace("{\"productoId\":3,\"cantidad\":2}", "{\"productoId\":3,\"cantidad\":2,\"precioUnitario\":1,\"subtotal\":1,\"nombre\":\"Gratis\"}")
                .replace("\"metodoPago\"", "\"total\":1,\"subtotal\":1,\"costoEnvio\":0,\"estado\":\"ENTREGADA\",\"rut\":\"1-9\",\"email\":\"otro@x.cl\",\"usuarioId\":99,\"metodoPago\"");
        mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(conIntrusos))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearOrdenRequestDto> dto = ArgumentCaptor.forClass(CrearOrdenRequestDto.class);
        verify(ordenService).crear(eq("cliente@ferrefix.cl"), dto.capture());   // el cliente es el del token
        assertEquals(2, dto.getValue().items().size());
        assertEquals(3L, dto.getValue().items().get(0).productoId());
        assertEquals(2, dto.getValue().items().get(0).cantidad());
    }

    @Test
    void datosInvalidosDan400ConDetalle() throws Exception {
        String[] malos = {
                "{}",
                BODY.replace("{\"productoId\":3,\"cantidad\":2},{\"productoId\":5,\"cantidad\":1}", ""),            // items vacío
                BODY.replace("\"items\":[{\"productoId\":3,\"cantidad\":2},{\"productoId\":5,\"cantidad\":1}],", ""), // sin items
                BODY.replace("\"cantidad\":2", "\"cantidad\":0"),
                BODY.replace("\"cantidad\":2", "\"cantidad\":1001"),
                BODY.replace("\"productoId\":3,", ""),
                BODY.replace("\"items\":[{\"productoId\":3,\"cantidad\":2},{\"productoId\":5,\"cantidad\":1}]", "\"items\":[null]"),
                BODY.replace("+56 9 8765 4321", "abc"),
                BODY.replace("Av. Providencia 1240", ""),
                BODY.replace("\"comuna\":\"Providencia\"", "\"comuna\":\"\""),
                BODY.replace("Dejar con conserje", "x".repeat(501))
        };
        for (String malo : malos) {
            mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(malo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details").isArray());
        }
        verifyNoInteractions(ordenService);
    }

    @Test
    void masDeCincuentaLineasDa400() throws Exception {
        StringBuilder lineas = new StringBuilder();
        for (int i = 1; i <= 51; i++) {
            lineas.append(i > 1 ? "," : "").append("{\"productoId\":").append(i).append(",\"cantidad\":1}");
        }
        String body = BODY.replace("{\"productoId\":3,\"cantidad\":2},{\"productoId\":5,\"cantidad\":1}", lineas.toString());
        mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ordenService);
    }

    @Test
    void metodoDePagoInexistenteDa400() throws Exception {
        mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("WEBPAY_DEBITO", "BITCOIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ordenService);
    }

    @Test
    void erroresDeNegocioSeTraducenEnJson() throws Exception {
        when(ordenService.crear(any(), any()))
                .thenThrow(new BadRequestException("El producto 3 está repetido en la orden"))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado: [77]"))
                .thenThrow(new ConflictException("No hay stock suficiente para completar la compra. No se descontó nada",
                        List.of("Esmeril (SKU SKU-5): agotado", "Taladro (SKU SKU-3): disponibles 1, solicitaste 9")));
        mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No hay stock suficiente")))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0]").value("Esmeril (SKU SKU-5): agotado"));
    }

    @Test
    void elTimeoutDeConsultaOElBloqueoOcupadoDan409Reintente() throws Exception {
        when(ordenService.crear(any(), any()))
                .thenThrow(new QueryTimeoutException("ORA-01013: el usuario solicitó cancelar la operación"))
                .thenThrow(new CannotAcquireLockException("lock timeout"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/ordenes").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Intente nuevamente")));
        }
    }

    // ---------------------------------------------------------------- GET /ordenes/mis-ordenes
    @Test
    void misOrdenesSoloCliente() throws Exception {
        when(ordenService.misOrdenes("cliente@ferrefix.cl")).thenReturn(List.of(orden(false)));
        mvc.perform(get("/api/v1/ordenes/mis-ordenes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ordenes/mis-ordenes").with(ADMIN)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ordenes/mis-ordenes").with(CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("ORD-000501"));
    }

    // ---------------------------------------------------------------- GET /ordenes/{id}
    @Test
    void obtenerRequiereTokenYPasaQuienPideYSuRol() throws Exception {
        when(ordenService.obtener(501L, "cliente@ferrefix.cl", false)).thenReturn(orden(false));
        when(ordenService.obtener(501L, "admin@ferrefix.cl", true)).thenReturn(orden(true));

        mvc.perform(get("/api/v1/ordenes/501")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ordenes/501").with(CLIENTE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cliente").doesNotExist());
        mvc.perform(get("/api/v1/ordenes/501").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.rut").value("15432890-4"))
                .andExpect(jsonPath("$.cliente.passwordHash").doesNotExist());
    }

    @Test
    void obtenerAjenaOInexistenteDa404YIdNoNumericoDa400() throws Exception {
        when(ordenService.obtener(eq(7L), any(), eq(false))).thenThrow(new ResourceNotFoundException("Orden no encontrada: 7"));
        mvc.perform(get("/api/v1/ordenes/7").with(CLIENTE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ordenes/abc").with(CLIENTE)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- GET /ordenes (ADMIN)
    @Test
    void listarSoloAdminConFiltros() throws Exception {
        when(ordenService.listar("PAGADA", 3L)).thenReturn(List.of(orden(true)));
        mvc.perform(get("/api/v1/ordenes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ordenes").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ordenes").with(ADMIN).param("estado", "PAGADA").param("usuarioId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cliente.nombre").value("Juan Maestro"));
    }

    @Test
    void listarConEstadoInvalidoDa400() throws Exception {
        when(ordenService.listar(eq("COMPLETADA"), any())).thenThrow(new BadRequestException("Estado inválido"));
        mvc.perform(get("/api/v1/ordenes").with(ADMIN).param("estado", "COMPLETADA")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- PATCH /ordenes/{id}/estado (ADMIN)
    @Test
    void cambiarEstadoSoloAdmin() throws Exception {
        when(ordenService.cambiarEstado(501L, EstadoOrden.CANCELADA)).thenReturn(orden(true));
        mvc.perform(patch("/api/v1/ordenes/501/estado").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CANCELADA\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/ordenes/501/estado").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/ordenes/501/estado").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CANCELADA\"}"))
                .andExpect(status().isOk());
        verify(ordenService, times(1)).cambiarEstado(501L, EstadoOrden.CANCELADA);
    }

    @Test
    void cambiarEstadoConDatosInvalidos() throws Exception {
        mvc.perform(patch("/api/v1/ordenes/501/estado").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").isArray());
        mvc.perform(patch("/api/v1/ordenes/501/estado").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"COMPLETADA\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ordenService);
    }

    @Test
    void transicionNoPermitidaDa409YOrdenInexistenteDa404() throws Exception {
        when(ordenService.cambiarEstado(501L, EstadoOrden.ENTREGADA)).thenThrow(new ConflictException("Transición no permitida: PAGADA → ENTREGADA"));
        when(ordenService.cambiarEstado(9L, EstadoOrden.CANCELADA)).thenThrow(new ResourceNotFoundException("no"));
        mvc.perform(patch("/api/v1/ordenes/501/estado").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENTREGADA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Transición no permitida: PAGADA → ENTREGADA"));
        mvc.perform(patch("/api/v1/ordenes/9/estado").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CANCELADA\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- DELETE /ordenes/{id} (ADMIN)
    @Test
    void eliminarSoloAdmin() throws Exception {
        mvc.perform(delete("/api/v1/ordenes/501")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/ordenes/501").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/ordenes/501").with(ADMIN)).andExpect(status().isNoContent());
        verify(ordenService, times(1)).eliminar(501L);
    }

    @Test
    void eliminarEnCursoDa409YInexistenteDa404() throws Exception {
        doThrow(new ConflictException("Solo se pueden eliminar órdenes ENTREGADA o CANCELADA")).when(ordenService).eliminar(501L);
        doThrow(new ResourceNotFoundException("no")).when(ordenService).eliminar(9L);
        mvc.perform(delete("/api/v1/ordenes/501").with(ADMIN)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/ordenes/9").with(ADMIN)).andExpect(status().isNotFound());
    }
}
