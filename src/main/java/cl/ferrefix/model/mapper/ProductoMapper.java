package cl.ferrefix.model.mapper;

import cl.ferrefix.model.Producto;
import cl.ferrefix.model.ProductoEspecificacion;
import cl.ferrefix.model.dto.ProductoRequestDto;
import cl.ferrefix.model.dto.ProductoResponseDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mapper manual. toEntity solo para create; el update se hace campo a campo en el service. */
@Component
public class ProductoMapper {

    /** El service ya validó y normalizó sku, categoría y especificaciones. El estado lo calcula el service. */
    public Producto toEntity(ProductoRequestDto dto, String sku, String categoria, Map<String, String> especificaciones) {
        Producto p = new Producto();
        p.setSku(sku);
        p.setNombre(dto.nombre().trim());
        p.setMarca(dto.marca().trim());
        p.setCategoria(categoria);
        p.setPrecioVenta(dto.precioVenta());
        p.setStock(dto.stock());
        p.setDescripcion(limpiar(dto.descripcion()));
        p.setImagenUrl(limpiar(dto.imagenUrl()));
        p.setImagenOfertaUrl(limpiar(dto.imagenOfertaUrl()));
        int orden = 0;
        for (Map.Entry<String, String> e : especificaciones.entrySet()) {
            p.agregarEspecificacion(e.getKey(), e.getValue(), orden++);
        }
        return p;
    }

    public ProductoResponseDto toResponse(Producto p) {
        Map<String, String> specs = new LinkedHashMap<>();
        for (ProductoEspecificacion e : p.getEspecificaciones()) {
            specs.put(e.getClave(), e.getValor());
        }
        return new ProductoResponseDto(p.getId(), p.getSku(), p.getNombre(), p.getMarca(), p.getCategoria(),
                p.getPrecioVenta(), p.getStock(), p.getEstado().name(), p.getImagenUrl(), p.getImagenOfertaUrl(),
                p.getDescripcion(), specs, p.getCreatedAt(), p.getUpdatedAt());
    }

    public static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
