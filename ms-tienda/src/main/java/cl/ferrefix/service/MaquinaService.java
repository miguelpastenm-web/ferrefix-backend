package cl.ferrefix.service;

import cl.ferrefix.model.dto.MaquinaRequestDto;
import cl.ferrefix.model.dto.MaquinaResponseDto;
import cl.ferrefix.model.dto.UnidadResponseDto;

import java.util.List;

public interface MaquinaService {

    /** Filtros opcionales; sort "campo[,asc|desc]" con campo en nombre, marca, categoria, tarifa, fecha. */
    List<MaquinaResponseDto> listar(String q, String categoria, boolean soloDisponibles, String sort);

    MaquinaResponseDto obtener(Long id);

    MaquinaResponseDto crear(MaquinaRequestDto request);

    MaquinaResponseDto actualizar(Long id, MaquinaRequestDto request);

    void eliminar(Long id);

    List<UnidadResponseDto> listarUnidades(Long maquinaId);

    List<UnidadResponseDto> agregarUnidades(Long maquinaId, int cantidad);

    void eliminarUnidad(Long maquinaId, Long unidadId);
}
