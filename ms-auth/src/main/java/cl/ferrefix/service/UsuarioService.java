package cl.ferrefix.service;

import cl.ferrefix.model.dto.ActualizarPerfilRequestDto;
import cl.ferrefix.model.dto.UsuarioResponseDto;

public interface UsuarioService {

    /** Edita el perfil del dueño del token (el email sale del token, nunca del cuerpo). */
    UsuarioResponseDto actualizarPerfil(String emailAutenticado, ActualizarPerfilRequestDto request);
}
