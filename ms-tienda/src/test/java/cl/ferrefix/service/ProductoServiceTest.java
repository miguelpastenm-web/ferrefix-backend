package cl.ferrefix.service;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.DuplicateResourceException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.EstadoProducto;
import cl.ferrefix.model.Producto;
import cl.ferrefix.model.dto.ProductoRequestDto;
import cl.ferrefix.model.dto.ProductoResponseDto;
import cl.ferrefix.model.mapper.ProductoMapper;
import cl.ferrefix.repository.ProductoRepository;
import cl.ferrefix.service.impl.ProductoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock ProductoRepository repo;
    @Spy ProductoMapper mapper = new ProductoMapper();
    @InjectMocks ProductoServiceImpl service;

    private static Map<String, String> specs(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private ProductoRequestDto dto(String sku, String categoria, int stock, Map<String, String> specs) {
        return new ProductoRequestDto(sku, " Taladro 20V ", " DeWalt ", categoria, 129990, stock,
                "desc", "/media/productos/a.webp", null, specs);
    }

    private Producto existente() {
        Producto p = new Producto();
        p.setId(5L);
        p.setSku("OLD");
        p.setNombre("Viejo");
        p.setMarca("X");
        p.setCategoria("Maderas");
        p.setPrecioVenta(1000);
        p.setStock(3);
        p.setEstado(EstadoProducto.DISPONIBLE);
        p.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        p.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        p.agregarEspecificacion("Peso", "1 kg", 0);
        p.agregarEspecificacion("Color", "Rojo", 1);
        return p;
    }

    // ---------------------------------------------------------------- crear
    @Test
    void crearNormalizaYCalculaEstadoDisponible() {
        when(repo.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        ProductoResponseDto r = service.crear(dto(" 5422223214 ", "herramientas", 12, specs("Peso", "1.6 kg", "Garantía", "3 años")));

        assertEquals("5422223214", r.sku());
        assertEquals("Taladro 20V", r.nombre());
        assertEquals("DeWalt", r.marca());
        assertEquals("Herramientas", r.categoria());      // escritura oficial
        assertEquals("DISPONIBLE", r.estado());
        assertEquals(List.of("Peso", "Garantía"), List.copyOf(r.especificaciones().keySet())); // orden conservado
    }

    @Test
    void crearConStockCeroQuedaAgotado() {
        when(repo.save(any(Producto.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals("AGOTADO", service.crear(dto("A1", "Maderas", 0, null)).estado());
    }

    @Test
    void crearSkuDuplicado() {
        when(repo.existsBySku("A1")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.crear(dto("A1", "Maderas", 1, null)));
        verify(repo, never()).save(any());
    }

    @Test
    void crearCategoriaInvalida() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.crear(dto("A1", "Todas", 1, null)));
        assertTrue(ex.getMessage().contains("Categoría inválida"));
    }

    @Test
    void crearEspecificacionVacia() {
        assertThrows(BadRequestException.class, () -> service.crear(dto("A1", "Maderas", 1, specs(" ", "x"))));
        assertThrows(BadRequestException.class, () -> service.crear(dto("A1", "Maderas", 1, specs("Peso", " "))));
    }

    @Test
    void crearEspecificacionRepetidaTrasRecortar() {
        assertThrows(BadRequestException.class, () -> service.crear(dto("A1", "Maderas", 1, specs("Peso", "1", " Peso", "2"))));
    }

    @Test
    void crearEspecificacionDemasiadoLarga() {
        assertThrows(BadRequestException.class, () -> service.crear(dto("A1", "Maderas", 1, specs("x".repeat(81), "v"))));
        assertThrows(BadRequestException.class, () -> service.crear(dto("A1", "Maderas", 1, specs("k", "v".repeat(301)))));
    }

    // ---------------------------------------------------------------- actualizar
    @Test
    void actualizarConservaIdYCreatedAtYRefrescaUpdatedAt() {
        Producto p = existente();
        LocalDateTime creado = p.getCreatedAt();
        LocalDateTime actualizadoAntes = p.getUpdatedAt();
        when(repo.findById(5L)).thenReturn(Optional.of(p));
        when(repo.saveAndFlush(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        ProductoResponseDto r = service.actualizar(5L, dto("NEW", "Seguridad", 0, specs("Peso", "2 kg", "Talla", "L")));

        assertEquals(5L, r.id());
        assertEquals(creado, r.createdAt());
        assertTrue(r.updatedAt().isAfter(actualizadoAntes));
        assertEquals("NEW", r.sku());
        assertEquals("Seguridad", r.categoria());
        assertEquals("AGOTADO", r.estado());               // estado recalculado con stock 0
    }

    @Test
    void actualizarSincronizaEspecificacionesSinReinsertar() {
        Producto p = existente();
        var pesoOriginal = p.getEspecificaciones().get(0);  // misma instancia debe actualizarse en el lugar
        when(repo.findById(5L)).thenReturn(Optional.of(p));
        when(repo.saveAndFlush(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        // Color se elimina, Peso cambia de valor y pasa al final, Talla es nueva
        service.actualizar(5L, dto("OLD", "Maderas", 3, specs("Talla", "L", "Peso", "2 kg")));

        assertEquals(2, p.getEspecificaciones().size());
        assertTrue(p.getEspecificaciones().contains(pesoOriginal));
        assertEquals("2 kg", pesoOriginal.getValor());
        assertEquals(1, pesoOriginal.getOrden());
        assertTrue(p.getEspecificaciones().stream().anyMatch(e -> e.getClave().equals("Talla") && e.getOrden() == 0));
        assertTrue(p.getEspecificaciones().stream().noneMatch(e -> e.getClave().equals("Color")));
    }

    @Test
    void actualizarDevuelveEspecificacionesEnElMismoOrdenQueUnGetPosterior() {
        Producto p = existente();                              // Peso(0), Color(1)
        p.agregarEspecificacion("Marca", "M", 2);              // Peso(0), Color(1), Marca(2)
        when(repo.findById(5L)).thenReturn(Optional.of(p));
        when(repo.saveAndFlush(any(Producto.class))).thenAnswer(i -> i.getArgument(0));

        // quita Color, agrega Nueva al inicio y reordena Marca y Peso
        ProductoResponseDto r = service.actualizar(5L,
                dto("OLD", "Maderas", 3, specs("Nueva", "n", "Marca", "M2", "Peso", "p")));

        // sin el sort, en memoria quedaría [Peso, Marca, Nueva]
        assertEquals(List.of("Nueva", "Marca", "Peso"), List.copyOf(r.especificaciones().keySet()));
        assertEquals(List.of("Nueva", "Marca", "Peso"),
                p.getEspecificaciones().stream().map(e -> e.getClave()).toList());
        assertEquals(List.of(0, 1, 2), p.getEspecificaciones().stream().map(e -> e.getOrden()).toList());
        assertEquals("M2", r.especificaciones().get("Marca"));
    }

    @Test
    void actualizarInexistente() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.actualizar(9L, dto("A", "Maderas", 1, null)));
    }

    @Test
    void actualizarSkuDeOtroProducto() {
        when(repo.findById(5L)).thenReturn(Optional.of(existente()));
        when(repo.existsBySkuAndIdNot("OTRO", 5L)).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.actualizar(5L, dto("OTRO", "Maderas", 1, null)));
        verify(repo, never()).saveAndFlush(any());
    }

    // ---------------------------------------------------------------- eliminar / obtener
    @Test
    void eliminarOk() {
        Producto p = existente();
        when(repo.findById(5L)).thenReturn(Optional.of(p));
        service.eliminar(5L);
        verify(repo).delete(p);
    }

    @Test
    void eliminarInexistente() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eliminar(9L));
        verify(repo, never()).delete(any(Producto.class));
    }

    @Test
    void obtenerInexistente() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.obtener(9L));
    }

    // ---------------------------------------------------------------- listar
    @SuppressWarnings("unchecked")
    private Sort sortUsado(String sort) {
        when(repo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        service.listar(null, null, null, sort);
        ArgumentCaptor<Sort> cap = ArgumentCaptor.forClass(Sort.class);
        verify(repo).findAll(any(Specification.class), cap.capture());
        return cap.getValue();
    }

    @Test
    void sortPorDefectoEsNombreAsc() {
        assertEquals("nombre: ASC", sortUsado(null).getOrderFor("nombre").toString());
    }

    @Test
    void sortPrecioDescMapeaALaPropiedad() {
        Sort s = sortUsado("precio,desc");
        assertEquals(Sort.Direction.DESC, s.getOrderFor("precioVenta").getDirection());
        assertNotNull(s.getOrderFor("id")); // desempate estable
    }

    @Test
    void sortSinDireccionEsAsc() {
        assertEquals(Sort.Direction.ASC, sortUsado("STOCK").getOrderFor("stock").getDirection());
    }

    @Test
    void sortConCampoNoPermitidoDa400() {
        for (String malo : new String[]{"passwordHash", "precioVenta", "precio,sideways", "precio,asc,x", "id; drop table"}) {
            assertThrows(BadRequestException.class, () -> service.listar(null, null, null, malo), malo);
        }
        verifyNoInteractions(repo);
    }

    @Test
    void filtroDeCategoriaInvalidaDa400() {
        assertThrows(BadRequestException.class, () -> service.listar(null, "Todas", null, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void listarMapeaResultados() {
        when(repo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(existente()));
        List<ProductoResponseDto> r = service.listar("tal", "maderas", "x", null);
        assertEquals(1, r.size());
        assertEquals(List.of("Peso", "Color"), List.copyOf(r.get(0).especificaciones().keySet()));
    }
}
