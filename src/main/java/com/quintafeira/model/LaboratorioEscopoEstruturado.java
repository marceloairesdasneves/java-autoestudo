package com.quintafeira.model;

// @author Marcelo Neves

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.StructuredTaskScope;

public class LaboratorioEscopoEstruturado {

    public static void main(String[] args) {
        System.out.println("=== TESTE DE CONCORRÊNCIA ESTRUTURADA: SHUTDOWN ON FAILURE ===");
        Instant inicio = Instant.now();

        try {
            executarVerificacoesFiscais("12.345.678/0001-90");
        } catch (Exception e) {
            System.out.printf("%n[FALHA CONTROLADA] A operação falhou com a mensagem: %s%n", e.getMessage());
        }

        Instant fim = Instant.now();
        long duracaoMs = Duration.between(inicio, fim).toMillis();

        System.out.printf("Tempo total de execução: %d ms%n", duracaoMs);
        if (duracaoMs < 1000) {
            System.out.println("[SUCESSO ARQUITETURAL] A tarefa lenta de 3s foi cancelada imediatamente após a falha de 300ms!");
        } else {
            System.out.println("[ALERTA] A tarefa lenta não foi interrompida.");
        }
    }

    private static void executarVerificacoesFiscais(String cnpj) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Subtarefa 1: Consulta Lenta ao Bureau de Crédito (3 segundos)
            var subtaskBureau = scope.fork(() -> {
                System.out.println("[Bureau] Iniciando verificação de crédito...");
                Thread.sleep(Duration.ofSeconds(3));
                System.out.println("[Bureau] Finalizado com sucesso.");
                return "Score: 850";
            });

            // Subtarefa 2: Consulta à SEFAZ (Falha em 300ms)
            var subtaskSefaz = scope.fork(() -> {
                System.out.println("[SEFAZ] Iniciando consulta cadastral...");
                Thread.sleep(Duration.ofMillis(300));
                System.out.println("[SEFAZ] Detectada inconsistência cadastral fatal!");
                throw new IllegalStateException("CNPJ Bloqueado na SEFAZ para emissão de faturas.");
            });

            // Ponto de encontro: bloqueia até todas terminarem ou a primeira falhar
            scope.join();

            // Se alguma falhou, relança a exceção aqui
            scope.throwIfFailed();

            // Se ambas tivessem sucesso:
            System.out.println("Resultado: " + subtaskBureau.get() + " | " + subtaskSefaz.get());
        }
    }
}