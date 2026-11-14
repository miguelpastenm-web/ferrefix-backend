package cl.ferrefix.service;

import cl.ferrefix.model.dto.*;

import java.util.List;

public interface CotizacionService {

    /**
     * @param emailAutenticado email del token si la petición traía un token válido; null si fue anónima.
     *                         Si el usuario no existe o está inactivo, la cotización se guarda igual con usuarioId nulo.
     */
    CotizacionCreadaResponseDto crear(String emailAutenticado, CrearCotizacionRequestDto request);

    List<CotizacionResponseDto> listar(String estado);

    CotizacionResponseDto obtener(Long id);

    CotizacionResponseDto actualizar(Long id, ActualizarCotizacionRequestDto request);

    void eliminar(Long id);
}
