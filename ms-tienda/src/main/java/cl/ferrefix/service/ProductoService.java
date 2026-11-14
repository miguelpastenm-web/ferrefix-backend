package cl.ferrefix.service;

import cl.ferrefix.model.dto.ProductoRequestDto;
import cl.ferrefix.model.dto.ProductoResponseDto;

import java.util.List;

public interface ProductoService {

    /** Filtros opcionales; sort con formato "campo[,asc|desc]" (nombre, marca, categoria, precio, stock, fecha). */
    List<ProductoResponseDto> listar(String q, String categoria, String marca, String sort);

    ProductoResponseDto obtener(Long id);

    ProductoResponseDto crear(ProductoRequestDto request);

    ProductoResponseDto actualizar(Long id, ProductoRequestDto request);

    void eliminar(Long id);
}
