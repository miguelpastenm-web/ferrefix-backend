package cl.ferrefix.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "MAQUINA_ESPECIFICACIONES")
@Getter
@Setter
@NoArgsConstructor
public class MaquinaEspecificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MAQUINA_ID", nullable = false)
    private Maquina maquina;

    @Column(name = "CLAVE", nullable = false, length = 80)
    private String clave;

    @Column(name = "VALOR", nullable = false, length = 300)
    private String valor;

    @Column(name = "ORDEN", nullable = false)
    private Integer orden;
}
