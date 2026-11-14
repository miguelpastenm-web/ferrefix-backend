package cl.ferrefix.model.mapper;

import cl.ferrefix.model.Rol;
import cl.ferrefix.model.Usuario;
import cl.ferrefix.model.dto.RegisterRequestDto;
import cl.ferrefix.model.dto.UsuarioResponseDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Mapper manual. El hash se setea en la capa service, nunca aquí. */
@Component
public class UsuarioMapper {

    /** Solo para create. Rut y email llegan ya normalizados; el hash y los roles los asigna el service. */
    public Usuario toEntity(RegisterRequestDto dto, String rutNormalizado, String emailNormalizado) {
        Usuario usuario = new Usuario();
        usuario.setRut(rutNormalizado);
        usuario.setEmail(emailNormalizado);
        usuario.setNombre(dto.nombre().trim());
        usuario.setTelefono(limpiar(dto.telefono()));
        usuario.setDireccion(limpiar(dto.direccion()));
        usuario.setRegion(limpiar(dto.region()));
        usuario.setComuna(limpiar(dto.comuna()));
        usuario.setActivo(true);
        return usuario;
    }

    public UsuarioResponseDto toResponse(Usuario u) {
        List<String> roles = u.getRoles().stream()
                .sorted(Comparator.naturalOrder())
                .map(Rol::name)
                .toList();
        return new UsuarioResponseDto(u.getId(), u.getRut(), u.getNombre(), u.getEmail(), u.getTelefono(),
                u.getDireccion(), u.getRegion(), u.getComuna(), u.isActivo(), roles, u.getCreatedAt());
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
