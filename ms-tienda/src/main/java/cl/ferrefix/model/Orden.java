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
@Table(name = "ORDENES")
@Getter
@Setter
@NoArgsConstructor
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "CLIENTE_RUT", nullable = false, updatable = false, length = 12)
    private String clienteRut;

    @Column(name = "CLIENTE_NOMBRE", nullable = false, updatable = false, length = 120)
    private String clienteNombre;

    @Column(name = "CLIENTE_EMAIL", nullable = false, updatable = false, length = 120)
    private String clienteEmail;

    @Column(name = "TELEFONO_CONTACTO", nullable = false, length = 20)
    private String telefonoContacto;

    @Column(name = "DIRECCION_ENVIO", nullable = false, length = 200)
    private String direccionEnvio;

    @Column(name = "REGION_ENVIO", nullable = false, length = 80)
    private String regionEnvio;

    @Column(name = "COMUNA_ENVIO", nullable = false, length = 80)
    private String comunaEnvio;

    @Column(name = "INDICACIONES", length = 500)
    private String indicaciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "METODO_PAGO", nullable = false, updatable = false, length = 20)
    private MetodoPago metodoPago;

    @Column(name = "SUBTOTAL", nullable = false, updatable = false)
    private Long subtotal;

    @Column(name = "COSTO_ENVIO", nullable = false, updatable = false)
    private Integer costoEnvio;

    @Column(name = "TOTAL", nullable = false, updatable = false)
    private Long total;

    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoOrden estado = EstadoOrden.PAGADA;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<OrdenItem> items = new ArrayList<>();

    public void agregarItem(OrdenItem item) {
        item.setOrden(this);
        items.add(item);
    }
}
