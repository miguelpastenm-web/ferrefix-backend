package cl.ferrefix.service;

import cl.ferrefix.model.dto.ActualizarArriendoRequestDto;
import cl.ferrefix.model.dto.ArriendoResponseDto;
import cl.ferrefix.model.dto.CrearArriendoRequestDto;
import cl.ferrefix.security.UsuarioToken;

import java.util.List;

public interface ArriendoService {

    /** El cliente sale del token. La unidad, tarifa y total las calcula el servidor. */
    ArriendoResponseDto crear(UsuarioToken cliente, CrearArriendoRequestDto request);

    List<ArriendoResponseDto> misArriendos(Long usuarioId);

    /** Un cliente solo ve los suyos (el ajeno responde 404); un ADMIN ve todos y con los datos del cliente. */
    ArriendoResponseDto obtener(Long id, Long usuarioId, boolean esAdmin);

    List<ArriendoResponseDto> listar(String estado, Long usuarioId, Long maquinaId);

    ArriendoResponseDto actualizar(Long id, ActualizarArriendoRequestDto request);

    ArriendoResponseDto devolver(Long id);

    void eliminar(Long id);
}
