// @author Marcelo Neves
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LaboratorioConcorrencia {

    private static final int TOTAL_TAREFAS = 10_000;

    public static void main(String[] args) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        System.out.println("=== DIAGNÓSTICO DE PERFORMANCE JVM (JAVA 21) ===");
        System.out.println("Cores disponíveis: " + Runtime.getRuntime().availableProcessors());

        // Força uma limpeza prévia para baseline
        System.gc();
        long memoriaAntes = memoryBean.getHeapMemoryUsage().getUsed();
        int threadsNativasIniciais = threadBean.getThreadCount();

        System.out.printf("Threads do SO ativas antes do teste: %d%n", threadsNativasIniciais);
        System.out.printf("Memória Heap usada antes do teste: %.2f MB%n%n", memoriaAntes / (1024.0 * 1024.0));

        var tarefasConcluidas = new AtomicInteger(0);
        Instant inicio = Instant.now();

        int maxThreadsNativasDetectadas = threadsNativasIniciais;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= TOTAL_TAREFAS; i++) {
                final int idTarefa = i;
                executor.submit(() -> {
                    Thread.sleep(Duration.ofSeconds(1));
                    tarefasConcluidas.incrementAndGet();
                    return idTarefa;
                });
            }

            // Amostragem enquanto as 10.000 tarefas estão em voo
            for (int s = 0; s < 5; s++) {
                try {
                    Thread.sleep(200);
                    int threadsAgora = threadBean.getThreadCount();
                    if (threadsAgora > maxThreadsNativasDetectadas) {
                        maxThreadsNativasDetectadas = threadsAgora;
                    }
                } catch (InterruptedException ignored) {}
            }
        } // Aguarda todas finalizarem

        Instant fim = Instant.now();
        long duracaoMs = Duration.between(inicio, fim).toMillis();
        long memoriaDepois = memoryBean.getHeapMemoryUsage().getUsed();

        System.out.println("--- RESULTADOS DA MEDIÇÃO ---");
        System.out.printf("Tempo total de execução: %d ms (~%.2f segundos)%n", duracaoMs, duracaoMs / 1000.0);
        System.out.printf("Pico de Threads do Sistema Operacional (OS Threads): %d%n", maxThreadsNativasDetectadas);
        System.out.printf("Diferença de Heap consumido: %.2f MB%n", (memoriaDepois - memoriaAntes) / (1024.0 * 1024.0));
        System.out.printf("Throughput estimado: %.0f requisições/segundo%n", (TOTAL_TAREFAS / (duracaoMs / 1000.0)));
    }
}