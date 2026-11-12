package cl.ferrefix.repository;

import cl.ferrefix.model.EstadoUnidad;
import cl.ferrefix.model.MaquinaUnidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaquinaUnidadRepository extends JpaRepository<MaquinaUnidad, Long> {

    List<MaquinaUnidad> findByMaquinaIdOrderByCodigoAsc(Long maquinaId);

    Optional<MaquinaUnidad> findByIdAndMaquinaId(Long id, Long maquinaId);

    long countByMaquinaId(Long maquinaId);

    long countByMaquinaIdAndEstado(Long maquinaId, EstadoUnidad estado);

    /** Filas (maquinaId, estado, cantidad) para armar los conteos de varios modelos con una sola consulta. */
    @Query("select u.maquina.id, u.estado, count(u) from MaquinaUnidad u "
            + "where u.maquina.id in :ids group by u.maquina.id, u.estado")
    List<Object[]> contarPorMaquinaYEstado(@Param("ids") Collection<Long> ids);
}
