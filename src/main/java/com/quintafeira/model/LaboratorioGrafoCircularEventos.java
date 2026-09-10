package com.quintafeira.model;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;

public class LaboratorioGrafoCircularEventos {

    public static void main(String[] args) {
        System.out.println("=== 1. INICIALIZANDO CONTEXTO COM GRAFO DESACOPLADO (DAG) ===");
        var context = new AnnotationConfigApplicationContext(ConfiguracaoEventos.class);

        System.out.println("\n=== 2. DISPARANDO FLUXO DE PAGAMENTO ===");
        var servicoPagamento = context.getBean(ServicoPagamento.class);
        servicoPagamento.processarPagamento("FAT-100", new BigDecimal("1500.00"));

        context.close();
    }

    // 1. Evento de Domínio Imutável (Record)
    public record FaturaPagaEvent(String faturaId, BigDecimal valor) {}

    // 2. Serviço de Pagamento: Depende apenas do Publisher (Grafo Unidirecional)
    public record ServicoPagamento(ApplicationEventPublisher eventPublisher) {

        public void processarPagamento(String faturaId, BigDecimal valor) {
            System.out.println("[Pagamento] Liquidando fatura: " + faturaId + " no valor de R$ " + valor);
            // Em vez de chamar o ServicoNotificacao diretamente, publica o evento
            eventPublisher.publishEvent(new FaturaPagaEvent(faturaId, valor));
        }
    }

    // 3. Serviço de Notificação: Escuta o evento sem que ServicoPagamento saiba da sua existência
    public record ServicoNotificacao() {

        @EventListener
        public void onFaturaPaga(FaturaPagaEvent evento) {
            System.out.printf("  [Notificação/Audit] Notificando cliente da fatura %s | Valor: R$ %.2f%n",
                    evento.faturaId(), evento.valor());
        }
    }

    // 4. Configuração do Container
    @Configuration
    static class ConfiguracaoEventos {

        @Bean
        public ServicoPagamento servicoPagamento(ApplicationEventPublisher publisher) {
            return new ServicoPagamento(publisher);
        }

        @Bean
        public ServicoNotificacao servicoNotificacao() {
            return new ServicoNotificacao();
        }
    }
}
