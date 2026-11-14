package cl.ferrefix.model.mapper;

import cl.ferrefix.model.Maquina;
import cl.ferrefix.model.MaquinaEspecificacion;
import cl.ferrefix.model.MaquinaUnidad;
import cl.ferrefix.model.dto.MaquinaRequestDto;
import cl.ferrefix.model.dto.MaquinaResponseDto;
import cl.ferrefix.model.dto.UnidadResponseDto;
import cl.ferrefix.util.Textos;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mapper manual. toEntity solo para create; el update se hace campo a campo en el service. */
@Component
public class MaquinaMapper {

    /** El service ya validó y normalizó código y especificaciones. El estado arranca NO_DISPONIBLE (sin unidades). */
    public Maquina toEntity(MaquinaRequestDto dto, String codigo, Map<String, String> especificaciones) {
        Maquina m = new Maquina();
        m.setCodigo(codigo);
        m.setNombre(dto.nombre().trim());
        m.setMarca(dto.marca().trim());
        m.setCategoria(dto.categoria().trim());
        m.setTarifaDia(dto.tarifaDia());
        m.setIncluye(Textos.limpiar(dto.incluye()));
        m.setImagenUrl(Textos.limpiar(dto.imagenUrl()));
        int orden = 0;
        for (Map.Entry<String, String> e : especificaciones.entrySet()) {
            m.agregarEspecificacion(e.getKey(), e.getValue(), orden++);
        }
        return m;
    }

    public MaquinaResponseDto toResponse(Maquina m, long unidadesTotales, long unidadesDisponibles) {
        Map<String, String> specs = new LinkedHashMap<>();
        for (MaquinaEspecificacion e : m.getEspecificaciones()) {
            specs.put(e.getClave(), e.getValor());
        }
        return new MaquinaResponseDto(m.getId(), m.getCodigo(), m.getNombre(), m.getMarca(), m.getCategoria(),
                m.getTarifaDia(), m.getEstado().name(), m.getIncluye(), m.getImagenUrl(), specs,
                unidadesTotales, unidadesDisponibles, m.getCreatedAt(), m.getUpdatedAt());
    }

    public UnidadResponseDto toResponse(MaquinaUnidad u) {
        return new UnidadResponseDto(u.getId(), u.getMaquina().getId(), u.getCodigo(), u.getEstado().name(), u.getCreatedAt());
    }
}
