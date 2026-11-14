package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PRODUCTOS")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SKU", nullable = false, unique = true, length = 30)
    private String sku;

    @Column(name = "NOMBRE", nullable = false, length = 150)
    private String nombre;

    @Column(name = "MARCA", nullable = false, length = 60)
    private String marca;

    @Column(name = "CATEGORIA", nullable = false, length = 40)
    private String categoria;

    @Column(name = "PRECIO_VENTA", nullable = false)
    private Integer precioVenta;

    @Column(name = "STOCK", nullable = false)
    private Integer stock = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoProducto estado = EstadoProducto.AGOTADO;

    @Column(name = "IMAGEN_URL", length = 300)
    private String imagenUrl;

    @Column(name = "IMAGEN_OFERTA_URL", length = 300)
    private String imagenOfertaUrl;

    @Column(name = "DESCRIPCION", length = 2000)
    private String descripcion;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    @BatchSize(size = 50)
    private List<ProductoEspecificacion> especificaciones = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = ahora;
        }
        if (updatedAt == null) {
            updatedAt = ahora;
        }
    }

    /** Regla de negocio: stock 0 => AGOTADO; stock > 0 => DISPONIBLE. */
    public void recalcularEstado() {
        this.estado = (stock == null || stock <= 0) ? EstadoProducto.AGOTADO : EstadoProducto.DISPONIBLE;
    }

    public void agregarEspecificacion(String clave, String valor, int orden) {
        ProductoEspecificacion e = new ProductoEspecificacion();
        e.setProducto(this);
        e.setClave(clave);
        e.setValor(valor);
        e.setOrden(orden);
        especificaciones.add(e);
    }
}
