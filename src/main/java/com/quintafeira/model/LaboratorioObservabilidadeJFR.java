package com.quintafeira.model;

// @author Marcelo Neves

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class LaboratorioObservabilidadeJFR {

    private static final Object LOCK_MONITOR = new Object();
    private static final ReentrantLock REENTRANT_LOCK = new ReentrantLock();

    public static void main(String[] args) throws Exception {
        System.out.println("=== DIAGNÓSTICO DE CONCORRÊNCIA: DETECÇÃO DE PINNING ===");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // 1. Simulação com ReentrantLock (Padrão Recomendado - Sem Pinning)
            executor.submit(() -> {
                executarComReentrantLock();
            });

            // Pequena pausa para separar as saídas
            Thread.sleep(200);

            // 2. Simulação com synchronized + I/O (Anti-padrão que causa Pinning)
            executor.submit(() -> {
                executarComSynchronizedBloqueante();
            });
        }

        Thread.sleep(500);
        System.out.println("\n[FIM DO TESTE] Se o flag -Djdk.tracePinnedThreads estiver ativo, o stack trace acima revela o pinning.");
    }

    private static void executarComReentrantLock() {
        REENTRANT_LOCK.lock();
        try {
            System.out.println("[ReentrantLock] Executando I/O com segurança... (Carrier Thread descolada)");
            Thread.sleep(Duration.ofMillis(100)); // I/O seguro: JVM desmonta a Virtual Thread
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            REENTRANT_LOCK.unlock();
        }
    }

    private static void executarComSynchronizedBloqueante() {
        synchronized (LOCK_MONITOR) {
            try {
                System.out.println("[Synchronized] Executando I/O dentro de monitor nativo... (Provoca Pinning!)");
                Thread.sleep(Duration.ofMillis(100)); // Bloqueio nativo: Carrier Thread presa à força!
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}