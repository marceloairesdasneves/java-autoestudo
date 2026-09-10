package com.quintafeira.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LaboratorioTomcatVirtualThreads {

    public static void main(String[] args) {
        System.out.println("=== CENÁRIO 1: SPRING BOOT PADRÃO (PLATFORM THREADS) ===");
        executarCenario(false);

        System.out.println("\n=== CENÁRIO 2: SPRING BOOT 3 COM VIRTUAL THREADS ATIVAS ===");
        executarCenario(true);
    }

    private static void executarCenario(boolean habilitarVirtualThreads) {
        var context = new AnnotationConfigApplicationContext();

        // Simula o efeito de 'spring.threads.virtual.enabled' no application.properties
        var props = Map.<String, Object>of(
                "spring.threads.virtual.enabled", String.valueOf(habilitarVirtualThreads)
        );
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("props-loom", props));

        context.register(ConfiguracaoServidorLoom.class);
        context.refresh();

        var conector = context.getBean(ConectorServidorHttp.class);
        conector.atenderRequisicao("/api/v1/faturas/FAT-001");

        context.close();
    }

    @Configuration
    static class ConfiguracaoServidorLoom {

        // Ativado quando spring.threads.virtual.enabled=true
        @Bean
        @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true")
        public ConectorServidorHttp conectorVirtualThreads() {
            System.out.println("[IoC Container] Configurando Executor do Servidor com Executors.newVirtualThreadPerTaskExecutor()");
            return new ConectorServidorHttp(Executors.newVirtualThreadPerTaskExecutor());
        }

        // Fallback padrão quando spring.threads.virtual.enabled=false
        @Bean
        @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "false", matchIfMissing = true)
        public ConectorServidorHttp conectorPlatformThreads() {
            System.out.println("[IoC Container] Configurando Executor do Servidor com Pool Fixo Clássico (200 Platform Threads)");
            return new ConectorServidorHttp(Executors.newFixedThreadPool(200));
        }
    }

    // Abstração do conector HTTP que despacha requisições web
    static record ConectorServidorHttp(Executor executor) {
        public void atenderRequisicao(String uri) {
            executor.execute(() -> {
                Thread atual = Thread.currentThread();
                System.out.printf("  [HTTP INBOUND] Requisição '%s' processada por: %s | É Virtual Thread? %b%n",
                        uri, atual, atual.isVirtual());
            });
        }
    }
}