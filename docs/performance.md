# Desempenho com dados em escala

Medido com o **jar real** (`java -jar`, JDK 25, opções padrão da JVM) sobre um CSV sintético de **1.000.000 de
filmes**, guardado fora do repositório. Máquina: Apple M3 Pro, 11 CPUs, 18 GB. Todas as medições só começam depois
do log `filmes carregados`; o corpo de **cada** resposta foi comparado com o oráculo Python (30 de 30 idênticos).

## Dados

`tools/perf/gen_big.py` (semente fixa): 1.000.000 filmes, anos 1980–2019, 20,0% vencedores (200.110), 100.000
produtores, 30,0% dos filmes com 2 ou 3 produtores (separadores `and`, `,` e `, and`). Resultado do oráculo
(`tools/oracle/oracle.py`): 279.550 vitórias distintas (produtor, ano); `min` com **18.992** itens (intervalo 1) e `max`
com **29** itens (intervalo 39). A resposta tem ~1,6 MB porque a regra de empates devolve todos os pares empatados.

## Antes × depois

Cliente que bate na API assim que a porta aceita conexões (pior caso para a 1ª requisição). "Sem cache" é a
mediana de 3 execuções; "aquece antes da porta" é a mediana de 5.

| Métrica | Sem cache | Aquece antes da porta |
|---|---:|---:|
| Carga do CSV | 7,24 s | 6,31 s |
| start → porta aceitando conexões | 7,68 s | 8,01 s |
| start → 1ª resposta correta | 8,81 s | **8,10 s** |
| 1ª requisição, ao abrir a porta | 1.066 ms | **98,5 ms** |
| Requisições 2 a 10, mediana | 87,3 ms | **9,8 ms** |
| Requisições 2 a 10, pior caso | 105,4 ms | 11,2 ms |
| Tamanho da resposta | 1,58 MB | 1,58 MB |

- **O aquecimento acontece antes da porta** (`AwardIntervalsWarmUp`): a porta passa a abrir ~1,3 s mais tarde
  (porta − carga: 0,44 s → 1,7 s), e em troca nenhuma requisição paga o cálculo. O tempo até a 1ª resposta correta
  cai de 8,8 s para 8,1 s.
- A variação da carga entre execuções (6,0 a 6,6 s, com uma execução a 12,5 s) é ruído da máquina e não tem relação com
  o aquecimento; por isso as medianas. Em todas as execuções o corpo foi idêntico ao oráculo (50 de 50 chamadas).
- Uma variante intermediária, com aquecimento em `ApplicationReadyEvent` (depois de abrir a porta), deixava a 1ª
  requisição em ~1,1 s, porque quem chegava durante o aquecimento esperava o mesmo cálculo. Foi descartada.
- **Por que `SmartLifecycle` e não `SmartInitializingSingleton`:** os callbacks de `SmartInitializingSingleton` rodam na
  ordem de registro dos beans (`awards` antes de `movies`), então o aquecimento poderia rodar antes da carga e guardar
  um resultado vazio. O `SmartLifecycle` de fase 0 inicia depois da carga e antes do servidor web.
  `AwardIntervalsWarmUpBeforePortTest` prova a ordem (com a fase trocada para depois do servidor ele falha).
- RSS: 1,8 a 3,1 GB nas variantes, variando com o momento do GC; não é ganho do cache. O pico vem da carga:
  `MovieCsvReader` lê o arquivo inteiro e mantém linhas e lotes em memória antes de gravar. Para CSVs muito maiores o
  caminho é leitura em streaming com lotes (não feito: o enunciado não exige e dificultaria o "tudo ou nada").
- Os ~9 ms que restam na requisição aquecida são serialização de 1,6 MB e transferência, não cálculo.

## Onde o tempo ia (instrumentação temporária, requisição sem cache)

Medido numa cópia descartável do projeto (a instrumentação **não** está neste repositório), 3 execuções.

| Fase | 1ª requisição (fria) | Requisições 2–10 (aquecidas, mediana) |
|---|---:|---:|
| Consulta SQL + mapeamento (279.550 linhas) | 810–1.174 ms | 39,0 ms (62% do total) |
| Cálculo em Java (uma passada) | 42–50 ms | 19,7 ms |
| Serialização JSON | 36–49 ms | 3,2 ms |

A consulta domina; por isso o cálculo foi cacheado inteiro, e não só otimizado.

## Índices: A/B e decisão

`EXPLAIN ANALYZE` do baseline: varredura completa de `movie` (`scanCount: 1000001`), junção por PK em
`movie_producer`. Candidatos testados, 3 rodadas intercaladas (ordem rotacionada, GC antes de cada medição), mediana
de 5 execuções por rodada, resultado idêntico ao baseline conferido a cada execução:

| Candidato | Plano | Consulta (média das 3 rodadas) | Criação |
|---|---|---:|---:|
| baseline (só PKs) | `tableScan`, 1.000.001 linhas | 56,6 ms | — |
| A `movie(winner, release_year)` | índice, 200.111 linhas | 50,2 ms | 1,0 s |
| B `movie(winner)` | índice, 200.111 linhas | 60,1 ms | 0,6 s |
| C `movie_producer(producer, movie_id)` | **ignorado pelo otimizador** (plano igual ao baseline) | 59,8 ms | 5,4 s |
| D `movie(winner, id, release_year)` | índice, 200.111 linhas | 51,2 ms | 1,1 s |

As faixas se sobrepõem às do baseline (44–75 ms). Teste de ponta a ponta no jar real, 3 execuções cada, com o índice
no `schema.sql` (como seria em produção):

| Variante | Carga | Requisição aquecida (mediana por execução) |
|---|---:|---:|
| sem índice | 5,5–5,9 s | 66–88 ms |
| A | 7,0–8,4 s | 87–97 ms |
| D | 6,5–9,7 s | 83–110 ms |

**Nenhum índice foi mantido**: o plano melhora (200 mil linhas em vez de 1 milhão), mas o ganho de tempo não é provado
(faixas sobrepostas), a carga fica 1–3 s mais lenta e a requisição aquecida não melhora. Além disso, com o resultado
em cache a consulta roda uma única vez. O `schema.sql` continua só com chaves primárias.

## Cache: validade e evolução

`AwardIntervalsCache` calcula o resultado uma vez (`volatile` e dupla verificação em `synchronized`);
`AwardIntervalsWarmUp` o aquece antes de o servidor web abrir a porta.

- **Condição de validade:** os dados só mudam na carga do CSV, que termina antes de a porta abrir, e não há escrita em
  `movies` em tempo de execução. Nessa condição o valor nunca fica desatualizado.
- **Para aceitar escrita em runtime** (não implementado): `movies` publicaria um evento na sua API pública após cada
  alteração confirmada (ex.: `MoviesChanged`, depois do commit) e `awards` o ouviria para descartar o valor
  (`cached = null`), deixando a próxima leitura recalcular. A dependência continuaria sendo só `awards → movies`.

## Como reproduzir

```bash
python3 -I tools/perf/gen_big.py /tmp/big.csv
python3 -I tools/oracle/oracle.py /tmp/big.csv > /tmp/expected_big.json
./gradlew bootJar
JAVA=/caminho/jdk-25/bin/java python3 -I tools/perf/measure.py build/libs/movies-0.0.1-SNAPSHOT.jar /tmp/big.csv /tmp/expected_big.json rodada
```
