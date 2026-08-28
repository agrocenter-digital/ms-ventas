package com.agrocenter.ms_ventas.repository;

import com.agrocenter.ms_ventas.entity.Venta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    @EntityGraph(attributePaths = "detalles")
    Optional<Venta> findWithDetallesById(Long id);

    @EntityGraph(attributePaths = "detalles")
    Optional<Venta> findByClienteIdAndIdempotencyKey(String clienteId, String idempotencyKey);

    Page<Venta> findByClienteIdOrderByFechaCreacionDesc(String clienteId, Pageable pageable);

    Page<Venta> findAllByOrderByFechaCreacionDesc(Pageable pageable);
}
