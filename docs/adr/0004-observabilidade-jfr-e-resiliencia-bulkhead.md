# ADR 0004: Observabilidade com JFR, Detecção de Pinning e Resiliência via Bulkhead

* **Status:** ACCEPTED
* **Data:** 10/09/2026
* **Decisores:** Arquiteto de Soluções & Engenharia de Backend
* **Contexto Técnico:** Java 21+, Telemetria HotSpot e Resiliência no ClearPulse Ledger

---

## 1. Contexto

Com a adoção massiva de Virtual Threads e processamento concorrente em larga escala, métodos tradicionais de auditoria operacional (como `jstack` e thread dumps manuais) tornaram-se inviáveis pelo risco de degradação da JVM (*stop-the-world* prolongado). Além disso, bibliotecas de terceiros ou legadas contendo bloqueios nativos (`synchronized`) podem provocar *Thread Pinning*, prendendo as Carrier Threads físicas e derrubando a vazão geral do motor contábil.

No aspecto de resiliência, oscilações de latência em APIs parceiras (SEFAZ, bureaus de crédito) podem acumular dezenas de milhares de tarefas em espera, exigindo mecanismos rígidos de isolamento para evitar falha em cascata.

---

## 2. Decisão

Decidimos padronizar a stack de observabilidade e proteção de recursos:

1. **Auditoria Contínua de Pinning via JVM:** Ambientes de homologação e testes de carga devem rodar obrigatoriamente com o flag `-Djdk.tracePinnedThreads=full` ativo. Em produção, eventos do Java Flight Recorder (`jdk.VirtualThreadPinned`) serão monitorados para auditoria de baixa sobrecarga (< 1% CPU).
2. **Eliminação de Bloqueios de Monitor:** Fica terminantemente proibido o uso de `synchronized` envolvendo operações de rede ou I/O, sendo obrigatória a migração para `ReentrantLock`.
3. **Padrão Bulkhead por Serviço Externo:** Cada integração externa terá sua concorrência contida por instâncias isoladas de `Semaphore` e timeouts mandatórios via `StructuredTaskScope`, assegurando que a degradação de um parceiro não comprometa os demais subsistemas.

---

## 3. Consequências

### Positivas
* **Identificação Imediata de Gargalos:** Rastreabilidade cirúrgica da linha de código responsável por retenção indevida de threads nativas sem impacto na memória Heap.
* **Tolerância a Falhas e Isolamento:** Falhas ou latências anômalas em serviços externos são contidas no compartimento do parceiro, sem afetar o faturamento das demais filiais.
* **Telemetria de Baixa Intrusão:** Uso de eventos nativos do HotSpot sem sobrecarga de agentes pesados de APM tradicional.

### Negativas e Trade-offs
* **Necessidade de Configuração de JVM:** Requer parametrização explícita de flags de inicialização em contêineres e pipelines de CI/CD.
* **Governança de Bibliotecas:** Auditoria contínua de dependências Maven para garantir que bibliotecas de terceiros não introduzam `synchronized` bloqueante em atualizações.