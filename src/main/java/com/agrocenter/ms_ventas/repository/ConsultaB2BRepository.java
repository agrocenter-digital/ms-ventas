package com.agrocenter.ms_ventas.repository;

import com.agrocenter.ms_ventas.entity.ConsultaB2B;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultaB2BRepository extends JpaRepository<ConsultaB2B, Long> {

    List<ConsultaB2B> findAllByOrderByFechaCreacionDesc();

    List<ConsultaB2B> findByEstadoOrderByFechaCreacionDesc(EstadoConsultaB2B estado);
}
