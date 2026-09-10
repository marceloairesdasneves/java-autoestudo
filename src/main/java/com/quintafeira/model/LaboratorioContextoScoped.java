package com.quintafeira.model;

// @author Marcelo Neves

import java.util.concurrent.StructuredTaskScope;

public class LaboratorioContextoScoped {

    // 1. Constantes globais de contexto imutável
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> CHAVE_FISCAL = ScopedValue.newInstance();

    public static void main(String[] args) throws Exception {
        System.out.println("=== TESTE DE PROPAGAÇÃO DE CONTEXTO: SCOPED VALUES ===");

        System.out.printf("Fora do escopo, TENANT está vinculado? %b%n", TENANT_ID.isBound());

        // 2. Vincula os valores e delimita a execução
        ScopedValue.where(TENANT_ID, "FILIAL_VALE_PARAIBA")
                .where(CHAVE_FISCAL, "NFE-2026-998811")
                .run(() -> {
                    System.out.printf("[Thread Principal] Iniciando sob Tenant: %s | Chave: %s%n",
                            TENANT_ID.get(), CHAVE_FISCAL.get());

                    processarFluxoConcorrente();
                });

        // 3. Verificação pós-execução (garantia contra Memory Leak)
        System.out.printf("Após o encerramento do bloco, TENANT está vinculado? %b%n", TENANT_ID.isBound());
        System.out.println("[SUCESSO] O contexto foi coletado automaticamente pela JVM sem .remove() manual!");
    }

    private static void processarFluxoConcorrente() {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Subtarefa 1 em Virtual Thread paralela herdando o contexto sem cópia de mapa
            var subtaskValidacao = scope.fork(() -> {
                System.out.printf("  [Virtual Thread 1 - Validador] Lendo Tenant: %s%n", TENANT_ID.get());
                return "Validação OK";
            });

            // Subtarefa 2 em Virtual Thread paralela acessando a mesma referência imutável
            var subtaskGravacao = scope.fork(() -> {
                System.out.printf("  [Virtual Thread 2 - Gravação Ledger] Gravando Chave: %s para Tenant: %s%n",
                        CHAVE_FISCAL.get(), TENANT_ID.get());
                return "Gravado com Sucesso";
            });

            scope.join();
            scope.throwIfFailed();

            System.out.printf("[Resultado Final]: %s | %s%n", subtaskValidacao.get(), subtaskGravacao.get());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}