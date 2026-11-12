package cl.ferrefix.service;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.Cotizacion;
import cl.ferrefix.model.EstadoCotizacion;
import cl.ferrefix.model.TipoProyecto;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.*;
import cl.ferrefix.model.mapper.CotizacionMapper;
import cl.ferrefix.repository.CotizacionRepository;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.impl.CotizacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CotizacionServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Santiago")); // 12:00 en Santiago

    @Mock CotizacionRepository cotizacionRepo;
    @Mock UsuarioRepository usuarioRepo;

    CotizacionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CotizacionServiceImpl(cotizacionRepo, usuarioRepo, new CotizacionMapper(), RELOJ);
    }

    // ---------------------------------------------------------------- datos
    private CrearCotizacionRequestDto request(String rut, String tipo, String detalle, Boolean despacho) {
        return new CrearCotizacionRequestDto(" Constructora Los Andes SpA ", rut, " Ventas@Los-Andes.CL ", " +56 9 8765 4321 ",
                tipo, " Región Metropolitana ", detalle, despacho);
    }

    private CrearCotizacionRequestDto requestValida() {
        return request("11.111.111-1", " otro requerimiento especial ", "  Necesito cotizar 200 sacos de cemento  ", null);
    }

    private void stubGuardar() {
        when(cotizacionRepo.save(any(Cotizacion.class))).thenAnswer(i -> {
            Cotizacion c = i.getArgument(0);
            c.setId(88L);
            return c;
        });
    }

    private Usuario usuario(boolean activo) {
        Usuario u = new Usuario();
        u.setId(3L);
        u.setEmail("cliente@ferrefix.cl");
        u.setActivo(activo);
        return u;
    }

    private Cotizacion cotizacion(long id, EstadoCotizacion estado) {
        Cotizacion c = new Cotizacion();
        c.setId(id);
        c.setNombre("Constructora");
        c.setRut("11111111-1");
        c.setEmail("a@b.cl");
        c.setTelefono("+56 9 1111 1111");
        c.setTipoProyecto(TipoProyecto.TODOS.get(0));
        c.setRegion("Región Metropolitana");
        c.setDetalle("Detalle de la solicitud");
        c.setEstado(estado);
        c.setNotaAdmin("nota previa");
        c.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        c.setUpdatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        return c;
    }

    // ---------------------------------------------------------------- crear: anónimo
    @Test
    void crearAnonimaNormalizaTodoYNaceDePendiente() {
        stubGuardar();

        CotizacionCreadaResponseDto r = service.crear(null, requestValida());

        ArgumentCaptor<Cotizacion> cap = ArgumentCaptor.forClass(Cotizacion.class);
        verify(cotizacionRepo).save(cap.capture());
        Cotizacion c = cap.getValue();
        assertNull(c.getUsuarioId());                                 // anónima
        assertEquals("Constructora Los Andes SpA", c.getNombre());     // recortado
        assertEquals("11111111-1", c.getRut());                        // normalizado (sin puntos)
        assertEquals("ventas@los-andes.cl", c.getEmail());             // minúscula y recortado
        assertEquals("+56 9 8765 4321", c.getTelefono());
        assertEquals("Otro Requerimiento Especial", c.getTipoProyecto());   // texto oficial
        assertEquals("Región Metropolitana", c.getRegion());
        assertEquals("Necesito cotizar 200 sacos de cemento", c.getDetalle());
        assertTrue(c.isDespachoObra(), "Sin despachoObra se asume true");
        assertEquals(EstadoCotizacion.PENDIENTE, c.getEstado());
        assertNull(c.getNotaAdmin());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), c.getCreatedAt());   // hora de Chile
        assertEquals(c.getCreatedAt(), c.getUpdatedAt());

        assertEquals(88L, r.id());
        assertEquals("COT-000088", r.codigo());
        assertEquals("PENDIENTE", r.estado());
        verifyNoInteractions(usuarioRepo);                              // sin token no se consulta ningún usuario
    }

    @Test
    void elAcuseNoDevuelveDatosPersonales() {
        stubGuardar();
        String json = service.crear(null, requestValida()).toString().toLowerCase();
        assertFalse(json.contains("11111111") || json.contains("ventas@") || json.contains("8765") || json.contains("cemento"));
    }

    @Test
    void despachoObraExplicitoSeRespeta() {
        stubGuardar();
        ArgumentCaptor<Cotizacion> cap = ArgumentCaptor.forClass(Cotizacion.class);
        service.crear(null, request("11111111-1", TipoProyecto.TODOS.get(0), "Detalle suficiente", false));
        service.crear(null, request("11111111-1", TipoProyecto.TODOS.get(0), "Detalle suficiente", true));
        verify(cotizacionRepo, times(2)).save(cap.capture());
        assertFalse(cap.getAllValues().get(0).isDespachoObra());
        assertTrue(cap.getAllValues().get(1).isDespachoObra());
    }

    // ---------------------------------------------------------------- crear: con token
    @Test
    void conTokenValidoSeAsociaElUsuarioSeaCualSeaSuRol() {
        stubGuardar();
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(usuario(true)));
        service.crear("cliente@ferrefix.cl", requestValida());
        ArgumentCaptor<Cotizacion> cap = ArgumentCaptor.forClass(Cotizacion.class);
        verify(cotizacionRepo).save(cap.capture());
        assertEquals(3L, cap.getValue().getUsuarioId());
    }

    @Test
    void siElUsuarioDelTokenYaNoExisteOEstaInactivoQuedaAnonimaSinFallar() {
        stubGuardar();
        when(usuarioRepo.findByEmailIgnoreCase("fantasma@x.cl")).thenReturn(Optional.empty());
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(usuario(false)));

        assertDoesNotThrow(() -> service.crear("fantasma@x.cl", requestValida()));
        assertDoesNotThrow(() -> service.crear("cliente@ferrefix.cl", requestValida()));

        ArgumentCaptor<Cotizacion> cap = ArgumentCaptor.forClass(Cotizacion.class);
        verify(cotizacionRepo, times(2)).save(cap.capture());
        assertTrue(cap.getAllValues().stream().allMatch(c -> c.getUsuarioId() == null));
    }

    // ---------------------------------------------------------------- crear: reglas
    @Test
    void rutInvalidoDa400SinGuardar() {
        for (String rut : new String[]{"11111111-2", "15.432.890-K", "hola", "12345", "1-9"}) {
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> service.crear(null, request(rut, TipoProyecto.TODOS.get(0), "Detalle suficiente", null)), rut);
            assertEquals("El RUT ingresado no es válido", ex.getMessage());
        }
        verify(cotizacionRepo, never()).save(any());
    }

    @Test
    void elRutAceptaPuntosYGuionYSeGuardaNormalizado() {
        stubGuardar();
        service.crear(null, request("6.000.000-k", TipoProyecto.TODOS.get(0), "Detalle suficiente", null));
        ArgumentCaptor<Cotizacion> cap = ArgumentCaptor.forClass(Cotizacion.class);
        verify(cotizacionRepo).save(cap.capture());
        assertEquals("6000000-K", cap.getValue().getRut());
    }

    @Test
    void tipoDeProyectoFueraDeLosSeisDa400ConLosValoresPermitidos() {
        for (String tipo : new String[]{"Todas", "Herramientas", "OTRO", "Arriendo de Maquinaria"}) {
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> service.crear(null, request("11111111-1", tipo, "Detalle suficiente", null)), tipo);
            assertTrue(ex.getMessage().contains("Otro Requerimiento Especial"));
        }
        verify(cotizacionRepo, never()).save(any());
    }

    @Test
    void cadaUnoDeLosSeisTiposSeAcepta() {
        stubGuardar();
        for (String tipo : TipoProyecto.TODOS) {
            assertDoesNotThrow(() -> service.crear(null, request("11111111-1", tipo, "Detalle suficiente", null)), tipo);
        }
        verify(cotizacionRepo, times(6)).save(any());
    }

    @Test
    void detalleCortoYaRecortadoDa400() {
        for (String detalle : new String[]{"corto", "   123456789   ", "         x         "}) {
            BadRequestException ex = assertThrows(BadRequestException.class,
                    () -> service.crear(null, request("11111111-1", TipoProyecto.TODOS.get(0), detalle, null)), detalle);
            assertTrue(ex.getMessage().contains("al menos 10 caracteres"));
        }
        verify(cotizacionRepo, never()).save(any());
    }

    @Test
    void nombreDeSoloEspaciosAlrededorQuedaBajoElMinimo() {
        CrearCotizacionRequestDto r = new CrearCotizacionRequestDto("  ab  ", "11111111-1", "a@b.cl", "+56 9 8765 4321",
                TipoProyecto.TODOS.get(0), "RM", "Detalle suficiente", null);
        assertThrows(BadRequestException.class, () -> service.crear(null, r));
        verify(cotizacionRepo, never()).save(any());
    }

    // ---------------------------------------------------------------- ADMIN: listar / obtener
    @Test
    void listarSinFiltroOrdenaDeLaMasNuevaAMasAntigua() {
        when(cotizacionRepo.findAll(any(Sort.class))).thenReturn(List.of(cotizacion(2, EstadoCotizacion.PENDIENTE), cotizacion(1, EstadoCotizacion.CERRADA)));
        List<CotizacionResponseDto> r = service.listar(null);
        assertEquals(List.of("COT-000002", "COT-000001"), r.stream().map(CotizacionResponseDto::codigo).toList());
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(cotizacionRepo).findAll(sort.capture());
        assertEquals(Sort.Direction.DESC, sort.getValue().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getValue().getOrderFor("id").getDirection());
    }

    @Test
    void listarConFiltroDeEstadoEnMinusculas() {
        when(cotizacionRepo.findByEstado(eq(EstadoCotizacion.EN_REVISION), any(Sort.class))).thenReturn(List.of(cotizacion(5, EstadoCotizacion.EN_REVISION)));
        List<CotizacionResponseDto> r = service.listar(" en_revision ");
        assertEquals(1, r.size());
        assertEquals("EN_REVISION", r.get(0).estado());
        assertEquals("11111111-1", r.get(0).rut());           // el ADMIN ve los datos completos
        verify(cotizacionRepo, never()).findAll(any(Sort.class));
    }

    @Test
    void listarConEstadoInvalidoDa400() {
        assertThrows(BadRequestException.class, () -> service.listar("RECHAZADA"));
        verifyNoInteractions(cotizacionRepo);
    }

    @Test
    void obtenerYObtenerInexistente() {
        when(cotizacionRepo.findById(5L)).thenReturn(Optional.of(cotizacion(5, EstadoCotizacion.PENDIENTE)));
        when(cotizacionRepo.findById(9L)).thenReturn(Optional.empty());
        assertEquals("COT-000005", service.obtener(5L).codigo());
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.obtener(9L));
        assertEquals("Cotización no encontrada: 9", ex.getMessage());
    }

    // ---------------------------------------------------------------- ADMIN: actualizar
    private CotizacionResponseDto patch(Cotizacion c, EstadoCotizacion estado, String nota) {
        when(cotizacionRepo.findById(c.getId())).thenReturn(Optional.of(c));
        when(cotizacionRepo.save(c)).thenReturn(c);
        return service.actualizar(c.getId(), new ActualizarCotizacionRequestDto(estado, nota));
    }

    @Test
    void soloEstadoNoTocaLaNota() {
        Cotizacion c = cotizacion(5, EstadoCotizacion.PENDIENTE);
        CotizacionResponseDto r = patch(c, EstadoCotizacion.EN_REVISION, null);
        assertEquals("EN_REVISION", r.estado());
        assertEquals("nota previa", r.notaAdmin());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), c.getUpdatedAt());
    }

    @Test
    void soloNotaNoTocaElEstado() {
        Cotizacion c = cotizacion(5, EstadoCotizacion.RESPONDIDA);
        CotizacionResponseDto r = patch(c, null, "  Se envió el presupuesto por correo  ");
        assertEquals("RESPONDIDA", r.estado());
        assertEquals("Se envió el presupuesto por correo", r.notaAdmin());
    }

    @Test
    void estadoYNotaJuntos() {
        CotizacionResponseDto r = patch(cotizacion(5, EstadoCotizacion.PENDIENTE), EstadoCotizacion.CERRADA, "Cliente no respondió");
        assertEquals("CERRADA", r.estado());
        assertEquals("Cliente no respondió", r.notaAdmin());
    }

    @Test
    void unaNotaVaciaOEnBlancoBorraLaNota() {
        assertNull(patch(cotizacion(5, EstadoCotizacion.PENDIENTE), null, "").notaAdmin());
        assertNull(patch(cotizacion(6, EstadoCotizacion.PENDIENTE), null, "    ").notaAdmin());
    }

    @Test
    void cualquierEstadoPuedePasarACualquierOtroIncluidoElMismo() {
        for (EstadoCotizacion origen : EstadoCotizacion.values()) {
            for (EstadoCotizacion destino : EstadoCotizacion.values()) {
                Cotizacion c = cotizacion(5, origen);
                assertEquals(destino.name(), patch(c, destino, null).estado(), origen + " -> " + destino);
            }
        }
    }

    @Test
    void sinEstadoNiNotaDa400() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.actualizar(5L, new ActualizarCotizacionRequestDto(null, null)));
        assertEquals("Debe enviar estado y/o notaAdmin", ex.getMessage());
        verifyNoInteractions(cotizacionRepo);
    }

    @Test
    void actualizarInexistenteDa404() {
        when(cotizacionRepo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.actualizar(9L, new ActualizarCotizacionRequestDto(EstadoCotizacion.CERRADA, null)));
        verify(cotizacionRepo, never()).save(any());
    }

    // ---------------------------------------------------------------- ADMIN: eliminar
    @Test
    void eliminarEnCualquierEstado() {
        for (EstadoCotizacion estado : EstadoCotizacion.values()) {
            Cotizacion c = cotizacion(5, estado);
            when(cotizacionRepo.findById(5L)).thenReturn(Optional.of(c));
            service.eliminar(5L);
            verify(cotizacionRepo).delete(c);
            clearInvocations(cotizacionRepo);
        }
    }

    @Test
    void eliminarInexistenteDa404() {
        when(cotizacionRepo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eliminar(9L));
        verify(cotizacionRepo, never()).delete(any(Cotizacion.class));
    }
}
