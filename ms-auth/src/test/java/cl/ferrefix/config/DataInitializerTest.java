package cl.ferrefix.config;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Usuarios iniciales. Como cada servicio tiene su esquema, al recrear la base el ADMIN y el CLIENTE de demostración
 * se vuelven a crear aquí (en ms-auth) al arrancar, solo si su contraseña viene por variable de entorno.
 */
class DataInitializerTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);   // costo bajo: solo para que el test sea rápido

    private DataInitializer inicializador(String claveAdmin, String claveCliente) {
        DataInitializer init = new DataInitializer(repo, encoder);
        ReflectionTestUtils.setField(init, "adminEmail", "admin@ferrefix.cl");
        ReflectionTestUtils.setField(init, "adminPassword", claveAdmin);
        ReflectionTestUtils.setField(init, "clientEmail", "cliente@ferrefix.cl");
        ReflectionTestUtils.setField(init, "clientPassword", claveCliente);
        return init;
    }

    @Test
    void conLasDosClavesCreaAdminYClienteConLaClaveCifrada() {
        DataInitializer init = inicializador("clave-admin-123", "clave-cliente-123");
        ReflectionTestUtils.setField(init, "adminEmail", " Admin@Ferrefix.cl ");   // tal como podría venir en el .env
        init.run();

        ArgumentCaptor<Usuario> cap = ArgumentCaptor.forClass(Usuario.class);
        verify(repo, times(2)).save(cap.capture());
        List<Usuario> creados = cap.getAllValues();

        Usuario admin = creados.get(0);
        assertEquals("admin@ferrefix.cl", admin.getEmail());                 // normalizado: sin espacios y en minúscula
        assertEquals(Set.of(Rol.ADMIN), admin.getRoles());
        assertNotEquals("clave-admin-123", admin.getPasswordHash());         // nunca en texto plano
        assertTrue(encoder.matches("clave-admin-123", admin.getPasswordHash()));

        Usuario cliente = creados.get(1);
        assertEquals("cliente@ferrefix.cl", cliente.getEmail());
        assertEquals(Set.of(Rol.CLIENTE), cliente.getRoles());
        assertTrue(encoder.matches("clave-cliente-123", cliente.getPasswordHash()));
        assertTrue(cliente.isActivo());
    }

    @Test
    void sinClaveOConClaveCortaNoCreaEseUsuario() {
        inicializador("", "corta").run();          // vacía y de menos de 8 caracteres
        inicializador(null, null).run();
        verify(repo, never()).save(any());
    }

    @Test
    void puedeCrearseSoloUnoDeLosDos() {
        inicializador("clave-admin-123", "").run();
        ArgumentCaptor<Usuario> cap = ArgumentCaptor.forClass(Usuario.class);
        verify(repo).save(cap.capture());
        assertEquals(Set.of(Rol.ADMIN), cap.getValue().getRoles());
    }

    @Test
    void siElCorreoOElRutYaExistenNoDuplicaNiCambiaLaClave() {
        when(repo.existsByEmailIgnoreCase("admin@ferrefix.cl")).thenReturn(true);   // el admin ya estaba
        when(repo.existsByRut("15432890-4")).thenReturn(true);                       // el RUT del cliente ya estaba
        inicializador("otra-clave-distinta", "otra-clave-distinta").run();
        verify(repo, never()).save(any());
    }
}
