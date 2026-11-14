package cl.ferrefix.repository;

import cl.ferrefix.model.Orden;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdenRepository extends JpaRepository<Orden, Long>, JpaSpecificationExecutor<Orden> {

    List<Orden> findByUsuarioIdOrderByCreatedAtDescIdDesc(Long usuarioId);

    /** SELECT ... FOR UPDATE WAIT 10 de la orden: serializa cambios de estado, cancelaciones y borrados de una misma orden. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("select o from Orden o where o.id = :id")
    Optional<Orden> findByIdForUpdate(@Param("id") Long id);
}
