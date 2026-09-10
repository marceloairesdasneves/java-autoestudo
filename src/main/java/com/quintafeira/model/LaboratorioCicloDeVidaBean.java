package com.quintafeira.model;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class LaboratorioCicloDeVidaBean {

    public static void main(String[] args) {
        System.out.println("=== 1. INICIALIZANDO O APPLICATION CONTEXT ===");
        var context = new AnnotationConfigApplicationContext(ConfiguracaoTeste.class);

        System.out.println("\n=== 2. RECUPERANDO E UTILIZANDO O BEAN ===");
        var servico = context.getBean(ServicoAuditoria.class);
        servico.registrarTransacao("FAT-9999");

        System.out.println("\n=== 3. ENCERRANDO O CONTEXTO (DESTRUIÇÃO) ===");
        context.close();
    }

    @Configuration
    static class ConfiguracaoTeste {
        @Bean
        public ServicoAuditoria servicoAuditoria() {
            return new ServicoAuditoria();
        }

        @Bean
        public RastreadorCicloVida rastreadorCicloVida() {
            return new RastreadorCicloVida();
        }
    }

    static class ServicoAuditoria {
        public ServicoAuditoria() {
            System.out.println("[Ciclo de Vida] 1. Construtor invocado (Instanciação)");
        }

        @PostConstruct
        public void inicializar() {
            System.out.println("[Ciclo de Vida] 3. @PostConstruct invocado (Inicialização)");
        }

        public void registrarTransacao(String id) {
            System.out.println("[Operação] Transação " + id + " processada pelo bean.");
        }

        @PreDestroy
        public void finalizar() {
            System.out.println("[Ciclo de Vida] 5. @PreDestroy invocado (Liberação de recursos)");
        }
    }

    // Interceptor que audita a criação de qualquer Bean na JVM
    static class RastreadorCicloVida implements BeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof ServicoAuditoria) {
                System.out.println("[BeanPostProcessor] 2. postProcessBeforeInitialization para: " + beanName);
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof ServicoAuditoria) {
                System.out.println("[BeanPostProcessor] 4. postProcessAfterInitialization para: " + beanName + " (Ponto onde proxies AOP são injetados)");
            }
            return bean;
        }
    }
}