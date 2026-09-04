# ADR 0002: Adoção de Virtual Threads e Controle de Saturação de Recursos

* **Status:** ACCEPTED
* **Data:** 04/09/2026
* **Decisores:** Arquiteto de Soluções & Engenharia de Backend
* **Contexto Técnico:** Java 21 LTS, Concorrência e Execução Concorrente no ClearPulse Ledger

---

## 1. Contexto

O subsistema de faturamento e liquidações contábeis opera sob picos de transações concorrentes envolvendo chamadas de rede com tempo de resposta imprevisível (consultas a bancos parceiros, autorizações da SEFAZ e gravação de títulos).

O modelo clássico de concorrência com threads de plataforma (Platform Threads) aloca 1 MB de pilha por thread e impõe limites rígidos de escalabilidade, provocando exaustão de memória sob alta carga. Modelos alternativos não-bloqueantes (reativos) exigiriam reescrita completa do pipeline para APIs assíncronas complexas (Mono/Flux), degradando a manutenibilidade e a rastreabilidade via logs e stack traces.

---

## 2. Decisão

Decidimos adotar **Virtual Threads (Project Loom)** como o modelo de concorrência padrão para todo o processamento de I/O contábil:

1. **Modelo Thread-per-Task:** Utilização de `Executors.newVirtualThreadPerTaskExecutor()` em escopos estruturados (`try-with-resources`), sem a implementação de pools para Virtual Threads.
2. **Eliminação de Bloqueios Nativos (Anti-Pinning):** Fica proibido o uso da palavra-chave `synchronized` em trechos de código que realizem operações de I/O, substituindo-a por `ReentrantLock` para assegurar o desmonte (*unmount*) limpo das Carrier Threads.
3. **Proteção de Recursos Finitos:** A proteção a recursos com limites rígidos de concorrência (como pools de conexões com bancos de dados relacionais) será garantida via `Semaphore` ou pools dedicados (HikariCP), isolando o banco de sobrecargas de conexões simultâneas.

---

## 3. Consequências

### Positivas
* **Escalabilidade Massiva com Código Síncrono:** Capacidade de sustentar dezenas de milhares de tarefas simultâneas com baixo consumo de memória RAM (megabytes de Heap em vez de gigabytes de memória nativa).
* **Rastreabilidade e Manutenibilidade:** Preservação de stack traces legíveis e alinhados com ferramentas padrão de monitoramento e profiling da JVM (JMX, ThreadMXBean, APMs).
* **Eficiência de CPU:** O desacoplamento das Carrier Threads maximiza a utilização dos núcleos físicos do servidor sem desperdício de tempo de processador em operações de espera de I/O.

### Negativas e Trade-offs
* **Risco de Thread Pinning:** A presença inadvertida de bibliotecas legado contendo blocos `synchronized` em torno de I/O pode reter Carrier Threads físicas e degradar a vazão geral.
* **Sobrecarga em Recursos Downstream:** Como o sistema é capaz de disparar dezenas de milhares de requisições por segundo, sistemas externos e bancos de dados tornam-se o ponto crítico de gargalo caso os limites de taxa via Semáforo ou pool de conexões não sejam rigidamente observados.