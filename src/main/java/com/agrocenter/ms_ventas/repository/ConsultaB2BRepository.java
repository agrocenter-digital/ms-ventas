package com.agrocenter.ms_ventas.repository;

import com.agrocenter.ms_ventas.entity.ConsultaB2B;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ConsultaB2BRepository extends JpaRepository<ConsultaB2B, Long> {

    List<ConsultaB2B> findAllByOrderByFechaCreacionDesc();

    List<ConsultaB2B> findByEstadoOrderByFechaCreacionDesc(EstadoConsultaB2B estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConsultaB2B c where c.id = :id")
    Optional<ConsultaB2B> findByIdForUpdate(@Param("id") Long id);
}
