package cl.ferrefix.repository;

import cl.ferrefix.model.Producto;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    /** Solo los ids que existen (valores escalares, NO entidades: no deja filas desactualizadas en el contexto). */
    @Query("select p.id from Producto p where p.id in :ids")
    List<Long> idsExistentes(@Param("ids") Collection<Long> ids);

    /**
     * Descuento atómico de stock. El CHECK CK_PRODUCTOS_ESTADO_STOCK exige que el mismo UPDATE cambie también el
     * ESTADO (stock 0 = AGOTADO), y el WHERE STOCK >= :n impide que baje de 0. Devuelve 1 si descontó y 0 si no
     * alcanzaba (o el producto no existe). En Oracle los valores del SET son los de ANTES de la sentencia, por eso
     * "STOCK - :n = 0" es la condición correcta. Si otra transacción retiene la fila más de 10 s, la consulta
     * vence (QueryTimeoutException) y la API responde 409 "reintente".
     */
    @Modifying(flushAutomatically = true)
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "10000"))
    @Query(nativeQuery = true, value = "UPDATE PRODUCTOS SET STOCK = STOCK - :n, "
            + "ESTADO = CASE WHEN STOCK - :n = 0 THEN 'AGOTADO' ELSE 'DISPONIBLE' END, UPDATED_AT = SYSTIMESTAMP "
            + "WHERE ID = :id AND STOCK >= :n")
    int descontarStock(@Param("id") Long id, @Param("n") int n);

    /** Devuelve stock al cancelar una orden. Sumar deja stock > 0, así que el estado vuelve a DISPONIBLE. 0 filas = producto eliminado. */
    @Modifying(flushAutomatically = true)
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "10000"))
    @Query(nativeQuery = true, value = "UPDATE PRODUCTOS SET STOCK = STOCK + :n, ESTADO = 'DISPONIBLE', UPDATED_AT = SYSTIMESTAMP WHERE ID = :id")
    int restaurarStock(@Param("id") Long id, @Param("n") int n);
}
