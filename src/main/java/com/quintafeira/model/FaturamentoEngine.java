package com.quintafeira.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class FaturamentoEngine {

    // =========================================================================
    // 1. HIERARQUIA SELADA DE EVENTOS (MODELO EXPANDIDO)
    // =========================================================================
    public sealed interface EventoFaturamento permits
            EventoFaturamento.FaturaCriada,
            EventoFaturamento.FaturaPaga,
            EventoFaturamento.FaturaCancelada,
            EventoFaturamento.FaturaContestada,
            EventoFaturamento.FaturaRenegociada { // <-- Novo evento corporativo!

        UUID idEvento();
        UUID idFatura();
        Instant timestamp();

        record FaturaCriada(
                UUID idEvento,
                UUID idFatura,
                Instant timestamp,
                String documentoCliente,
                BigDecimal valorTotal
        ) implements EventoFaturamento {
            public FaturaCriada {
                if (valorTotal == null || valorTotal.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Valor da fatura deve ser estritamente positivo.");
                }
                if (documentoCliente == null || documentoCliente.isBlank()) {
                    throw new IllegalArgumentException("Documento do cliente é obrigatório.");
                }
            }
        }

        record FaturaPaga(
                UUID idEvento,
                UUID idFatura,
                Instant timestamp,
                String metodoPagamento,
                String codigoAutenticacao
        ) implements EventoFaturamento {}

        record FaturaCancelada(
                UUID idEvento,
                UUID idFatura,
                Instant timestamp,
                String motivoCancelamento
        ) implements EventoFaturamento {}

        record FaturaContestada(
                UUID idEvento,
                UUID idFatura,
                Instant timestamp,
                BigDecimal valorContestado,
                String protocoloDisputa
        ) implements EventoFaturamento {}

        // NOVO EVENTO: Renegociação com divisão em novas parcelas
        record FaturaRenegociada(
                UUID idEvento,
                UUID idFaturaOriginal,
                Instant timestamp,
                int quantidadeParcelas,
                BigDecimal novoValorTotal,
                List<UUID> idsNovasFaturas
        ) implements EventoFaturamento {
            public UUID idFatura() {
                return idFaturaOriginal;
            }

            public FaturaRenegociada {
                if (quantidadeParcelas < 2) {
                    throw new IllegalArgumentException("Renegociação exige no mínimo 2 parcelas.");
                }
                if (novoValorTotal == null || novoValorTotal.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Novo valor renegociado inválido.");
                }
            }
        }
    }

    // =========================================================================
    // 2. RESULTADO FUNCIONAL SELADO (O FIM DO TRY/CATCH PARA ERRO DE NEGÓCIO)
    // Em arquiteturas limpas, o resultado de uma operação é tipado!
    // =========================================================================
    public sealed interface ResultadoProcessamento permits
            ResultadoProcessamento.ProcessadoComSucesso,
            ResultadoProcessamento.RejeitadoPorRegra {

        record ProcessadoComSucesso(UUID idEvento, String acaoContabil, Instant executadoEm)
                implements ResultadoProcessamento {}

        record RejeitadoPorRegra(UUID idEvento, String codigoMotivo, String detalhe)
                implements ResultadoProcessamento {}
    }

    // =========================================================================
    // 3. MOTOR DE PROCESSAMENTO CONTÁBIL COM PATTERN MATCHING
    // =========================================================================
    public static class ProcessadorContabil {

        public ResultadoProcessamento processar(EventoFaturamento evento) {
            // Switch Expression com Pattern Matching do Java 21
            return switch (evento) {
                case EventoFaturamento.FaturaCriada criada ->
                        new ResultadoProcessamento.ProcessadoComSucesso(
                                criada.idEvento(),
                                String.format("DÉBITO: Clientes a Receber | CRÉDITO: Receita Bruta [R$ %s] (Cliente: %s)",
                                        criada.valorTotal(), criada.documentoCliente()),
                                Instant.now()
                        );

                case EventoFaturamento.FaturaPaga paga ->
                        new ResultadoProcessamento.ProcessadoComSucesso(
                                paga.idEvento(),
                                String.format("DÉBITO: Banco Conta Movimento | CRÉDITO: Clientes a Receber [Fatura: %s via %s]",
                                        paga.idFatura(), paga.metodoPagamento()),
                                Instant.now()
                        );

                case EventoFaturamento.FaturaCancelada cancelada ->
                        new ResultadoProcessamento.ProcessadoComSucesso(
                                cancelada.idEvento(),
                                String.format("ESTORNO CONTÁBIL: Reversão de receita da fatura %s. Motivo: %s",
                                        cancelada.idFatura(), cancelada.motivoCancelamento()),
                                Instant.now()
                        );

                case EventoFaturamento.FaturaContestada contestada ->
                        new ResultadoProcessamento.ProcessadoComSucesso(
                                contestada.idEvento(),
                                String.format("PROVISÃO DE PERDA ESTIMADA: R$ %s bloqueados para disputa judicial (Ref: %s)",
                                        contestada.valorContestado(), contestada.protocoloDisputa()),
                                Instant.now()
                        );

                case EventoFaturamento.FaturaRenegociada renegociada ->
                        new ResultadoProcessamento.ProcessadoComSucesso(
                                renegociada.idEvento(),
                                String.format("REPACTUAÇÃO: Baixa de título original %s e emissão de %d novas parcelas somando R$ %s",
                                        renegociada.idFatura(), renegociada.quantidadeParcelas(), renegociada.novoValorTotal()),
                                Instant.now()
                        );
            };
        }
    }

    // =========================================================================
    // 4. EXECUÇÃO OPERACIONAL DA TARDE
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("=== MOTOR CONTÁBIL EXECUTIVO - ULTRA-APRENDIZADO (JAVA 21) ===\n");

        ProcessadorContabil processador = new ProcessadorContabil();
        UUID faturaOriginal = UUID.randomUUID();

        // Cenário 1: Fatura Criada
        var eventoCriacao = new EventoFaturamento.FaturaCriada(
                UUID.randomUUID(), faturaOriginal, Instant.now(), "00.123.456/0001-99", new BigDecimal("12000.00")
        );

        // Cenário 2: Renegociação com novas parcelas
        var eventoRenegociacao = new EventoFaturamento.FaturaRenegociada(
                UUID.randomUUID(),
                faturaOriginal,
                Instant.now(),
                2,
                new BigDecimal("12600.00"), // Com juros de repactuação
                List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );

        executarEExibir(processador, eventoCriacao);
        executarEExibir(processador, eventoRenegociacao);
    }

    private static void executarEExibir(ProcessadorContabil processador, EventoFaturamento evento) {
        ResultadoProcessamento resultado = processador.processar(evento);

        // Pattern matching também para inspecionar o resultado funcional!
        switch (resultado) {
            case ResultadoProcessamento.ProcessadoComSucesso sucesso ->
                    System.out.printf("✔ [CONTABILIZADO] Evento: %s\n  Lançamento: %s\n\n",
                            sucesso.idEvento(), sucesso.acaoContabil());

            case ResultadoProcessamento.RejeitadoPorRegra rejeitado ->
                    System.err.printf("✖ [REJEITADO] Código: %s | Detalhe: %s\n\n",
                            rejeitado.codigoMotivo(), rejeitado.detalhe());
        }
    }
}