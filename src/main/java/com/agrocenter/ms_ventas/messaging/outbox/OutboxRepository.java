package com.agrocenter.ms_ventas.messaging.outbox;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from OutboxEvent e
            where e.sentAt is null and e.nextAttemptAt <= :now
              and not exists (select 1 from OutboxEvent anterior
                              where anterior.partitionKey = e.partitionKey
                                and anterior.sentAt is null and anterior.orden < e.orden)
            order by e.orden
            """)
    List<OutboxEvent> buscarPublicables(@Param("now") Instant now, Pageable pageable);
}
