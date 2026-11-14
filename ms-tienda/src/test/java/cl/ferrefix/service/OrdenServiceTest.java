package cl.ferrefix.service;

import cl.ferrefix.exception.BadRequestException;
import cl.ferrefix.exception.ConflictException;
import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.*;
import cl.ferrefix.model.dto.CrearOrdenRequestDto;
import cl.ferrefix.model.dto.CrearOrdenRequestDto.LineaOrden;
import cl.ferrefix.model.dto.OrdenResponseDto;
import cl.ferrefix.model.mapper.OrdenMapper;
import cl.ferrefix.repository.OrdenRepository;
import cl.ferrefix.repository.ProductoRepository;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.impl.OrdenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdenServiceTest {

    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Santiago")); // 12:00 en Santiago

    @Mock OrdenRepository ordenRepo;
    @Mock ProductoRepository productoRepo;
    @Mock UsuarioRepository usuarioRepo;

    OrdenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrdenServiceImpl(ordenRepo, productoRepo, usuarioRepo, new OrdenMapper(), new PoliticaEnvio(3990, 50_000), RELOJ);
    }

    // ---------------------------------------------------------------- datos
    private Usuario cliente() {
        Usuario u = new Usuario();
        u.setId(3L);
        u.setRut("15432890-4");
        u.setNombre("Juan Maestro");
        u.setEmail("cliente@ferrefix.cl");
        u.setActivo(true);
        return u;
    }

    private Producto producto(long id, String sku, String nombre, int precio, int stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        p.setNombre(nombre);
        p.setPrecioVenta(precio);
        p.setStock(stock);
        p.setEstado(stock == 0 ? EstadoProducto.AGOTADO : EstadoProducto.DISPONIBLE);
        return p;
    }

    private CrearOrdenRequestDto request(LineaOrden... lineas) {
        return new CrearOrdenRequestDto(List.of(lineas), MetodoPago.WEBPAY_DEBITO, " +56 9 8765 4321 ",
                " Av. Providencia 1240 ", " Región Metropolitana ", " Providencia ", " Dejar con conserje ");
    }

    private LineaOrden linea(long productoId, int cantidad) {
        return new LineaOrden(productoId, cantidad);
    }

    private Orden orden(long id, EstadoOrden estado) {
        Orden o = new Orden();
        o.setId(id);
        o.setUsuario(cliente());
        o.setClienteRut("15432890-4");
        o.setClienteNombre("Juan Maestro");
        o.setClienteEmail("cliente@ferrefix.cl");
        o.setTelefonoContacto("+56 9 8765 4321");
        o.setDireccionEnvio("Av. Providencia 1240");
        o.setRegionEnvio("Región Metropolitana");
        o.setComunaEnvio("Providencia");
        o.setMetodoPago(MetodoPago.TRANSFERENCIA);
        o.setSubtotal(8500L);
        o.setCostoEnvio(3990);
        o.setTotal(12490L);
        o.setEstado(estado);
        o.agregarItem(item(9L, "SKU-9", 1));
        o.agregarItem(item(3L, "SKU-3", 2));
        o.agregarItem(item(null, "SKU-BORRADO", 5));   // producto eliminado después de la compra
        return o;
    }

    private OrdenItem item(Long productoId, String sku, int cantidad) {
        OrdenItem i = new OrdenItem();
        i.setProductoId(productoId);
        i.setSku(sku);
        i.setNombre("Producto " + sku);
        i.setPrecioUnitario(1000);
        i.setCantidad(cantidad);
        i.setSubtotal(1000L * cantidad);
        return i;
    }

    /** Stubs de una compra exitosa de los productos 3, 5 y 9 (precios 1000, 2500 y 4000). */
    private void stubsDeCompraExitosa() {
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(cliente()));
        when(productoRepo.idsExistentes(List.of(3L, 5L, 9L))).thenReturn(List.of(3L, 5L, 9L));
        when(productoRepo.descontarStock(anyLong(), anyInt())).thenReturn(1);
        when(productoRepo.findAllById(List.of(3L, 5L, 9L))).thenReturn(List.of(
                producto(9, "SKU-9", "Producto nueve", 4000, 10),
                producto(3, "SKU-3", "Producto tres", 1000, 10),
                producto(5, "SKU-5", "Producto cinco", 2500, 10)));
        when(ordenRepo.save(any(Orden.class))).thenAnswer(i -> {
            Orden o = i.getArgument(0);
            o.setId(501L);
            return o;
        });
    }

    // ---------------------------------------------------------------- crear: caso feliz
    @Test
    void crearCalculaTodoEnElServidorConLosPreciosDelCatalogo() {
        stubsDeCompraExitosa();

        OrdenResponseDto r = service.crear("cliente@ferrefix.cl", request(linea(9, 1), linea(3, 2), linea(5, 1)));

        ArgumentCaptor<Orden> cap = ArgumentCaptor.forClass(Orden.class);
        verify(ordenRepo).save(cap.capture());
        Orden o = cap.getValue();
        assertEquals(8500L, o.getSubtotal());                    // 2 x 1000 + 2500 + 4000
        assertEquals(3990, o.getCostoEnvio());                   // bajo 50.000: paga envío
        assertEquals(12490L, o.getTotal());
        assertEquals(EstadoOrden.PAGADA, o.getEstado());
        assertEquals(MetodoPago.WEBPAY_DEBITO, o.getMetodoPago());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), o.getCreatedAt());  // hora de Chile
        // cliente copiado del token (usuario), no del body
        assertEquals("15432890-4", o.getClienteRut());
        assertEquals("Juan Maestro", o.getClienteNombre());
        assertEquals("cliente@ferrefix.cl", o.getClienteEmail());
        assertEquals(3L, o.getUsuario().getId());
        // datos de envío recortados
        assertEquals("+56 9 8765 4321", o.getTelefonoContacto());
        assertEquals("Providencia", o.getComunaEnvio());
        assertEquals("Dejar con conserje", o.getIndicaciones());

        assertEquals(3, o.getItems().size());
        assertEquals(List.of("SKU-3", "SKU-5", "SKU-9"), o.getItems().stream().map(OrdenItem::getSku).toList());
        OrdenItem tres = o.getItems().get(0);
        assertEquals(1000, tres.getPrecioUnitario());
        assertEquals(2, tres.getCantidad());
        assertEquals(2000L, tres.getSubtotal());

        assertEquals("ORD-000501", r.codigo());
        assertEquals("BOL-000501", r.boleta());
        assertEquals(12490L, r.total());
        assertNull(r.cliente(), "El cliente ve su orden sin el bloque de identidad");
    }

    @Test
    void crearDescuentaEnOrdenAscendenteSinImportarElOrdenEnQueLlegan() {
        stubsDeCompraExitosa();
        service.crear("cliente@ferrefix.cl", request(linea(9, 1), linea(3, 2), linea(5, 1)));   // llegan 9, 3, 5

        InOrder orden = inOrder(productoRepo);
        orden.verify(productoRepo).descontarStock(3L, 2);   // siempre 3, 5, 9: evita deadlocks entre compras concurrentes
        orden.verify(productoRepo).descontarStock(5L, 1);
        orden.verify(productoRepo).descontarStock(9L, 1);
    }

    @Test
    void elEnvioEsGratisSoloSiElSubtotalSuperaLos50000() {
        for (int[] caso : new int[][]{{50_000, 3990}, {50_001, 0}, {49_999, 3990}}) {
            reset(productoRepo, ordenRepo, usuarioRepo);
            when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
            when(productoRepo.idsExistentes(List.of(3L))).thenReturn(List.of(3L));
            when(productoRepo.descontarStock(3L, 1)).thenReturn(1);
            when(productoRepo.findAllById(List.of(3L))).thenReturn(List.of(producto(3, "SKU-3", "Caro", caso[0], 5)));
            when(ordenRepo.save(any(Orden.class))).thenAnswer(i -> {
                Orden o = i.getArgument(0);
                o.setId(1L);
                return o;
            });

            OrdenResponseDto r = service.crear("cliente@ferrefix.cl", request(linea(3, 1)));

            assertEquals(caso[1], r.costoEnvio(), "subtotal " + caso[0]);
            assertEquals(caso[0] + (long) caso[1], r.total(), "subtotal " + caso[0]);
        }
    }

    // ---------------------------------------------------------------- crear: stock (todo o nada)
    @Test
    void siUnaLineaNoAlcanzaDa409ConDetalleYNoSeGuardaNada() {
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(productoRepo.idsExistentes(List.of(3L, 5L, 9L))).thenReturn(List.of(3L, 5L, 9L));
        when(productoRepo.descontarStock(3L, 2)).thenReturn(1);
        when(productoRepo.descontarStock(5L, 4)).thenReturn(0);       // pidió 4, hay 2
        when(productoRepo.descontarStock(9L, 1)).thenReturn(1);
        when(productoRepo.findAllById(List.of(5L))).thenReturn(List.of(producto(5, "SKU-5", "Esmeril", 2500, 2)));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.crear("cliente@ferrefix.cl", request(linea(9, 1), linea(3, 2), linea(5, 4))));

        assertTrue(ex.getMessage().contains("No se descontó nada"));
        assertEquals(List.of("Esmeril (SKU SKU-5): disponibles 2, solicitaste 4"), ex.getDetalles());
        verify(productoRepo, times(3)).descontarStock(anyLong(), anyInt());   // intentó todas para informar todos los problemas
        verify(ordenRepo, never()).save(any());                                // la excepción revierte los descuentos ya hechos
    }

    @Test
    void productoAgotadoDa409ConMensajeClaro() {
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(productoRepo.idsExistentes(List.of(5L))).thenReturn(List.of(5L));
        when(productoRepo.descontarStock(5L, 1)).thenReturn(0);
        when(productoRepo.findAllById(List.of(5L))).thenReturn(List.of(producto(5, "SKU-5", "Esmeril", 2500, 0)));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.crear("cliente@ferrefix.cl", request(linea(5, 1))));

        assertEquals(List.of("Esmeril (SKU SKU-5): agotado"), ex.getDetalles());
    }

    @Test
    void variasLineasProblematicasSeInformanJuntas() {
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(productoRepo.idsExistentes(List.of(3L, 5L))).thenReturn(List.of(3L, 5L));
        when(productoRepo.descontarStock(anyLong(), anyInt())).thenReturn(0);
        when(productoRepo.findAllById(List.of(3L, 5L))).thenReturn(List.of(
                producto(5, "SKU-5", "Esmeril", 2500, 0), producto(3, "SKU-3", "Taladro", 1000, 1)));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.crear("cliente@ferrefix.cl", request(linea(5, 1), linea(3, 9))));

        assertEquals(2, ex.getDetalles().size());
        assertTrue(ex.getDetalles().contains("Taladro (SKU SKU-3): disponibles 1, solicitaste 9"));
        assertTrue(ex.getDetalles().contains("Esmeril (SKU SKU-5): agotado"));
    }

    @Test
    void productoInexistenteDa404SinDescontarNada() {
        when(usuarioRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(cliente()));
        when(productoRepo.idsExistentes(List.of(3L, 77L))).thenReturn(List.of(3L));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.crear("cliente@ferrefix.cl", request(linea(3, 1), linea(77, 1))));

        assertTrue(ex.getMessage().contains("77"));
        verify(productoRepo, never()).descontarStock(anyLong(), anyInt());
        verify(ordenRepo, never()).save(any());
    }

    @Test
    void productoRepetidoDa400SinTocarLaBase() {
        assertThrows(BadRequestException.class, () -> service.crear("cliente@ferrefix.cl", request(linea(3, 1), linea(3, 2))));
        verifyNoInteractions(usuarioRepo, productoRepo, ordenRepo);
    }

    @Test
    void usuarioInexistenteOInactivoDa401SinTocarElStock() {
        when(usuarioRepo.findByEmailIgnoreCase("fantasma@x.cl")).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> service.crear("fantasma@x.cl", request(linea(3, 1))));

        Usuario inactivo = cliente();
        inactivo.setActivo(false);
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(inactivo));
        assertThrows(BadCredentialsException.class, () -> service.crear("cliente@ferrefix.cl", request(linea(3, 1))));

        verifyNoInteractions(productoRepo, ordenRepo);
    }

    // ---------------------------------------------------------------- lectura
    @Test
    void misOrdenesUsaElUsuarioDelTokenYNoExponeSuIdentidad() {
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(cliente()));
        when(ordenRepo.findByUsuarioIdOrderByCreatedAtDescIdDesc(3L)).thenReturn(List.of(orden(501, EstadoOrden.PAGADA)));
        List<OrdenResponseDto> r = service.misOrdenes("cliente@ferrefix.cl");
        assertEquals(1, r.size());
        assertNull(r.get(0).cliente());
    }

    @Test
    void obtenerPropiaOk_ajenaDa404_adminVeTodaConCliente() {
        when(ordenRepo.findById(501L)).thenReturn(Optional.of(orden(501, EstadoOrden.PAGADA)));

        assertEquals("ORD-000501", service.obtener(501L, "CLIENTE@ferrefix.cl", false).codigo());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.obtener(501L, "otro@ferrefix.cl", false));
        assertEquals("Orden no encontrada: 501", ex.getMessage());      // igual que una inexistente

        OrdenResponseDto admin = service.obtener(501L, "admin@ferrefix.cl", true);
        assertEquals("15432890-4", admin.cliente().rut());
        assertEquals("cliente@ferrefix.cl", admin.cliente().email());
    }

    @Test
    void obtenerInexistente() {
        when(ordenRepo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.obtener(9L, "admin@ferrefix.cl", true));
    }

    @SuppressWarnings("unchecked")
    @Test
    void listarAceptaEstadoEnMinusculaYOrdenaDelMasNuevo() {
        when(ordenRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(orden(501, EstadoOrden.PAGADA)));
        List<OrdenResponseDto> r = service.listar("pagada", 3L);
        assertEquals("cliente@ferrefix.cl", r.get(0).cliente().email());
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(ordenRepo).findAll(any(Specification.class), sort.capture());
        assertEquals(Sort.Direction.DESC, sort.getValue().getOrderFor("createdAt").getDirection());
    }

    @Test
    void listarConEstadoInvalidoDa400() {
        assertThrows(BadRequestException.class, () -> service.listar("COMPLETADA", null));
        verifyNoInteractions(ordenRepo);
    }

    // ---------------------------------------------------------------- cambiar estado / cancelar
    @Test
    void cancelarBloqueaLaOrdenYDevuelveElStockEnOrdenAscendente() {
        Orden o = orden(501, EstadoOrden.PAGADA);
        when(ordenRepo.findByIdForUpdate(501L)).thenReturn(Optional.of(o));
        when(ordenRepo.save(o)).thenReturn(o);

        OrdenResponseDto r = service.cambiarEstado(501L, EstadoOrden.CANCELADA);

        assertEquals("CANCELADA", r.estado());
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), o.getUpdatedAt());
        assertNotNull(r.cliente());                                          // vista de ADMIN
        InOrder orden = inOrder(ordenRepo, productoRepo);
        orden.verify(ordenRepo).findByIdForUpdate(501L);                     // 1) la orden
        orden.verify(productoRepo).restaurarStock(3L, 2);                    // 2) sus productos, ascendente
        orden.verify(productoRepo).restaurarStock(9L, 1);
        verify(productoRepo, times(2)).restaurarStock(anyLong(), anyInt());  // el ítem con producto eliminado se omite
    }

    @Test
    void cancelarDesdeEnPreparacionTambienDevuelveStock() {
        Orden o = orden(501, EstadoOrden.EN_PREPARACION);
        when(ordenRepo.findByIdForUpdate(501L)).thenReturn(Optional.of(o));
        when(ordenRepo.save(o)).thenReturn(o);
        service.cambiarEstado(501L, EstadoOrden.CANCELADA);
        verify(productoRepo).restaurarStock(3L, 2);
        verify(productoRepo).restaurarStock(9L, 1);
    }

    @Test
    void losAvancesNormalesNoTocanElStock() {
        for (EstadoOrden[] par : new EstadoOrden[][]{
                {EstadoOrden.PAGADA, EstadoOrden.EN_PREPARACION},
                {EstadoOrden.EN_PREPARACION, EstadoOrden.DESPACHADA},
                {EstadoOrden.DESPACHADA, EstadoOrden.ENTREGADA}}) {
            Orden o = orden(501, par[0]);
            when(ordenRepo.findByIdForUpdate(501L)).thenReturn(Optional.of(o));
            when(ordenRepo.save(o)).thenReturn(o);
            assertEquals(par[1].name(), service.cambiarEstado(501L, par[1]).estado());
        }
        verifyNoInteractions(productoRepo);
    }

    @Test
    void transicionesInvalidasDan409SinTocarElStock() {
        EstadoOrden[][] invalidas = {
                {EstadoOrden.PAGADA, EstadoOrden.ENTREGADA},         // salta pasos
                {EstadoOrden.PAGADA, EstadoOrden.DESPACHADA},
                {EstadoOrden.DESPACHADA, EstadoOrden.CANCELADA},     // ya salió
                {EstadoOrden.ENTREGADA, EstadoOrden.CANCELADA},
                {EstadoOrden.CANCELADA, EstadoOrden.CANCELADA},      // sin doble devolución de stock
                {EstadoOrden.CANCELADA, EstadoOrden.PAGADA},
                {EstadoOrden.PAGADA, EstadoOrden.PAGADA}};
        for (EstadoOrden[] par : invalidas) {
            Orden o = orden(501, par[0]);
            when(ordenRepo.findByIdForUpdate(501L)).thenReturn(Optional.of(o));
            ConflictException ex = assertThrows(ConflictException.class, () -> service.cambiarEstado(501L, par[1]), par[0] + "->" + par[1]);
            assertTrue(ex.getMessage().startsWith("Transición no permitida"));
            assertEquals(par[0], o.getEstado());
        }
        verifyNoInteractions(productoRepo);
        verify(ordenRepo, never()).save(any());
    }

    @Test
    void cambiarEstadoDeOrdenInexistente() {
        when(ordenRepo.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.cambiarEstado(9L, EstadoOrden.CANCELADA));
    }

    // ---------------------------------------------------------------- eliminar
    @Test
    void eliminarEnEstadosFinalesNoToca_elStock() {
        for (EstadoOrden final_ : new EstadoOrden[]{EstadoOrden.CANCELADA, EstadoOrden.ENTREGADA}) {
            Orden o = orden(501, final_);
            when(ordenRepo.findByIdForUpdate(501L)).thenReturn(Optional.of(o));
            service.eliminar(501L);
            verify(ordenRepo).delete(o);
            clearInvocations(ordenRepo);
        }
        verifyNoInteractions(productoRepo);
    }

    @Test
    void eliminarUnaOrdenEnCursoDa409() {
        for (EstadoOrden enCurso : new EstadoOrden[]{EstadoOrden.PAGADA, EstadoOrden.EN_PREPARACION, EstadoOrden.DESPACHADA}) {
            Orden o = orden(501, enCurso);
            when(ordenRepo.findByIdForUpdate(501L)).thenReturn(Optional.of(o));
            ConflictException ex = assertThrows(ConflictException.class, () -> service.eliminar(501L), enCurso.name());
            assertTrue(ex.getMessage().contains("ENTREGADA o CANCELADA"));
        }
        verify(ordenRepo, never()).delete(any(Orden.class));
        verifyNoInteractions(productoRepo);
    }

    @Test
    void eliminarInexistente() {
        when(ordenRepo.findByIdForUpdate(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eliminar(9L));
    }
}
