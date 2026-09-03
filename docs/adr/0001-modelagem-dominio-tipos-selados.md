# ADR 0001: Modelagem de Domínio Contábil com Tipos Selados e Imutabilidade no Java 21

* **Status:** ACCEPTED
* **Data:** 03/09/2026
* **Decisores:** Arquiteto de Soluções & Engenharia de Backend
* **Contexto Técnico:** Java 21 LTS, ClearPulse Ledger, Motor de Faturamento

---

## 1. Contexto

O subsistema contábil e de faturamento lida com lançamentos financeiros críticos e liquidações concorrentes. 
O uso tradicional de JavaBeans mutáveis com anotações Lombok permitia que estados intermediários inválidos (como faturas
com valores negativos ou ausência de documentos cadastrais) fossem instanciados e circulassem pelas camadas de serviço.

Além disso, a introdução de novos tipos de eventos de faturamento dependia de checagens manuais com cadeias de 
`if/else`, resultando em falhas silenciosas em tempo de execução quando novas regras de negócio eram adicionadas sem a 
devida atualização do motor de processamento.

---

## 2. Decisão

Decidimos utilizar **Java 21 Records** e **Sealed Interfaces** com **Pattern Matching** para todas as representações de 
eventos e comandos do domínio contábil:

1. **Imutabilidade Estrita com Records:** Todos os eventos serão declarados como Records, com validações de integridade 
2. contidas em **construtores compactos**, impedindo que instâncias inconsistentes cheguem à memória Heap.
2. **Fronteira Fechada de Tipos:** Hierarquias de eventos serão delimitadas explicitamente por interfaces seladas 
(`sealed interface ... permits ...`), garantindo governança total sobre os herdeiros do domínio.
3. **Exaustividade no Compilador:** O processamento desses eventos usará `switch expressions` sem cláusula 
`default`, delegando ao compilador Java a verificação matemática de cobertura de todos os casos de negócio.

---

## 3. Consequências

### Positivas
* **Segurança e Integridade em Tempo de Compilação:** O compilador atua como fiscalizador de regras. A adição de novos 
* ventos fiscais ou contábeis quebra o build automaticamente até que todos os fluxos de negócio sejam devidamente 
* tratados, eliminando falhas silenciosas em produção.
* **Imutabilidade e *Thread-Safety* Nativa:** Instâncias de Records são inerentemente seguras para operações 
* concorrentes na JVM, sem risco de corrupção de estado ou necessidade de locks manuais complexos no Heap.
* **Eliminação de Estados Inválidos:** Os construtores compactos impedem que entidades sem identificador, com valores 
* negativos ou dados cadastrais inconsistentes cheguem à memória.

### Negativas e Trade-offs
* **Rigidez e Sobrecarga em Mudanças Estruturais:** A introdução de um novo subtipo na interface selada requer a 
* atualização coordenada de todos os blocos `switch` existentes no sistema, elevando o esforço de codificação e 
* refatoração quando o domínio sofre mudanças frequentes.
* **Incompatibilidade com Ferramental Legado:** Ferramentas de persistência e serialização antigas que dependem de 
* construtores sem argumentos (default constructors) ou de mutabilidade via *setters* (JavaBeans) exigem adaptadores 
* (mappers/DTOs) dedicados para interoperar com os Records.
* **Curva de Aprendizado:** Exige disciplina da equipe para abandonar o padrão tradicional de `default` genérico 
* e o uso disperso de exceções para validações que devem pertencer ao modelo de tipos.
