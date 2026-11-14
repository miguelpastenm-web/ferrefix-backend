package cl.ferrefix.service.impl;

import cl.ferrefix.exception.ResourceNotFoundException;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.ActualizarPerfilRequestDto;
import cl.ferrefix.model.dto.UsuarioResponseDto;
import cl.ferrefix.model.mapper.UsuarioMapper;
import cl.ferrefix.repository.UsuarioRepository;
import cl.ferrefix.service.UsuarioService;
import cl.ferrefix.util.Textos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper mapper;

    @Override
    @Transactional
    public UsuarioResponseDto actualizarPerfil(String emailAutenticado, ActualizarPerfilRequestDto request) {
        Usuario u = usuarioRepository.findByEmailIgnoreCase(emailAutenticado)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Campo a campo: se conservan id, rut, email, passwordHash, roles, activo y createdAt.
        u.setNombre(request.nombre().trim());
        u.setTelefono(Textos.limpiar(request.telefono()));     // omitido o en blanco => null
        u.setDireccion(Textos.limpiar(request.direccion()));
        u.setRegion(Textos.limpiar(request.region()));
        u.setComuna(Textos.limpiar(request.comuna()));

        return mapper.toResponse(usuarioRepository.save(u));
    }
}
