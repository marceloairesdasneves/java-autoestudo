package com.quintafeira.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public class LaboratorioInjecaoModerna {

    public static void main(String[] args) {
        System.out.println("=== TESTE 1: AMBIENTE DE PRODUÇÃO (GATEWAY REAL) ===");
        executarCenario("producao");

        System.out.println("\n=== TESTE 2: AMBIENTE DE HOMOLOGAÇÃO (GATEWAY MOCK) ===");
        executarCenario("homologacao");
    }

    private static void executarCenario(String ambiente) {
        var context = new AnnotationConfigApplicationContext();

        // Simula a injeção de propriedades do application.properties
        var properties = Map.<String, Object>of("integracao.gateway.ambiente", ambiente);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("props-teste", properties));

        // Registra as classes e inicializa o container
        context.register(ConfiguracaoGateways.class, ProcessadorPagamentos.class);
        context.refresh();

        var processador = context.getBean(ProcessadorPagamentos.class);
        processador.executar("FAT-100");

        context.close();
    }

    // 1. Contrato
    interface GatewayPagamento {
        void enviarCobranca(String faturaId);
    }

    // 2. Implementação de Produção
    static class GatewayProducao implements GatewayPagamento {
        @Override
        public void enviarCobranca(String faturaId) {
            System.out.println("[PROD] Cobrança enviada ao Gateway Bancário Real: " + faturaId);
        }
    }

    // 3. Implementação Mock (Homologação / Local)
    static class GatewayMock implements GatewayPagamento {
        @Override
        public void enviarCobranca(String faturaId) {
            System.out.println("[MOCK] Simulando autorização de pagamento sem custo de API: " + faturaId);
        }
    }

    // 4. Configuração com Carregamento Condicional
    @Configuration
    static class ConfiguracaoGateways {

        @Bean
        @ConditionalOnProperty(name = "integracao.gateway.ambiente", havingValue = "producao")
        public GatewayPagamento gatewayProducao() {
            return new GatewayProducao();
        }

        @Bean
        @ConditionalOnProperty(name = "integracao.gateway.ambiente", havingValue = "homologacao", matchIfMissing = true)
        public GatewayPagamento gatewayMock() {
            return new GatewayMock();
        }
    }

    // 5. Injeção de Dependência Moderna via Record (Imutável, seguro e conciso)
    static record ProcessadorPagamentos(GatewayPagamento gateway) {
        public void executar(String faturaId) {
            System.out.println("[Processador] Validando regras locais da fatura " + faturaId);
            gateway.enviarCobranca(faturaId);
        }
    }
}