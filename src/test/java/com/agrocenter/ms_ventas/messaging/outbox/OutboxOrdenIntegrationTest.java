package com.agrocenter.ms_ventas.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OutboxOrdenIntegrationTest {
    @Autowired OutboxRepository repository;
    @Autowired TransactionTemplate transactions;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach void limpiar() { repository.deleteAll(); }

    @Test void reintentoBloqueaSoloEventosPosterioresDeLaMismaParticion() {
        Instant now = Instant.now();
        transactions.executeWithoutResult(status -> {
            var primero = new OutboxEvent("primero", "Creado", "1", "consulta:1", "{}", now);
            primero.fallido(now.plusSeconds(600), "error");
            repository.saveAndFlush(primero);
            repository.saveAndFlush(new OutboxEvent("segundo", "Actualizado", "1", "consulta:1", "{}", now));
            repository.saveAndFlush(new OutboxEvent("independiente", "Creado", "2", "consulta:2", "{}", now));
        });
        transactions.executeWithoutResult(status -> {
            var lista = repository.buscarPublicables(now.plusSeconds(5), PageRequest.of(0, 10));
            assertThat(lista).extracting(OutboxEvent::getEventId).containsExactly("independiente");
            lista.getFirst().enviado(now);
        });
        transactions.executeWithoutResult(status -> {
            var lista = repository.buscarPublicables(now.plusSeconds(1000), PageRequest.of(0, 10));
            assertThat(lista).extracting(OutboxEvent::getEventId).containsExactly("primero");
            lista.getFirst().enviado(now);
        });
        transactions.executeWithoutResult(status -> assertThat(repository.buscarPublicables(now.plusSeconds(1000), PageRequest.of(0, 10)))
                .extracting(OutboxEvent::getEventId).containsExactly("segundo"));
    }

    @Test void dosPublicadoresNoTomanElMismoEventoSimultaneamente() throws Exception {
        Instant now = Instant.now();
        repository.saveAndFlush(new OutboxEvent("unico", "Creado", "1", "consulta:1", "{}", now));
        var bloqueado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var segundoIniciado = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primero = executor.submit(() -> transactions.execute(status -> {
                var item = repository.buscarPublicables(now.plusSeconds(1), PageRequest.of(0, 1)).getFirst();
                bloqueado.countDown();
                try { if (!liberar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                item.enviado(now);
                return item.getEventId();
            }));
            assertThat(bloqueado.await(10, TimeUnit.SECONDS)).isTrue();
            var segundo = executor.submit(() -> transactions.execute(status -> {
                segundoIniciado.countDown();
                return repository.buscarPublicables(now.plusSeconds(1), PageRequest.of(0, 1)).stream()
                        .map(OutboxEvent::getEventId).toList();
            }));
            try {
                assertThat(segundoIniciado.await(10, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> segundo.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally { liberar.countDown(); }
            assertThat(primero.get(10, TimeUnit.SECONDS)).isEqualTo("unico");
            assertThat(segundo.get(10, TimeUnit.SECONDS)).doesNotContain("unico");
        }
    }
}
