package com.quintafeira.model;

// @author Marcelo Neves

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class TesteCargaLedger {

    private static final int TOTAL_TRANSACAOES = 50_000;
    private static final Semaphore POOL_BANCO_MOCK = new Semaphore(20);

    public static void main(String[] args) {
        System.out.println("=== INICIANDO TESTE DE CARGA DE ALTA CONCORRÊNCIA ===");
        System.out.printf("Carga total planejada: %,d Virtual Threads simultâneas%n", TOTAL_TRANSACAOES);
        System.out.println("Limite de concorrência com o banco: 20 conexões fixas\n");

        var sucessoCount = new LongAdder();
        var falhaCount = new AtomicInteger(0);

        long memoriaInicialMb = obterMemoriaUsadaMb();
        Instant inicio = Instant.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= TOTAL_TRANSACAOES; i++) {
                final int idTransacao = i;
                executor.submit(() -> {
                    try {
                        executarTransacaoContabil(idTransacao);
                        sucessoCount.increment();
                    } catch (Exception e) {
                        falhaCount.incrementAndGet();
                    }
                });
            }
        } // O try-with-resources bloqueia até as 50.000 tarefas completarem

        Instant fim = Instant.now();
        long duracaoMs = Duration.between(inicio, fim).toMillis();
        long memoriaFinalMb = obterMemoriaUsadaMb();

        double throughputPorSegundo = (TOTAL_TRANSACAOES / (double) duracaoMs) * 1000.0;

        System.out.println("\n--- MÉTRICAS FINAIS DE EXECUÇÃO ---");
        System.out.printf("Tempo Total: %d ms (%.2f segundos)%n", duracaoMs, duracaoMs / 1000.0);
        System.out.printf("Throughput: %,.2f transações/segundo%n", throughputPorSegundo);
        System.out.printf("Transações com Sucesso: %,d%n", sucessoCount.sum());
        System.out.printf("Transações com Falha: %d%n", falhaCount.get());
        System.out.printf("Delta de Memória Heap estimada: +%d MB%n", (memoriaFinalMb - memoriaInicialMb));
    }

    private static void executarTransacaoContabil(int id) throws InterruptedException {
        // Simula I/O bloqueante contido pela barreira do pool
        POOL_BANCO_MOCK.acquire();
        try {
            // I/O simulado de 10ms (leitura/escrita no Ledger)
            Thread.sleep(Duration.ofMillis(10));
        } finally {
            POOL_BANCO_MOCK.release();
        }
    }

    private static long obterMemoriaUsadaMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
}