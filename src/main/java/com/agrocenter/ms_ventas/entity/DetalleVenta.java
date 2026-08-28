package com.agrocenter.ms_ventas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "detalles_venta",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_detalles_venta_producto",
                columnNames = {"venta_id", "producto_id"}
        ),
        indexes = @Index(name = "idx_detalles_venta_venta_id", columnList = "venta_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, updatable = false)
    private Venta venta;

    @Column(name = "producto_id", nullable = false, updatable = false)
    private Long productoId;

    @Column(nullable = false, length = 50, updatable = false)
    private String sku;

    @Column(name = "nombre_producto", nullable = false, length = 120, updatable = false)
    private String nombreProducto;

    @Column(nullable = false, updatable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal subtotal;

    public DetalleVenta(
            Long productoId,
            String sku,
            String nombreProducto,
            Integer cantidad,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {
        this.productoId = productoId;
        this.sku = sku;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = subtotal;
    }

    void asociarVenta(Venta venta) {
        this.venta = venta;
    }
}
