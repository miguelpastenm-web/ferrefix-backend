package cl.ferrefix.controller;

import cl.ferrefix.config.SecurityConfig;
import cl.ferrefix.controller.handler.ApiExceptionHandler;
import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.EstadoCotizacion;
import cl.ferrefix.model.dto.ActualizarCotizacionRequestDto;
import cl.ferrefix.model.dto.CotizacionCreadaResponseDto;
import cl.ferrefix.model.dto.CotizacionResponseDto;
import cl.ferrefix.model.dto.CrearCotizacionRequestDto;
import cl.ferrefix.security.*;
import cl.ferrefix.service.CotizacionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * El filtro JWT es el REAL (solo el verificador está simulado): así los casos de token cubren el camino completo
 * filtro -> SecurityContext -> controller, con la identidad armada a partir de los claims del token.
 */
@WebMvcTest(CotizacionController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, SecurityErrorWriter.class})
class CotizacionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CotizacionService cotizacionService;
    @MockitoBean JwtVerificador jwtVerificador;

    private static final RequestPostProcessor ADMIN = TokensDePrueba.como(TokensDePrueba.ADMIN);
    private static final RequestPostProcessor CLIENTE = TokensDePrueba.como(TokensDePrueba.CLIENTE);

    private static final String BODY = """
            {"nombre":"Constructora Los Andes SpA","rut":"11.111.111-1","email":"ventas@los-andes.cl",
             "telefono":"+56 9 8765 4321","tipoProyecto":"Materiales de Obra Gruesa","region":"Región Metropolitana",
             "detalle":"Necesito cotizar 200 sacos de cemento","despachoObra":true}""";

    private static CotizacionCreadaResponseDto creada() {
        return new CotizacionCreadaResponseDto(88L, "COT-000088", "PENDIENTE", LocalDateTime.now());
    }

    private static CotizacionResponseDto completa() {
        return new CotizacionResponseDto(88L, "COT-000088", 3L, "Constructora Los Andes SpA", "11111111-1", "ventas@los-andes.cl",
                "+56 9 8765 4321", "Materiales de Obra Gruesa", "Región Metropolitana", "Necesito cotizar 200 sacos de cemento",
                true, "PENDIENTE", null, LocalDateTime.now(), LocalDateTime.now());
    }

    /** Hace que el verificador acepte "TOKEN-VALIDO" como access token del cliente de prueba (id 3). */
    private void tokenValidoDeCliente() {
        when(jwtVerificador.validarAccessToken("TOKEN-VALIDO")).thenReturn(TokensDePrueba.claims(TokensDePrueba.CLIENTE));
    }

    // ---------------------------------------------------------------- POST público: token
    @Test
    void anonimoPuedeCrearYLaRespuestaEsSoloUnAcuse() throws Exception {
        when(cotizacionService.crear(isNull(), any())).thenReturn(creada());
        mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("COT-000088"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.rut").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.detalle").doesNotExist());
        verify(cotizacionService).crear(isNull(), any());
    }

    @Test
    void tokenValidoConFiltroRealAsociaAlUsuario() throws Exception {
        tokenValidoDeCliente();
        when(cotizacionService.crear(eq(3L), any())).thenReturn(creada());

        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", "Bearer TOKEN-VALIDO")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(cotizacionService).crear(eq(3L), any());   // llegó el id de usuario del token, no null
        verify(jwtVerificador).validarAccessToken("TOKEN-VALIDO");
    }

    @Test
    void tokenInvalidoSeTrataComoAnonimoSin401() throws Exception {
        when(jwtVerificador.validarAccessToken("BASURA")).thenThrow(new MalformedJwtException("token mal formado"));
        when(cotizacionService.crear(isNull(), any())).thenReturn(creada());

        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", "Bearer BASURA")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(jwtVerificador).validarAccessToken("BASURA");          // prueba que el filtro SÍ se ejecutó y rechazó el token
        verify(cotizacionService).crear(isNull(), any());
    }

    @Test
    void tokenVencidoSeTrataComoAnonimoSin401() throws Exception {
        when(jwtVerificador.validarAccessToken("TOKEN-VENCIDO")).thenThrow(new ExpiredJwtException(null, null, "JWT expired"));
        when(cotizacionService.crear(isNull(), any())).thenReturn(creada());

        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", "Bearer TOKEN-VENCIDO")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(jwtVerificador).validarAccessToken("TOKEN-VENCIDO");   // el filtro se ejecutó y el token estaba vencido
        verify(cotizacionService).crear(isNull(), any());
    }

    @Test
    void tokenFirmadoPeroSinIdentidadCompletaTambienQuedaAnonimo() throws Exception {
        // Token con firma válida pero sin uid, rut ni nombre (emitido antes de que existieran esos claims): el filtro
        // no puede armar la identidad, así que la cotización se registra sin usuario en vez de fallar.
        Claims claims = io.jsonwebtoken.Jwts.claims().subject("antiguo@ferrefix.cl").add("type", "access").build();
        when(jwtVerificador.validarAccessToken("TOKEN-HUERFANO")).thenReturn(claims);
        when(cotizacionService.crear(isNull(), any())).thenReturn(creada());

        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", "Bearer TOKEN-HUERFANO")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(jwtVerificador).validarAccessToken("TOKEN-HUERFANO");   // la firma se aceptó, pero faltaba la identidad
        verify(cotizacionService).crear(isNull(), any());
    }

    @Test
    void unRefreshTokenNoSirveParaAsociarYSeTrataComoAnonimo() throws Exception {
        when(jwtVerificador.validarAccessToken("REFRESH")).thenThrow(new io.jsonwebtoken.JwtException("Tipo de token inválido"));
        when(cotizacionService.crear(isNull(), any())).thenReturn(creada());
        mvc.perform(post("/api/v1/cotizaciones").header("Authorization", "Bearer REFRESH")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(jwtVerificador).validarAccessToken("REFRESH");         // el filtro lo intentó validar como access token y lo rechazó
        verify(cotizacionService).crear(isNull(), any());
    }

    @Test
    void clienteYAdminTambienPuedenCrearYSeAsocian() throws Exception {
        when(cotizacionService.crear(any(), any())).thenReturn(creada());
        mvc.perform(post("/api/v1/cotizaciones").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/cotizaciones").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        verify(cotizacionService).crear(eq(3L), any());   // id del cliente, tomado de su token
        verify(cotizacionService).crear(eq(1L), any());   // id del admin
    }

    // ---------------------------------------------------------------- POST público: validación
    @Test
    void elCuerpoNoPuedeFijarEstadoNotaNiUsuario() throws Exception {
        when(cotizacionService.crear(any(), any())).thenReturn(creada());
        String conIntrusos = BODY.replace("\"nombre\"", "\"id\":5,\"estado\":\"CERRADA\",\"notaAdmin\":\"hackeada\",\"usuarioId\":99,\"nombre\"");
        mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content(conIntrusos))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearCotizacionRequestDto> dto = ArgumentCaptor.forClass(CrearCotizacionRequestDto.class);
        verify(cotizacionService).crear(isNull(), dto.capture());     // sigue anónima: usuarioId=99 no se tomó del body
        assertEquals("Constructora Los Andes SpA", dto.getValue().nombre());
        assertEquals("Materiales de Obra Gruesa", dto.getValue().tipoProyecto());
        assertEquals(Boolean.TRUE, dto.getValue().despachoObra());
    }

    @Test
    void camposInvalidosDan400ConDetalleYNoLlegaAlService() throws Exception {
        String[] malos = {
                "{}",
                BODY.replace("Constructora Los Andes SpA", "ab"),                      // < 3
                BODY.replace("Constructora Los Andes SpA", "x".repeat(81)),            // > 80
                BODY.replace("Constructora Los Andes SpA", "Empresa <script>"),        // caracteres no permitidos
                BODY.replace("Constructora Los Andes SpA", "123 456"),                 // sin letras
                BODY.replace("11.111.111-1", "1".repeat(13)),                          // RUT > 12
                BODY.replace("11.111.111-1", ""),
                BODY.replace("ventas@los-andes.cl", "no-es-un-correo"),
                BODY.replace("ventas@los-andes.cl", "a".repeat(116) + "@x.cl"),        // > 120
                BODY.replace("+56 9 8765 4321", "abc"),
                BODY.replace("+56 9 8765 4321", "1".repeat(21)),                       // > 20
                BODY.replace("Materiales de Obra Gruesa", ""),
                BODY.replace("Materiales de Obra Gruesa", "x".repeat(61)),             // > 60
                BODY.replace("Región Metropolitana", ""),
                BODY.replace("Región Metropolitana", "x".repeat(81)),                  // > 80
                BODY.replace("Necesito cotizar 200 sacos de cemento", ""),
                BODY.replace("Necesito cotizar 200 sacos de cemento", "x".repeat(2001)) // > 2000
        };
        for (String malo : malos) {
            mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content(malo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.details").isArray());
        }
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void loQueDependeDelContenidoRecortadoLoRechazaElServiceConMensajeClaro() throws Exception {
        when(cotizacionService.crear(any(), any()))
                .thenThrow(new BadRequestException("El RUT ingresado no es válido"))
                .thenThrow(new BadRequestException("Tipo de proyecto inválido. Valores permitidos: ..."))
                .thenThrow(new BadRequestException("Describe tu requerimiento con al menos 10 caracteres"));
        for (String mensaje : new String[]{"El RUT ingresado no es válido", "Tipo de proyecto inválido", "al menos 10 caracteres"}) {
            mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(mensaje)));
        }
    }

    @Test
    void jsonMalFormadoYContentTypeIncorrecto() throws Exception {
        mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.APPLICATION_JSON).content("{no es json")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/cotizaciones").contentType(MediaType.TEXT_PLAIN).content("hola")).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(cotizacionService);
    }

    // ---------------------------------------------------------------- lectura: anónimo y CLIENTE no
    @Test
    void anonimoNoPuedeLeerNiModificarNada() throws Exception {
        mvc.perform(get("/api/v1/cotizaciones")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/v1/cotizaciones/88")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/cotizaciones/88").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CERRADA\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/cotizaciones/88")).andExpect(status().isUnauthorized());
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void clienteNoLeeNiModificaNada_ni_conCuerpoVacio() throws Exception {
        mvc.perform(get("/api/v1/cotizaciones").with(CLIENTE)).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/cotizaciones/88").with(CLIENTE)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/cotizaciones/88").with(CLIENTE).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/cotizaciones/88").with(CLIENTE)).andExpect(status().isForbidden());
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void noExisteMisCotizaciones() throws Exception {
        mvc.perform(get("/api/v1/cotizaciones/mis-cotizaciones").with(CLIENTE)).andExpect(status().isForbidden());   // cae en la regla ADMIN
        verifyNoInteractions(cotizacionService);
    }

    // ---------------------------------------------------------------- ADMIN
    @Test
    void adminListaConFiltroDeEstado() throws Exception {
        when(cotizacionService.listar("PENDIENTE")).thenReturn(List.of(completa()));
        mvc.perform(get("/api/v1/cotizaciones").with(ADMIN).param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("COT-000088"))
                .andExpect(jsonPath("$[0].rut").value("11111111-1"))
                .andExpect(jsonPath("$[0].usuarioId").value(3))
                .andExpect(jsonPath("$[0].despachoObra").value(true));
    }

    @Test
    void listarConEstadoInvalidoDa400() throws Exception {
        when(cotizacionService.listar("RECHAZADA")).thenThrow(new BadRequestException("Estado inválido"));
        mvc.perform(get("/api/v1/cotizaciones").with(ADMIN).param("estado", "RECHAZADA")).andExpect(status().isBadRequest());
    }

    @Test
    void adminVeUnaCotizacionYUnaInexistenteDa404() throws Exception {
        when(cotizacionService.obtener(88L)).thenReturn(completa());
        when(cotizacionService.obtener(9L)).thenThrow(new ResourceNotFoundException("Cotización no encontrada: 9"));
        mvc.perform(get("/api/v1/cotizaciones/88").with(ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ventas@los-andes.cl"));
        mvc.perform(get("/api/v1/cotizaciones/9").with(ADMIN)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/v1/cotizaciones/abc").with(ADMIN)).andExpect(status().isBadRequest());
    }

    @Test
    void adminCambiaEstadoYNota() throws Exception {
        when(cotizacionService.actualizar(eq(88L), any())).thenReturn(completa());
        mvc.perform(patch("/api/v1/cotizaciones/88").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RESPONDIDA\",\"notaAdmin\":\"Se envió el presupuesto\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/cotizaciones/88").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"notaAdmin\":\"\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ActualizarCotizacionRequestDto> dto = ArgumentCaptor.forClass(ActualizarCotizacionRequestDto.class);
        verify(cotizacionService, times(2)).actualizar(eq(88L), dto.capture());
        assertEquals(EstadoCotizacion.RESPONDIDA, dto.getAllValues().get(0).estado());
        assertEquals("", dto.getAllValues().get(1).notaAdmin());     // "" llega tal cual: el service la interpreta como borrar
    }

    @Test
    void patchInvalido() throws Exception {
        mvc.perform(patch("/api/v1/cotizaciones/88").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RECHAZADA\"}")).andExpect(status().isBadRequest());                     // enum inexistente
        mvc.perform(patch("/api/v1/cotizaciones/88").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notaAdmin\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details").isArray());                           // > 1000

        when(cotizacionService.actualizar(eq(88L), any())).thenThrow(new BadRequestException("Debe enviar estado y/o notaAdmin"));
        mvc.perform(patch("/api/v1/cotizaciones/88").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Debe enviar estado y/o notaAdmin"));

        when(cotizacionService.actualizar(eq(9L), any())).thenThrow(new ResourceNotFoundException("no"));
        mvc.perform(patch("/api/v1/cotizaciones/9").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CERRADA\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEliminaYUnaInexistenteDa404() throws Exception {
        doThrow(new ResourceNotFoundException("no")).when(cotizacionService).eliminar(9L);
        mvc.perform(delete("/api/v1/cotizaciones/88").with(ADMIN)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/cotizaciones/9").with(ADMIN)).andExpect(status().isNotFound());
        verify(cotizacionService).eliminar(88L);
    }
}
