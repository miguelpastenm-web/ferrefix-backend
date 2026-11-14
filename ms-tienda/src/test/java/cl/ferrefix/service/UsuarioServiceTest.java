package cl.ferrefix.service;

import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.ActualizarPerfilRequestDto;
import cl.ferrefix.model.dto.UsuarioResponseDto;
import cl.ferrefix.model.mapper.UsuarioMapper;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.impl.UsuarioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    static final LocalDateTime CREADO = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Mock UsuarioRepository usuarioRepo;
    UsuarioServiceImpl service;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new UsuarioServiceImpl(usuarioRepo, new UsuarioMapper());
        usuario = new Usuario();
        usuario.setId(2L);
        usuario.setRut("15432890-4");
        usuario.setNombre("Cliente Demo");
        usuario.setEmail("cliente@ferrefix.cl");
        usuario.setPasswordHash("$2a$10$hashquenuncadebesalir");
        usuario.setTelefono("+56 9 1111 1111");
        usuario.setDireccion("Calle vieja 1");
        usuario.setRegion("Región vieja");
        usuario.setComuna("Comuna vieja");
        usuario.setActivo(true);
        usuario.setCreatedAt(CREADO);
        usuario.setRoles(Set.of(Rol.CLIENTE));
    }

    private void stubs() {
        when(usuarioRepo.findByEmailIgnoreCase("cliente@ferrefix.cl")).thenReturn(Optional.of(usuario));
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void actualizaLosCincoCamposConRecorteDeEspacios() {
        stubs();
        UsuarioResponseDto r = service.actualizarPerfil("cliente@ferrefix.cl", new ActualizarPerfilRequestDto(
                "  Juan Maestro  ", " +56 9 8765 4321 ", " Av. Providencia 1240 ", " Región Metropolitana ", " Providencia "));

        assertEquals("Juan Maestro", usuario.getNombre());
        assertEquals("+56 9 8765 4321", usuario.getTelefono());
        assertEquals("Av. Providencia 1240", usuario.getDireccion());
        assertEquals("Región Metropolitana", usuario.getRegion());
        assertEquals("Providencia", usuario.getComuna());
        assertEquals("Juan Maestro", r.nombre());
        assertEquals("Providencia", r.comuna());
        verify(usuarioRepo).save(usuario);
    }

    @Test
    void loQueNoSeEditaQuedaIntacto() {
        stubs();
        service.actualizarPerfil("cliente@ferrefix.cl", new ActualizarPerfilRequestDto("Otro Nombre", null, null, null, null));

        assertEquals(2L, usuario.getId());
        assertEquals("15432890-4", usuario.getRut());                 // el RUT no cambia
        assertEquals("cliente@ferrefix.cl", usuario.getEmail());      // ni el email (es el subject del JWT)
        assertEquals("$2a$10$hashquenuncadebesalir", usuario.getPasswordHash());
        assertEquals(Set.of(Rol.CLIENTE), usuario.getRoles());
        assertTrue(usuario.isActivo());
        assertEquals(CREADO, usuario.getCreatedAt());
    }

    @Test
    void unOpcionalOmitidoOEnBlancoSeBorraYQuedaNull() {
        stubs();
        UsuarioResponseDto r = service.actualizarPerfil("cliente@ferrefix.cl", new ActualizarPerfilRequestDto("Juan", null, "", "   ", "\t"));

        assertNull(usuario.getTelefono());
        assertNull(usuario.getDireccion());
        assertNull(usuario.getRegion());
        assertNull(usuario.getComuna());
        assertNull(r.telefono());
        assertNull(r.direccion());
    }

    @Test
    void buscaPorEmailSinImportarMayusculasDelToken() {
        when(usuarioRepo.findByEmailIgnoreCase("CLIENTE@Ferrefix.CL")).thenReturn(Optional.of(usuario));
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        assertEquals("Juan", service.actualizarPerfil("CLIENTE@Ferrefix.CL", new ActualizarPerfilRequestDto("Juan", null, null, null, null)).nombre());
    }

    @Test
    void laRespuestaNuncaTraeElHash() {
        stubs();
        UsuarioResponseDto r = service.actualizarPerfil("cliente@ferrefix.cl", new ActualizarPerfilRequestDto("Juan", null, null, null, null));
        String texto = r.toString().toLowerCase();
        assertFalse(texto.contains("hashquenuncadebesalir") || texto.contains("$2a$"));
        assertEquals(java.util.List.of("CLIENTE"), r.roles());
    }

    @Test
    void usuarioInexistenteDa404YNoGuarda() {
        when(usuarioRepo.findByEmailIgnoreCase("fantasma@x.cl")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.actualizarPerfil("fantasma@x.cl", new ActualizarPerfilRequestDto("Juan", null, null, null, null)));
        verify(usuarioRepo, never()).save(any());
    }
}
