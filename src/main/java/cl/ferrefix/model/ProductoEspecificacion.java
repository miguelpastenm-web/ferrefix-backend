package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PRODUCTO_ESPECIFICACIONES")
@Getter
@Setter
@NoArgsConstructor
public class ProductoEspecificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCTO_ID", nullable = false)
    private Producto producto;

    @Column(name = "CLAVE", nullable = false, length = 80)
    private String clave;

    @Column(name = "VALOR", nullable = false, length = 300)
    private String valor;

    @Column(name = "ORDEN", nullable = false)
    private Integer orden;
}
