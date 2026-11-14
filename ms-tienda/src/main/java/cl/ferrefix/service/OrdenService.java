package cl.ferrefix.service;

import cl.ferrefix.model.EstadoOrden;
import cl.ferrefix.model.dto.CrearOrdenRequestDto;
import cl.ferrefix.model.dto.OrdenResponseDto;

import java.util.List;

public interface OrdenService {

    /** El cliente sale del token. Precio, subtotal, envío y total los calcula el servidor. Todo o nada. */
    OrdenResponseDto crear(String emailCliente, CrearOrdenRequestDto request);

    List<OrdenResponseDto> misOrdenes(String emailCliente);

    /** Un cliente solo ve las suyas (la ajena responde 404); un ADMIN ve todas, con los datos del cliente. */
    OrdenResponseDto obtener(Long id, String email, boolean esAdmin);

    List<OrdenResponseDto> listar(String estado, Long usuarioId);

    /** Solo ADMIN. Cancelar devuelve el stock de la orden. */
    OrdenResponseDto cambiarEstado(Long id, EstadoOrden nuevoEstado);

    /** Solo ADMIN y solo en estados finales. Nunca toca el stock. */
    void eliminar(Long id);
}
