package com.agrocenter.ms_ventas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(
        name = "consulta_b2b",
        indexes = {
                @Index(name = "idx_consulta_b2b_estado", columnList = "estado"),
                @Index(name = "idx_consulta_b2b_fecha_creacion", columnList = "fecha_creacion"),
                @Index(name = "idx_consulta_b2b_correo", columnList = "correo_contacto")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultaB2B {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku_producto", length = 100)
    private String skuProducto;

    @Column(name = "nombre_producto", length = 255)
    private String nombreProducto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "nombre_cliente", nullable = false, length = 150)
    private String nombreCliente;

    @Column(name = "correo_contacto", nullable = false, length = 150)
    private String correoContacto;

    @Column(nullable = false, columnDefinition = "text")
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrigenConsultaB2B origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoConsultaB2B estado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    public ConsultaB2B(
            String skuProducto,
            String nombreProducto,
            Integer cantidad,
            String nombreCliente,
            String correoContacto,
            String mensaje,
            OrigenConsultaB2B origen
    ) {
        this.skuProducto = skuProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad == null || cantidad < 1 ? 1 : cantidad;
        this.nombreCliente = nombreCliente;
        this.correoContacto = correoContacto;
        this.mensaje = mensaje;
        this.origen = origen;
        this.estado = EstadoConsultaB2B.PENDIENTE;
    }

    public void actualizarEstado(EstadoConsultaB2B nuevoEstado) {
        this.estado = nuevoEstado;
    }

    @PrePersist
    void prePersist() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = Instant.now();
        }
        if (this.estado == null) {
            this.estado = EstadoConsultaB2B.PENDIENTE;
        }
        if (this.cantidad == null || this.cantidad < 1) {
            this.cantidad = 1;
        }
    }
}
