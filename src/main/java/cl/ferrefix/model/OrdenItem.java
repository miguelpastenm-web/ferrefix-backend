package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Línea de una orden. Guarda copia de sku, nombre y precio: sigue siendo válida aunque el producto cambie o se elimine. */
@Entity
@Table(name = "ORDEN_ITEMS")
@Getter
@Setter
@NoArgsConstructor
public class OrdenItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ORDEN_ID", nullable = false, updatable = false)
    private Orden orden;

    /** Null cuando el producto fue eliminado (la base lo deja en NULL con ON DELETE SET NULL). */
    @Column(name = "PRODUCTO_ID")
    private Long productoId;

    @Column(name = "SKU", nullable = false, updatable = false, length = 30)
    private String sku;

    @Column(name = "NOMBRE", nullable = false, updatable = false, length = 150)
    private String nombre;

    @Column(name = "PRECIO_UNITARIO", nullable = false, updatable = false)
    private Integer precioUnitario;

    @Column(name = "CANTIDAD", nullable = false, updatable = false)
    private Integer cantidad;

    @Column(name = "SUBTOTAL", nullable = false, updatable = false)
    private Long subtotal;
}
