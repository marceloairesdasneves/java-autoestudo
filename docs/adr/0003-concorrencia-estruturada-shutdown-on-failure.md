# ADR 0003: Concorrência Estruturada e Eliminação de Tarefas Órfãs via ShutdownOnFailure

* **Status:** ACCEPTED
* **Data:** 07/09/2026
* **Decisores:** Arquiteto de Soluções & Engenharia de Backend
* **Contexto Técnico:** Java 21+ (Preview Feature), Orquestração de I/O no ClearPulse Ledger

---

## 1. Contexto

Com a introdução de Virtual Threads (ADR-0002), o sistema passou a disparar subtarefas de I/O em alta escala. Contudo, operações de liquidação e faturamento dependem de múltiplas chamadas externas simultâneas (ex: validação cadastral na SEFAZ e checagem de limite em bureau de crédito).

No modelo de concorrência clássico desestruturado (`CompletableFuture`, `ExecutorService` livre), a falha de uma chamada paralela não interrompe as demais de forma coordenada. Isso dá origem a **threads órfãs (threads zumbis)**, que continuam consumindo CPU, conexões de rede e cotas pagas de APIs externas para uma transação que o usuário ou o fluxo principal já considerou abortada. Além disso, a quebra das relações pai-filho entre threads fragmenta os stack traces em logs distribuídos, dificultando a depuração em produção.

---

## 2. Decisão

Decidimos adotar **Structured Concurrency** (`java.util.concurrent.StructuredTaskScope`) como o padrão arquitetural para qualquer fluxo de execução que envolva a decomposição de uma requisição em múltiplas subtarefas concorrentes:

1. **Escopo Obrigatório via Bloco Estruturado:** Todas as chamadas concorrentes irmãs devem ser delimitadas por um bloco `try-with-resources` gerenciado por uma instância de `StructuredTaskScope`. A thread principal não pode sair do bloco sem que todas as filhas completem ou sejam canceladas.
2. **Padrão Fail-Fast com `ShutdownOnFailure`:** Para pipelines em que todos os resultados parciais são estritamente necessários para consolidar a transação (ex: conformidade fiscal e análise cadastral), o escopo padrão será `StructuredTaskScope.ShutdownOnFailure`. A falha de qualquer subtarefa aciona o envio imediato de sinais de interrupção cooperativa (`Thread.interrupt()`) para todas as subtarefas irmãs ainda em voo.
3. **Ponto de Encontro Estrito:** A chamada a `scope.join()` seguida de `scope.throwIfFailed()` é mandatória antes de qualquer tentativa de leitura de valores via `Subtask.get()`, garantindo que exceções sejam propagadas de forma linear e transparente na pilha de chamada do invocador original.

### 2.1 Propagação Segura de Contexto via ScopedValue
Fica terminantemente vetado o uso de `ThreadLocal` ou `InheritableThreadLocal` para tráfego de dados de contexto transacional (ex: Tenant ID, identificadores fiscais e credenciais autenticadas). O compartilhamento desses dados com subtarefas criadas pelo `StructuredTaskScope` será feito exclusivamente via instâncias de `ScopedValue`, garantindo imutabilidade estrita e desalocação automática de escopo pela JVM.

---

### Adendo de Consequências (Scoped Values)
* **Zero Overhead de Cópia em Concorrência Massiva:** Eliminação do gargalo de duplicação de mapas de memória entre threads pais e filhas, viabilizando o tráfego de contexto para milhares de Virtual Threads simultâneas.
* **Prevenção Nativa de Vazamento de Memória (No Leaks):** Impossibilidade física de contaminação de sessões ou retenção indevida de dados no Heap após o término da requisição.

---

## 3. Consequências

### Positivas
* **Eliminação de Desperdício e Threads Órfãs:** Subtarefas lentas são abortadas no mesmo milissegundo em que uma falha impeditiva ocorre, economizando ciclos de processador, conexões de socket e consumo de serviços parceiros tarifados.
* **Stack Traces Unificados e Rastreabilidade:** A hierarquia pai-filho é preservada pela JVM, permitindo que logs de erro e ferramentas de diagnóstico apresentem o rastro completo da requisição original e de suas ramificações.
* **Código Concorrente com Leitura Linear:** Elimina o encadeamento complexo de callbacks assíncronos (`.thenCompose()`, `.exceptionally()`), mantendo a simplicidade do modelo imperativo tradicional.

### Negativas e Trade-offs
* **Dependência de Recursos em Preview:** Por estar em fase de refinamento na especificação da JVM (JEPs de Preview), a compilação e a execução exigem a flag `--enable-preview`, demandando alinhamento explícito no `pom.xml` e nos ambientes de CI/CD.
* **Exigência de Cooperação em Interrupções:** Tarefas filhas que realizam operações de cálculo intenso ou bibliotecas de terceiros que engolem `InterruptedException` sem restaurar o status de interrupção podem não ser canceladas imediatamente, exigindo auditoria nas rotinas chamadas.