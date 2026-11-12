package cl.ferrefix.service;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.*;
import cl.ferrefix.model.dto.MaquinaRequestDto;
import cl.ferrefix.model.dto.MaquinaResponseDto;
import cl.ferrefix.model.dto.UnidadResponseDto;
import cl.ferrefix.model.mapper.MaquinaMapper;
import cl.ferrefix.repository.ArriendoRepository;
import cl.ferrefix.repository.MaquinaRepository;
import cl.ferrefix.repository.MaquinaUnidadRepository;
import cl.ferrefix.service.MaquinaDisponibilidad;
import cl.ferrefix.service.impl.MaquinaServiceImpl;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaquinaServiceTest {

    @Mock MaquinaRepository maquinaRepo;
    @Mock MaquinaUnidadRepository unidadRepo;
    @Mock ArriendoRepository arriendoRepo;
    @Spy MaquinaMapper mapper = new MaquinaMapper();
    MaquinaServiceImpl service;

    @BeforeEach
    void setUp() {
        // MaquinaDisponibilidad real sobre los mismos mocks: así se verifica el orden bloqueo -> cambios.
        service = new MaquinaServiceImpl(maquinaRepo, unidadRepo, arriendoRepo, mapper,
                new MaquinaDisponibilidad(maquinaRepo, unidadRepo));
    }

    private static Map<String, String> specs(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private MaquinaRequestDto dto(String codigo, Map<String, String> specs) {
        return new MaquinaRequestDto(codigo, " Betonera 150L ", " Ingco ", " Hormigón y Mezcla ", 18990,
                "Cable", "/media/maquinas/maq-bet-02.webp", specs);
    }

    private Maquina maquina(long id, String codigo, EstadoMaquina estado) {
        Maquina m = new Maquina();
        m.setId(id);
        m.setCodigo(codigo);
        m.setNombre("Viejo");
        m.setMarca("X");
        m.setCategoria("Cat");
        m.setTarifaDia(1000);
        m.setEstado(estado);
        m.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        m.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
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

    // ---------------------------------------------------------------- crear / actualizar
    @Test
    void crearNormalizaYNaceNoDisponibleSinUnidades() {
        when(maquinaRepo.save(any(Maquina.class))).thenAnswer(i -> i.getArgument(0));
        MaquinaResponseDto r = service.crear(dto(" MAQ-BET-02 ", specs("Motor", "0.75 HP", "Ruedas", "10\"")));

        assertEquals("MAQ-BET-02", r.codigo());
        assertEquals("Betonera 150L", r.nombre());
        assertEquals("Ingco", r.marca());
        assertEquals("Hormigón y Mezcla", r.categoria());
        assertEquals("NO_DISPONIBLE", r.estado());
        assertEquals(0, r.unidadesTotales());
        assertEquals(0, r.unidadesDisponibles());
        assertEquals(List.of("Motor", "Ruedas"), List.copyOf(r.especificaciones().keySet()));
    }

    @Test
    void crearCodigoDuplicado() {
        when(maquinaRepo.existsByCodigo("A")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.crear(dto("A", null)));
        verify(maquinaRepo, never()).save(any());
    }

    @Test
    void crearConEspecificacionInvalida() {
        assertThrows(BadRequestException.class, () -> service.crear(dto("A", specs(" ", "x"))));
    }

    @Test
    void actualizarConservaIdCreatedAtYEstadoYRefrescaUpdatedAt() {
        Maquina m = maquina(5, "OLD", EstadoMaquina.DISPONIBLE);
        m.agregarEspecificacion("Peso", "1 kg", 0);
        m.agregarEspecificacion("Color", "Rojo", 1);
        m.agregarEspecificacion("Marca", "M", 2);
        var pesoOriginal = m.getEspecificaciones().get(0);
        LocalDateTime creado = m.getCreatedAt();
        LocalDateTime antes = m.getUpdatedAt();
        when(maquinaRepo.findById(5L)).thenReturn(Optional.of(m));
        when(maquinaRepo.saveAndFlush(any(Maquina.class))).thenAnswer(i -> i.getArgument(0));
        when(unidadRepo.contarPorMaquinaYEstado(anyCollection())).thenReturn(List.of(
                new Object[]{5L, EstadoUnidad.DISPONIBLE, 1L}, new Object[]{5L, EstadoUnidad.ARRENDADA, 2L}));

        MaquinaResponseDto r = service.actualizar(5L, dto("OLD", specs("Nueva", "n", "Marca", "M2", "Peso", "p")));

        assertEquals(5L, r.id());
        assertEquals(creado, r.createdAt());
        assertTrue(r.updatedAt().isAfter(antes));
        assertEquals("OLD", r.codigo());                            // el código no cambia
        assertEquals("DISPONIBLE", r.estado());                     // el PUT no toca el estado
        assertEquals(3, r.unidadesTotales());
        assertEquals(1, r.unidadesDisponibles());
        // quitó Color, agregó Nueva y reordenó: misma lista que verá un GET posterior
        assertEquals(List.of("Nueva", "Marca", "Peso"), List.copyOf(r.especificaciones().keySet()));
        assertTrue(m.getEspecificaciones().contains(pesoOriginal));  // actualización en el lugar, sin reinsertar
        assertEquals(List.of(0, 1, 2), m.getEspecificaciones().stream().map(MaquinaEspecificacion::getOrden).toList());
    }

    @Test
    void actualizarInexistente() {
        when(maquinaRepo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.actualizar(9L, dto("A", null)));
    }

    @Test
    void actualizarNoPermiteCambiarElCodigo() {
        Maquina m = maquina(5, "OLD", EstadoMaquina.DISPONIBLE);
        when(maquinaRepo.findById(5L)).thenReturn(Optional.of(m));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.actualizar(5L, dto("NEW", null)));

        assertEquals("El código de una máquina no se puede cambiar", ex.getMessage());
        verify(maquinaRepo, never()).saveAndFlush(any());
        assertEquals("OLD", m.getCodigo());
        assertEquals("Viejo", m.getNombre());                       // no se tocó ningún otro campo
    }

    @Test
    void actualizarAceptaElMismoCodigoConEspaciosAlrededor() {
        Maquina m = maquina(5, "OLD", EstadoMaquina.DISPONIBLE);
        when(maquinaRepo.findById(5L)).thenReturn(Optional.of(m));
        when(maquinaRepo.saveAndFlush(any(Maquina.class))).thenAnswer(i -> i.getArgument(0));
        when(unidadRepo.contarPorMaquinaYEstado(anyCollection())).thenReturn(List.of());
        assertEquals("OLD", service.actualizar(5L, dto("  OLD ", null)).codigo());
    }

    // ---------------------------------------------------------------- eliminar
    @Test
    void eliminarBloqueaElModeloRevisaArriendosYLuegoBorra() {
        Maquina m = maquina(5, "A", EstadoMaquina.DISPONIBLE);
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(arriendoRepo.existsByUnidadMaquinaId(5L)).thenReturn(false);

        service.eliminar(5L);

        InOrder orden = inOrder(maquinaRepo, arriendoRepo);
        orden.verify(maquinaRepo).findByIdForUpdate(5L);
        orden.verify(arriendoRepo).existsByUnidadMaquinaId(5L);
        orden.verify(maquinaRepo).delete(m);
    }

    @Test
    void eliminarConArriendosRegistradosDa409SinBorrar() {
        Maquina m = maquina(5, "A", EstadoMaquina.DISPONIBLE);
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(arriendoRepo.existsByUnidadMaquinaId(5L)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.eliminar(5L));

        assertTrue(ex.getMessage().contains("arriendos registrados"));
        verify(maquinaRepo, never()).delete(any(Maquina.class));
    }

    @Test
    void eliminarInexistente() {
        when(maquinaRepo.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eliminar(9L));
    }

    // ---------------------------------------------------------------- unidades
    @Test
    void agregarUnidadesBloqueaPrimeroNumeraDesdeElMaximoYMarcaDisponible() {
        Maquina m = maquina(5, "MAQ-BET-02", EstadoMaquina.NO_DISPONIBLE);
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(unidadRepo.findByMaquinaIdOrderByCodigoAsc(5L)).thenReturn(List.of(
                unidad(m, 1, "MAQ-BET-02-U01", EstadoUnidad.ARRENDADA), unidad(m, 3, "MAQ-BET-02-U03", EstadoUnidad.ARRENDADA)));
        when(unidadRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(2L);

        List<UnidadResponseDto> r = service.agregarUnidades(5L, 2);

        assertEquals(List.of("MAQ-BET-02-U04", "MAQ-BET-02-U05"), r.stream().map(UnidadResponseDto::codigo).toList());
        assertTrue(r.stream().allMatch(u -> u.estado().equals("DISPONIBLE")));
        assertEquals(EstadoMaquina.DISPONIBLE, m.getEstado());
        InOrder orden = inOrder(maquinaRepo, unidadRepo);
        orden.verify(maquinaRepo).findByIdForUpdate(5L);
        orden.verify(unidadRepo).saveAll(any());
    }

    @Test
    void primeraAltaDeUnidadesEmpiezaEnU01() {
        Maquina m = maquina(5, "MAQ-X", EstadoMaquina.NO_DISPONIBLE);
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(unidadRepo.findByMaquinaIdOrderByCodigoAsc(5L)).thenReturn(List.of());
        when(unidadRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(1L);
        assertEquals("MAQ-X-U01", service.agregarUnidades(5L, 1).get(0).codigo());
    }

    @Test
    void agregarUnidadesCantidadFueraDeRango() {
        assertThrows(BadRequestException.class, () -> service.agregarUnidades(5L, 0));
        assertThrows(BadRequestException.class, () -> service.agregarUnidades(5L, 21));
        verifyNoInteractions(maquinaRepo);
    }

    @Test
    void eliminarUnidadDisponibleRecalculaYDejaElModeloNoDisponible() {
        Maquina m = maquina(5, "A", EstadoMaquina.DISPONIBLE);
        MaquinaUnidad u = unidad(m, 7, "A-U01", EstadoUnidad.DISPONIBLE);
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(unidadRepo.findByIdAndMaquinaId(7L, 5L)).thenReturn(Optional.of(u));
        when(unidadRepo.countByMaquinaIdAndEstado(5L, EstadoUnidad.DISPONIBLE)).thenReturn(0L);

        service.eliminarUnidad(5L, 7L);

        verify(unidadRepo).delete(u);
        assertEquals(EstadoMaquina.NO_DISPONIBLE, m.getEstado());
    }

    @Test
    void eliminarUnidadArrendadaDa409() {
        Maquina m = maquina(5, "A", EstadoMaquina.DISPONIBLE);
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(unidadRepo.findByIdAndMaquinaId(7L, 5L)).thenReturn(Optional.of(unidad(m, 7, "A-U01", EstadoUnidad.ARRENDADA)));
        assertThrows(ConflictException.class, () -> service.eliminarUnidad(5L, 7L));
        verify(unidadRepo, never()).delete(any(MaquinaUnidad.class));
    }

    @Test
    void eliminarUnidadConHistorialDa409SinBorrar() {
        Maquina m = maquina(5, "A", EstadoMaquina.DISPONIBLE);
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(m));
        when(unidadRepo.findByIdAndMaquinaId(7L, 5L)).thenReturn(Optional.of(unidad(m, 7, "A-U01", EstadoUnidad.DISPONIBLE)));
        when(arriendoRepo.existsByUnidadId(7L)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.eliminarUnidad(5L, 7L));

        assertTrue(ex.getMessage().contains("arriendos registrados"));
        verify(unidadRepo, never()).delete(any(MaquinaUnidad.class));
    }

    @Test
    void eliminarUnidadDeOtroModeloDa404() {
        when(maquinaRepo.findByIdForUpdate(5L)).thenReturn(Optional.of(maquina(5, "A", EstadoMaquina.DISPONIBLE)));
        when(unidadRepo.findByIdAndMaquinaId(99L, 5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eliminarUnidad(5L, 99L));
    }

    @Test
    void listarUnidadesDeMaquinaInexistente() {
        when(maquinaRepo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listarUnidades(9L));
    }

    // ---------------------------------------------------------------- listar / obtener
    @SuppressWarnings("unchecked")
    @Test
    void listarArmaConteosPorModeloConUnaSolaConsultaAgrupada() {
        Maquina a = maquina(1, "A", EstadoMaquina.DISPONIBLE);
        Maquina b = maquina(2, "B", EstadoMaquina.NO_DISPONIBLE);
        when(maquinaRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(a, b));
        when(unidadRepo.contarPorMaquinaYEstado(anyCollection())).thenReturn(List.of(
                new Object[]{1L, EstadoUnidad.DISPONIBLE, 1L}, new Object[]{1L, EstadoUnidad.ARRENDADA, 1L},
                new Object[]{2L, EstadoUnidad.ARRENDADA, 1L}));

        List<MaquinaResponseDto> r = service.listar(null, null, false, "tarifa,desc");

        assertEquals(2, r.get(0).unidadesTotales());
        assertEquals(1, r.get(0).unidadesDisponibles());
        assertEquals(1, r.get(1).unidadesTotales());
        assertEquals(0, r.get(1).unidadesDisponibles());
        verify(unidadRepo, times(1)).contarPorMaquinaYEstado(anyCollection());
        ArgumentCaptor<Sort> cap = ArgumentCaptor.forClass(Sort.class);
        verify(maquinaRepo).findAll(any(Specification.class), cap.capture());
        assertEquals(Sort.Direction.DESC, cap.getValue().getOrderFor("tarifaDia").getDirection());
    }

    @Test
    void listarSinResultadosNoConsultaConteos() {
        when(maquinaRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        assertTrue(service.listar(null, null, true, null).isEmpty());
        verifyNoInteractions(unidadRepo);
    }

    @Test
    void listarConSortInvalidoDa400() {
        for (String malo : new String[]{"precioVenta", "tarifaDia", "estado", "tarifa,x", "nombre,asc,y"}) {
            assertThrows(BadRequestException.class, () -> service.listar(null, null, false, malo), malo);
        }
        verifyNoInteractions(maquinaRepo);
    }

    @Test
    void obtenerInexistente() {
        when(maquinaRepo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.obtener(9L));
    }
}
