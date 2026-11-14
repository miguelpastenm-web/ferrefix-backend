package cl.ferrefix.model.mapper;

import cl.ferrefix.model.Orden;
import cl.ferrefix.model.dto.OrdenResponseDto;
import org.springframework.stereotype.Component;

@Component
public class OrdenMapper {

    /** @param incluirCliente true solo para ADMIN. Nunca incluye datos de credenciales. */
    public OrdenResponseDto toResponse(Orden o, boolean incluirCliente) {
        return new OrdenResponseDto(
                o.getId(),
                String.format("ORD-%06d", o.getId()),
                String.format("BOL-%06d", o.getId()),
                o.getEstado().name(),
                o.getMetodoPago().name(),
                o.getItems().stream()
                        .map(i -> new OrdenResponseDto.Item(i.getProductoId(), i.getSku(), i.getNombre(),
                                i.getPrecioUnitario(), i.getCantidad(), i.getSubtotal()))
                        .toList(),
                o.getSubtotal(),
                o.getCostoEnvio(),
                o.getTotal(),
                new OrdenResponseDto.Envio(o.getTelefonoContacto(), o.getDireccionEnvio(), o.getRegionEnvio(),
                        o.getComunaEnvio(), o.getIndicaciones()),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                incluirCliente
                        ? new OrdenResponseDto.Cliente(o.getUsuario().getId(), o.getClienteRut(), o.getClienteNombre(), o.getClienteEmail())
                        : null);
    }
}
