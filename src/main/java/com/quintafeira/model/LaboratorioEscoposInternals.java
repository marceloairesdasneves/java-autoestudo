package com.quintafeira.model;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.UUID;

public class LaboratorioEscoposInternals {

    public static void main(String[] args) {
        System.out.println("=== TESTE DE ESCOPOS: SINGLETON vs PROTOTYPE COM OBJECTPROVIDER ===");
        var context = new AnnotationConfigApplicationContext(ConfiguracaoEscopos.class);

        var ledgerService = context.getBean(LedgerService.class);

        System.out.println("\n--- Execução 1 da Fatura ---");
        ledgerService.processarFatura("FAT-001");

        System.out.println("\n--- Execução 2 da Fatura ---");
        ledgerService.processarFatura("FAT-002");

        context.close();
    }

    @Configuration
    static class ConfiguracaoEscopos {

        // Bean com estado transiente mutável - deve ser recriado sempre
        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        public ContextoOperacaoTransiente contextoOperacao() {
            return new ContextoOperacaoTransiente();
        }

        @Bean
        public LedgerService ledgerService(ObjectProvider<ContextoOperacaoTransiente> provider) {
            return new LedgerService(provider);
        }
    }

    static class ContextoOperacaoTransiente {
        private final String sessionId = UUID.randomUUID().toString();

        public ContextoOperacaoTransiente() {
            System.out.println("  [Prototype] Novo ContextoOperacao instanciado: " + sessionId);
        }

        public String getSessionId() {
            return sessionId;
        }
    }

    static class LedgerService {
        private final ObjectProvider<ContextoOperacaoTransiente> contextoProvider;

        public LedgerService(ObjectProvider<ContextoOperacaoTransiente> contextoProvider) {
            this.contextoProvider = contextoProvider;
            System.out.println("[Singleton] LedgerService inicializado (instância única).");
        }

        public void processarFatura(String faturaId) {
            // Obtém uma instância exclusiva do prototype sob demanda
            var contexto = contextoProvider.getObject();
            System.out.printf("  [Operacao] Processando %s usando o SessionId: %s%n",
                    faturaId, contexto.getSessionId());
        }
    }
}