package cl.ferrefix.repository;

import cl.ferrefix.model.Arriendo;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArriendoRepository extends JpaRepository<Arriendo, Long>, JpaSpecificationExecutor<Arriendo> {

    @EntityGraph(attributePaths = {"unidad", "unidad.maquina", "usuario"})
    List<Arriendo> findByUsuarioIdOrderByCreatedAtDescIdDesc(Long usuarioId);

    @Override
    @EntityGraph(attributePaths = {"unidad", "unidad.maquina", "usuario"})
    List<Arriendo> findAll(Specification<Arriendo> spec, Sort sort);

    @EntityGraph(attributePaths = {"unidad", "unidad.maquina", "usuario"})
    @Query("select a from Arriendo a where a.id = :id")
    Optional<Arriendo> findDetalleById(@Param("id") Long id);

    /**
     * Solo el id del modelo (valor escalar, NO la entidad): sirve para saber qué modelo bloquear sin dejar el
     * arriendo en el contexto de persistencia. Así, tras bloquear, el arriendo se lee fresco de la base.
     */
    @Query("select a.unidad.maquina.id from Arriendo a where a.id = :id")
    Optional<Long> findMaquinaIdById(@Param("id") Long id);

    boolean existsByUnidadId(Long unidadId);

    boolean existsByUnidadMaquinaId(Long maquinaId);
}
