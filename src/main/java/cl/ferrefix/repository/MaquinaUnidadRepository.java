package cl.ferrefix.repository;

import cl.ferrefix.model.EstadoUnidad;
import cl.ferrefix.model.MaquinaUnidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Ids de las unidades de un modelo en un estado, ordenadas por código (la primera libre es la que se asigna). */
    @Query("select u.id from MaquinaUnidad u where u.maquina.id = :maquinaId and u.estado = :estado order by u.codigo asc")
    List<Long> idsPorEstado(@Param("maquinaId") Long maquinaId, @Param("estado") EstadoUnidad estado);

    /**
     * UPDATE atómico condicionado: solo cambia la unidad si SIGUE en el estado esperado. Devuelve 1 si lo logró y
     * 0 si otra transacción se le adelantó. No se usa clearAutomatically: limpiaría el contexto y dejaría
     * desconectado el modelo que ya está bloqueado y se va a modificar.
     */
    @Modifying(flushAutomatically = true)
    @Query("update MaquinaUnidad u set u.estado = :nuevo where u.id = :id and u.estado = :esperado")
    int cambiarEstado(@Param("id") Long id, @Param("esperado") EstadoUnidad esperado, @Param("nuevo") EstadoUnidad nuevo);
}
