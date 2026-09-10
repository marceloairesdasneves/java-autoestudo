package com.quintafeira.model;

// @author Marcelo Neves

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;

public class ProcessadorContabilIntegrado {

    // --- PILAR 4: CONTEXTO IMUTÁVEL VIA SCOPED VALUES ---
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

    // --- PILAR 2: CONTENÇÃO DE RECURSOS FINITOS (BANCO DE DADOS) ---
    // Simula um pool HikariCP com no máximo 20 conexões ativas
    private static final Semaphore POOL_CONEXOES_BANCO = new Semaphore(20);

    // --- PILAR 1: DOMÍNIO SELADO E IMUTÁVEL ---
    public sealed interface EventoContabil permits FaturaCriada, FaturaPaga, FaturaCancelada {}

    public record FaturaCriada(String id, BigDecimal valor, String cnpjCliente) implements EventoContabil {
        public FaturaCriada {
            if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Valor da fatura criada deve ser positivo.");
            }
        }
    }

    public record FaturaPaga(String id, BigDecimal valor, String codigoAutorizacao) implements EventoContabil {}
    public record FaturaCancelada(String id, String motivo) implements EventoContabil {}

    public static void main(String[] args) {
        System.out.println("=== MOTOR CONTÁBIL CLEARPULSE: PIPELINE INTEGRADO ===");

        var loteEventos = List.of(
                new FaturaCriada("FAT-001", new BigDecimal("15000.00"), "12.345.678/0001-90"),
                new FaturaPaga("FAT-002", new BigDecimal("4200.50"), "AUTH-998811"),
                new FaturaCancelada("FAT-003", "Erro de emissão duplicada"),
                new FaturaCriada("FAT-004", new BigDecimal("89000.00"), "98.765.432/0001-10"),
                new FaturaPaga("FAT-005", new BigDecimal("1250.00"), "AUTH-443322")
        );

        var totalSucessos = new AtomicInteger(0);
        var totalFalhas = new AtomicInteger(0);
        Instant inicio = Instant.now();

        // 1. Delimita o contexto imutável do lote
        ScopedValue.where(TENANT_ID, "FILIAL_VALE_PARAIBA")
                .where(CORRELATION_ID, "BATCH-20260910-01")
                .run(() -> {
                    // 2. Concorrência Estruturada: Herda o ScopedValue automaticamente para todas as Virtual Threads!
                    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

                        for (var evento : loteEventos) {
                            scope.fork(() -> {
                                try {
                                    processarEvento(evento);
                                    totalSucessos.incrementAndGet();
                                } catch (Exception e) {
                                    System.err.printf("[ERRO] Transação abortada: %s%n", e.getMessage());
                                    totalFalhas.incrementAndGet();
                                }
                                return null;
                            });
                        }

                        scope.join(); // Aguarda todas as tarefas do lote
                        scope.throwIfFailed();

                    } catch (Exception e) {
                        System.err.printf("[ERRO NO LOTE]: %s%n", e.getMessage());
                    }
                });

        Instant fim = Instant.now();
        long duracaoMs = Duration.between(inicio, fim).toMillis();

        System.out.println("\n--- RESULTADO DA LIQUIDAÇÃO CONCORRENTE ---");
        System.out.printf("Tempo total de processamento: %d ms%n", duracaoMs);
        System.out.printf("Eventos processados com sucesso: %d%n", totalSucessos.get());
        System.out.printf("Eventos com inconsistência/falha: %d%n", totalFalhas.get());
    }

    private static void processarEvento(EventoContabil evento) throws Exception {
        // PILAR 1: Pattern Matching exaustivo sobre tipos selados
        switch (evento) {
            case FaturaCriada criada -> processarEmissaoComValidacaoEstruturada(criada);
            case FaturaPaga paga -> processarBaixaFinanceiraComSemaforo(paga);
            case FaturaCancelada cancelada -> registrarEstornoSimples(cancelada);
        }
    }

    // PILAR 3: Validações Concorrentes com StructuredTaskScope (Fail-Fast)
    private static void processarEmissaoComValidacaoEstruturada(FaturaCriada fatura) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Subtarefa 1: Validação de cadastro e conformidade fiscal
            var subtaskFiscal = scope.fork(() -> {
                Thread.sleep(Duration.ofMillis(80)); // Simula I/O com SEFAZ
                return "SEFAZ_REGULAR";
            });

            // Subtarefa 2: Avaliação de risco de crédito
            var subtaskRisco = scope.fork(() -> {
                Thread.sleep(Duration.ofMillis(60)); // Simula chamada a bureau
                return "RISCO_BAIXO";
            });

            scope.join();
            scope.throwIfFailed(); // Se uma falhar, aborta a outra imediatamente

            System.out.printf("[FATURA CRIADA] Id: %s | Valor: R$ %.2f | Tenant: %s | Fiscal: %s | Risco: %s%n",
                    fatura.id(), fatura.valor(), TENANT_ID.get(), subtaskFiscal.get(), subtaskRisco.get());
        }
    }

    // PILAR 2: Proteção de recursos do banco com Semaphore
    private static void processarBaixaFinanceiraComSemaforo(FaturaPaga fatura) throws InterruptedException {
        POOL_CONEXOES_BANCO.acquire(); // Garante que apenas 20 transações acessem o banco simultaneamente
        try {
            // Simula gravação de baixa no banco de dados (I/O bloqueante desmonta a Virtual Thread)
            Thread.sleep(Duration.ofMillis(50));
            System.out.printf("[FATURA PAGA] Id: %s | Valor: R$ %.2f | Auth: %s | Tenant: %s%n",
                    fatura.id(), fatura.valor(), fatura.codigoAutorizacao(), TENANT_ID.get());
        } finally {
            POOL_CONEXOES_BANCO.release();
        }
    }

    private static void registrarEstornoSimples(FaturaCancelada fatura) {
        System.out.printf("[FATURA CANCELADA] Id: %s | Motivo: %s | Tenant: %s%n",
                fatura.id(), fatura.motivo(), TENANT_ID.get());
    }
}