package cl.ferrefix.service;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.*;
import cl.ferrefix.model.dto.ActualizarArriendoRequestDto;
import cl.ferrefix.model.dto.ArriendoResponseDto;
import cl.ferrefix.model.dto.CrearArriendoRequestDto;
import cl.ferrefix.model.mapper.ArriendoMapper;
import cl.ferrefix.repository.ArriendoRepository;
import cl.ferrefix.repository.MaquinaRepository;
import cl.ferrefix.repository.MaquinaUnidadRepository;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.impl.ArriendoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArriendoServiceTest {

    static final ZoneId CHILE = ZoneId.of("America/Santiago");
    /** 2026-11-20 12:00 en Santiago (UTC-3 en noviembre). */
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), CHILE);
    static final LocalDate HOY = LocalDate.of(2026, 11, 20);

    @Mock ArriendoRepository arriendoRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock MaquinaUnidadRepository unidadRepo;
    @Mock MaquinaRepository maquinaRepo;
    @Spy ArriendoMapper mapper = new ArriendoMapper();

    ArriendoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = servicioCon(RELOJ);
    }

    private ArriendoServiceImpl servicioCon(Clock clock) {
        return new ArriendoServiceImpl(arriendoRepo, usuarioRepo, unidadRepo,
                new MaquinaDisponibilidad(maquinaRepo, unidadRepo), mapper, clock);
    }

    // ---------------------------------------------------------------- datos de prueba
    private Usuario cliente() {
        Usuario u = new Usuario();
        u.setId(3L);
        u.setRut("15432890-4");
        u.setNombre("Juan Maestro");
        u.setEmail("cliente@ferrefix.cl");
        u.setActivo(true);
        return u;
    }

    private Maquina maquina() {
        Maquina m = new Maquina();
        m.setId(5L);
        m.setCodigo("MAQ-BET-02");
        m.setNombre("Betonera");
        m.setMarca("Ingco");
        m.setCategoria("Hormigón");
        m.setTarifaDia(18990);
        m.setEstado(EstadoMaquina.DISPONIBLE);
        return m;
    }

    private MaquinaUnidad unidad(Maquina m, long id, String codigo, EstadoUnidad estado) {
        MaquinaUnidad u = new MaquinaUnidad();
        u.setId(id);
        u.setMaquina(m);
        u.setCodigo(codigo);
        u.setEstado(estado);
        return u;
    }

    private CrearArriendoRequestDto request(int dias, LocalDate inicio) {
        return new CrearArriendoRequestDto(5L, dias, inicio, " +56 9 8765 4321 ", " Av. Faena 123 ", " Región Metropolitana ", " Providencia ");
    }

    private Arriendo arriendo(long id, Maquina m, MaquinaUnidad u, EstadoArriendo estado) {
        Arriendo a = new Arriendo();
        a.setId(id);
        a.setUnidad(u);
        a.setUsuario(cliente());
        a.setTarifaDia(18990);
        a.setDias(5);
        a.setFechaInicio(HOY);
        a.setFechaFin(HOY.plusDays(4));
        a.setTotal(94950L);
        a.setTelefonoContacto("+56 9 1111 1111");
        a.setDireccionFaena("Dirección vieja");
        a.setRegionFaena("Región vieja");
        a.setComunaFaena("Comuna vieja");
        a.setEstado(estado);
        if (estado == EstadoArriendo.FINALIZADO) {
            a.setFechaDevolucion(LocalDateTime.of(2026, 11, 1, 9, 0));
        }
        return a;
    }

    /** Stubs de un arriendo exitoso de la unidad 11 (hay otra libre, la 12). Devuelve el modelo bloqueado. */
    private Maquina stubsDeCreacionExitosa() {
        return stubsDeCreacionExitosa(1L);
    }

    /** @param libresTrasArrendar cuántas unidades quedan DISPONIBLES después de arrendar (decide el estado del modelo). */
    private Maquina stubsDeCreacionExitosa(long libresTrasArrendar) {
        Maquina m = maquina();
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(cliente()));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(unidadRepo.idsPorEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(List.of(11L, 12L));
        when(unidadRepo.cambiarEstado(11L, EstadoUnidad.DISPONIBLE, EstadoUnidad.ARRENDADA)).thenReturn(1);
        when(unidadRepo.findById(11L)).thenReturn(Optional.of(unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA)));
        when(arriendoRepo.save(any(Arriendo.class))).thenAnswer(i -> {
            Arriendo a = i.getArgument(0);
            a.setId(77L);
            return a;
        });
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(libresTrasArrendar);
        return m;
    }

    // ---------------------------------------------------------------- crear: caso feliz
    @Test
    void crearCalculaTarifaTotalYFinEnElServidor() {
        stubsDeCreacionExitosa();

        ArriendoResponseDto r = service.crear("cliente@ferrefix.cl", request(5, HOY.plusDays(2)));

        ArgumentCaptor<Arriendo> cap = ArgumentCaptor.forClass(Arriendo.class);
        verify(arriendoRepo).save(cap.capture());
        Arriendo a = cap.getValue();
        assertEquals(18990, a.getTarifaDia());                          // copiada del modelo
        assertEquals(94950L, a.getTotal());                             // 18990 x 5
        assertEquals(HOY.plusDays(2), a.getFechaInicio());
        assertEquals(HOY.plusDays(6), a.getFechaFin());                 // inicio + 5 - 1
        assertEquals(EstadoArriendo.VIGENTE, a.getEstado());
        assertNull(a.getFechaDevolucion());
        assertEquals("+56 9 8765 4321", a.getTelefonoContacto());       // recortado
        assertEquals("Providencia", a.getComunaFaena());
        assertEquals(3L, a.getUsuario().getId());                       // el cliente sale del token, no del body
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), a.getCreatedAt());  // hora de Chile

        assertEquals("ARR-000077", r.codigo());
        assertEquals("VIGENTE", r.estado());
        assertEquals("MAQ-BET-02-U01", r.unidadCodigo());
        assertEquals("MAQ-BET-02", r.maquina().codigo());
        assertEquals(94950L, r.total());
        assertNull(r.cliente(), "El cliente no necesita ver sus propios datos de identidad en la respuesta");
    }

    @Test
    void crearUnDiaTerminaElMismoDiaQueEmpieza() {
        stubsDeCreacionExitosa();
        ArriendoResponseDto r = service.crear("cliente@ferrefix.cl", request(1, HOY));
        assertEquals(r.fechaInicio(), r.fechaFin());
        assertEquals(18990L, r.total());
    }

    @Test
    void crearSigueElOrdenBloqueoUpdateAtomicoRecalculo() {
        Maquina m = stubsDeCreacionExitosa(1L);

        service.crear("cliente@ferrefix.cl", request(3, HOY));

        InOrder orden = inOrder(maquinaRepo, unidadRepo, arriendoRepo);
        orden.verify(maquinaRepo).findByIdForUpdate(5L);                                              // 1) bloquear modelo
        orden.verify(unidadRepo).idsPorEstado(5L, EstadoUnidad.DISPONIBLE);
        orden.verify(unidadRepo).cambiarEstado(11L, EstadoUnidad.DISPONIBLE, EstadoUnidad.ARRENDADA);  // 2) UPDATE atómico
        orden.verify(arriendoRepo).save(any(Arriendo.class));
        orden.verify(unidadRepo).countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE);              // 3) recalcular
        assertEquals(EstadoMaquina.DISPONIBLE, m.getEstado());                                         // queda la unidad 12
    }

    @Test
    void crearConLaUltimaUnidadDejaElModeloNoDisponible() {
        Maquina m = stubsDeCreacionExitosa(0L);
        service.crear("cliente@ferrefix.cl", request(3, HOY));
        assertEquals(EstadoMaquina.NO_DISPONIBLE, m.getEstado());
    }

    // ---------------------------------------------------------------- crear: concurrencia / disponibilidad
    @Test
    void crearSaltaUnidadesQueOtroSeLlevoPrimero() {
        Maquina m = maquina();
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(unidadRepo.idsPorEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(List.of(11L, 12L));
        when(unidadRepo.cambiarEstado(11L, EstadoUnidad.DISPONIBLE, EstadoUnidad.ARRENDADA)).thenReturn(0); // se la ganaron
        when(unidadRepo.cambiarEstado(12L, EstadoUnidad.DISPONIBLE, EstadoUnidad.ARRENDADA)).thenReturn(1);
        when(unidadRepo.findById(12L)).thenReturn(Optional.of(unidad(m, 12, "MAQ-BET-02-U02", EstadoUnidad.ARRENDADA)));
        when(arriendoRepo.save(any(Arriendo.class))).thenAnswer(i -> i.getArgument(0));
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(0L);

        ArriendoResponseDto r = service.crear("cliente@ferrefix.cl", request(2, HOY));

        assertEquals("MAQ-BET-02-U02", r.unidadCodigo());
    }

    @Test
    void crearSinNingunaUnidadLibreDa409YNoGuarda() {
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(maquina()));
        when(unidadRepo.idsPorEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(List.of());

        ConflictException ex = assertThrows(ConflictException.class, () -> service.crear("cliente@ferrefix.cl", request(2, HOY)));

        assertTrue(ex.getMessage().contains("No hay unidades disponibles"));
        verify(arriendoRepo, never()).save(any());
    }

    @Test
    void crearSiTodosLosUpdateAtomicosAfectanCeroFilasDa409() {
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(maquina()));
        when(unidadRepo.idsPorEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(List.of(11L, 12L));
        when(unidadRepo.cambiarEstado(anyLong(), eq(EstadoUnidad.DISPONIBLE), eq(EstadoUnidad.ARRENDADA))).thenReturn(0);

        assertThrows(ConflictException.class, () -> service.crear("cliente@ferrefix.cl", request(2, HOY)));

        verify(unidadRepo, times(2)).cambiarEstado(anyLong(), any(), any());
        verify(arriendoRepo, never()).save(any());
    }

    @Test
    void crearMaquinaInexistenteDa404() {
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.crear("cliente@ferrefix.cl", request(2, HOY)));
        verifyNoInteractions(unidadRepo, arriendoRepo);
    }

    @Test
    void crearConUsuarioInexistenteOInactivoDa401SinBloquear() {
        when(usuarioRepo.findByEmailIgnoreCase("fantasma@x.cl")).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> service.crear("fantasma@x.cl", request(2, HOY)));

        Usuario inactivo = cliente();
        inactivo.setActivo(false);
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(inactivo));
        assertThrows(BadCredentialsException.class, () -> service.crear("cliente@ferrefix.cl", request(2, HOY)));

        verifyNoInteractions(maquinaRepo, unidadRepo, arriendoRepo);
    }

    // ---------------------------------------------------------------- crear: días y fechas
    @Test
    void crearConDiasFueraDeRangoDa400SinTocarLaBase() {
        for (int dias : new int[]{0, -1, 91, 1000}) {
            assertThrows(BadRequestException.class, () -> service.crear("cliente@ferrefix.cl", request(dias, HOY)), "dias=" + dias);
        }
        verifyNoInteractions(usuarioRepo, maquinaRepo, unidadRepo, arriendoRepo);
    }

    @Test
    void crearAceptaLosExtremosDeDias() {
        stubsDeCreacionExitosa();
        assertEquals(90L * 18990, service.crear("cliente@ferrefix.cl", request(90, HOY)).total());
    }

    @Test
    void crearConFechaPasadaDa400() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.crear("cliente@ferrefix.cl", request(3, HOY.minusDays(1))));
        assertTrue(ex.getMessage().contains("anterior a hoy"));
        verifyNoInteractions(usuarioRepo, maquinaRepo, unidadRepo, arriendoRepo);
    }

    @Test
    void crearConFechaMuyLejanaDa400() {
        assertThrows(BadRequestException.class, () -> service.crear("cliente@ferrefix.cl", request(3, HOY.plusDays(366))));
        verifyNoInteractions(maquinaRepo);
    }

    @Test
    void hoyEsHoraDeSantiagoNoLaDelContenedor() {
        // 2026-11-21T01:00Z = 22:00 del 20-nov en Santiago. En UTC ya sería 21-nov y "20-nov" se vería como pasado.
        service = servicioCon(Clock.fixed(Instant.parse("2026-11-21T01:00:00Z"), CHILE));
        stubsDeCreacionExitosa();
        assertDoesNotThrow(() -> service.crear("cliente@ferrefix.cl", request(2, LocalDate.of(2026, 11, 20))));
    }

    @Test
    void hoyEsHoraDeSantiagoTambienAlOtroLadoDelCambioDeDia() {
        // 2026-11-20T02:00Z = 23:00 del 19-nov en Santiago. En UTC sería 20-nov y "19-nov" se rechazaría por error.
        service = servicioCon(Clock.fixed(Instant.parse("2026-11-20T02:00:00Z"), CHILE));
        stubsDeCreacionExitosa();
        assertDoesNotThrow(() -> service.crear("cliente@ferrefix.cl", request(2, LocalDate.of(2026, 11, 19))));
        assertThrows(BadRequestException.class, () -> service.crear("cliente@ferrefix.cl", request(2, LocalDate.of(2026, 11, 18))));
    }

    // ---------------------------------------------------------------- lectura
    @Test
    void misArriendosUsaElUsuarioDelTokenYNoExponeSusDatos() {
        Maquina m = maquina();
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE);
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(cliente()));
        when(arriendoRepo.findByUsuarioIdOrderByCreatedAtDescIdDesc(3L)).thenReturn(List.of(a));

        List<ArriendoResponseDto> r = service.misArriendos("cliente@ferrefix.cl");

        assertEquals(1, r.size());
        assertNull(r.get(0).cliente());
    }

    @Test
    void obtenerPropioDevuelveElArriendo() {
        Maquina m = maquina();
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(
                arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE)));
        ArriendoResponseDto r = service.obtener(77L, "CLIENTE@ferrefix.cl", false);   // sin importar mayúsculas
        assertEquals("ARR-000077", r.codigo());
        assertNull(r.cliente());
    }

    @Test
    void obtenerAjenoDa404IgualQueUnoInexistente() {
        Maquina m = maquina();
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(
                arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE)));
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.obtener(77L, "otro@ferrefix.cl", false));
        assertEquals("Arriendo no encontrado: 77", ex.getMessage());
    }

    @Test
    void obtenerComoAdminIncluyeRutNombreYEmailDelCliente() {
        Maquina m = maquina();
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(
                arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE)));
        ArriendoResponseDto r = service.obtener(77L, "admin@ferrefix.cl", true);
        assertEquals("15432890-4", r.cliente().rut());
        assertEquals("Juan Maestro", r.cliente().nombre());
        assertEquals("cliente@ferrefix.cl", r.cliente().email());
        assertFalse(r.toString().toLowerCase().contains("hash"));
    }

    @Test
    void obtenerInexistente() {
        when(arriendoRepo.findDetalleById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.obtener(9L, "admin@ferrefix.cl", true));
    }

    @SuppressWarnings("unchecked")
    @Test
    void listarAceptaElEstadoEnMinusculaYDevuelveDatosDelCliente() {
        Maquina m = maquina();
        when(arriendoRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(
                arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE)));
        List<ArriendoResponseDto> r = service.listar("vigente", 3L, 5L);
        assertEquals("cliente@ferrefix.cl", r.get(0).cliente().email());
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(arriendoRepo).findAll(any(Specification.class), sort.capture());
        assertEquals(Sort.Direction.DESC, sort.getValue().getOrderFor("createdAt").getDirection());
    }

    @Test
    void listarConEstadoInvalidoDa400() {
        assertThrows(BadRequestException.class, () -> service.listar("CANCELADO", null, null));
        verifyNoInteractions(arriendoRepo);
    }

    // ---------------------------------------------------------------- actualizar (solo contacto y faena)
    @Test
    void actualizarSoloCambiaContactoYFaena() {
        Maquina m = maquina();
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE);
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(a));
        when(arriendoRepo.save(a)).thenReturn(a);

        ArriendoResponseDto r = service.actualizar(77L, new ActualizarArriendoRequestDto(" +56 2 2222 2222 ", " Nueva 456 ", " Valparaíso ", " Viña del Mar "));

        assertEquals("+56 2 2222 2222", r.telefonoContacto());
        assertEquals("Nueva 456", r.direccionFaena());
        assertEquals("Valparaíso", r.regionFaena());
        assertEquals("Viña del Mar", r.comunaFaena());
        // Lo contractual queda intacto
        assertEquals(18990, r.tarifaDia());
        assertEquals(5, r.dias());
        assertEquals(94950L, r.total());
        assertEquals(HOY, r.fechaInicio());
        assertEquals(HOY.plusDays(4), r.fechaFin());
        assertEquals("MAQ-BET-02-U01", r.unidadCodigo());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), r.updatedAt());
        verifyNoInteractions(unidadRepo, maquinaRepo);                       // editar no toca unidades
    }

    @Test
    void actualizarArriendoFinalizadoDa409() {
        Maquina m = maquina();
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.DISPONIBLE), EstadoArriendo.FINALIZADO);
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(a));
        assertThrows(ConflictException.class, () -> service.actualizar(77L, new ActualizarArriendoRequestDto("+56 2 2222 2222", "x", "y", "z")));
        verify(arriendoRepo, never()).save(any());
    }

    @Test
    void actualizarInexistente() {
        when(arriendoRepo.findDetalleById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.actualizar(9L, new ActualizarArriendoRequestDto("+56 2 2222 2222", "x", "y", "z")));
    }

    // ---------------------------------------------------------------- devolver
    @Test
    void devolverFinalizaLiberaLaUnidadYRecalculaElModelo() {
        Maquina m = maquina();
        m.setEstado(EstadoMaquina.NO_DISPONIBLE);
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE);
        when(arriendoRepo.findMaquinaIdById(77L)).thenReturn(Optional.of(5L));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(a));
        when(unidadRepo.cambiarEstado(11L, EstadoUnidad.ARRENDADA, EstadoUnidad.DISPONIBLE)).thenReturn(1);
        when(arriendoRepo.save(a)).thenReturn(a);
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(1L);

        ArriendoResponseDto r = service.devolver(77L);

        assertEquals("FINALIZADO", r.estado());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), r.fechaDevolucion());   // hora de Chile
        assertEquals(EstadoMaquina.DISPONIBLE, m.getEstado());
        assertNotNull(r.cliente());                                                   // vista de ADMIN
        InOrder orden = inOrder(arriendoRepo, maquinaRepo, unidadRepo);
        orden.verify(arriendoRepo).findMaquinaIdById(77L);                            // 1) averiguar el modelo (escalar)
        orden.verify(maquinaRepo).findByIdForUpdate(5L);                              // 2) bloquearlo
        orden.verify(arriendoRepo).findDetalleById(77L);                              // 3) recién ahí leer el arriendo
        orden.verify(unidadRepo).cambiarEstado(11L, EstadoUnidad.ARRENDADA, EstadoUnidad.DISPONIBLE);
        orden.verify(unidadRepo).countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE);
    }

    @Test
    void devolverDosVecesDa409SinTocarLaUnidad() {
        Maquina m = maquina();
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.DISPONIBLE), EstadoArriendo.FINALIZADO);
        when(arriendoRepo.findMaquinaIdById(77L)).thenReturn(Optional.of(5L));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(a));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.devolver(77L));

        assertEquals("El arriendo ya fue devuelto", ex.getMessage());
        verify(unidadRepo, never()).cambiarEstado(anyLong(), any(), any());
    }

    @Test
    void devolverInexistenteDa404SinBloquear() {
        when(arriendoRepo.findMaquinaIdById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.devolver(9L));
        verifyNoInteractions(maquinaRepo, unidadRepo);
    }

    @Test
    void devolverConLaUnidadYaLibreEsUnaInconsistenciaYNoSeOculta() {
        Maquina m = maquina();
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.DISPONIBLE), EstadoArriendo.VIGENTE);
        when(arriendoRepo.findMaquinaIdById(77L)).thenReturn(Optional.of(5L));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(a));
        when(unidadRepo.cambiarEstado(11L, EstadoUnidad.ARRENDADA, EstadoUnidad.DISPONIBLE)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.devolver(77L));
        verify(arriendoRepo, never()).save(any());
    }

    // ---------------------------------------------------------------- eliminar
    @Test
    void eliminarUnArriendoVigenteLiberaLaUnidad() {
        Maquina m = maquina();
        m.setEstado(EstadoMaquina.NO_DISPONIBLE);
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.VIGENTE);
        when(arriendoRepo.findMaquinaIdById(77L)).thenReturn(Optional.of(5L));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(a));
        when(unidadRepo.cambiarEstado(11L, EstadoUnidad.ARRENDADA, EstadoUnidad.DISPONIBLE)).thenReturn(1);
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(1L);

        service.eliminar(77L);

        InOrder orden = inOrder(maquinaRepo, unidadRepo, arriendoRepo);
        orden.verify(maquinaRepo).findByIdForUpdate(5L);
        orden.verify(unidadRepo).cambiarEstado(11L, EstadoUnidad.ARRENDADA, EstadoUnidad.DISPONIBLE);
        orden.verify(arriendoRepo).delete(a);
        assertEquals(EstadoMaquina.DISPONIBLE, m.getEstado());
    }

    @Test
    void eliminarUnArriendoFinalizadoNoTocaLaUnidad() {
        Maquina m = maquina();
        Arriendo a = arriendo(77, m, unidad(m, 11, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), EstadoArriendo.FINALIZADO);
        when(arriendoRepo.findMaquinaIdById(77L)).thenReturn(Optional.of(5L));
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(arriendoRepo.findDetalleById(77L)).thenReturn(Optional.of(a));
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(1L);   // otra unidad del modelo sigue libre

        service.eliminar(77L);

        verify(arriendoRepo).delete(a);
        verify(unidadRepo, never()).cambiarEstado(anyLong(), any(), any());   // la unidad podría estar en uso por OTRO arriendo
    }

    @Test
    void eliminarInexistenteDa404() {
        when(arriendoRepo.findMaquinaIdById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eliminar(9L));
        verify(arriendoRepo, never()).delete(any(Arriendo.class));
    }
}
