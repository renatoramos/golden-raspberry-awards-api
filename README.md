# Golden Raspberry Awards — intervalos entre prêmios

API REST que lê a lista de indicados e vencedores da categoria **Pior Filme** do Golden Raspberry Awards a partir de um
CSV, grava em um banco H2 em memória ao iniciar e informa:

- o(s) produtor(es) com o **maior** intervalo entre dois prêmios consecutivos;
- o(s) produtor(es) que obtiveram dois prêmios **mais rápido**.

Stack: Java 25 (toolchain do Gradle) · Spring Boot 4.1.1 (Spring MVC, `JdbcClient`/`JdbcTemplate`, sem JPA) · H2 em
memória · Spring Modulith 2.1.1 · Gradle 9.8.2 (wrapper) · JUnit 5, AssertJ e `MockMvcTester`.

## Como rodar a aplicação

Pré-requisitos: um JDK **17 ou superior** no `JAVA_HOME`, só para iniciar o Gradle, e acesso à internet na primeira
execução. O Gradle baixa sozinho o Gradle 9.8.2, as dependências e o **JDK 25** (toolchain + foojay). Nenhum banco ou
serviço externo é necessário.

```bash
./gradlew bootRun
```

A aplicação carrega `src/main/resources/Movielist.csv`, registra `206 filmes carregados …` no log e só então abre a
porta 8080.

```bash
curl http://localhost:8080/producers/award-intervals
```

Jar executável (rodar com um JDK 25):

```bash
./gradlew bootJar
java -jar build/libs/movies-0.0.1-SNAPSHOT.jar
```

### Usando outro CSV

Sem mexer em código, de duas formas:

- **Trocar o arquivo** `src/main/resources/Movielist.csv` por outro com o mesmo nome. O arquivo vai empacotado, então é
  preciso rodar de novo `./gradlew bootRun` ou `./gradlew bootJar`.
- **Apontar para um arquivo externo** pela propriedade `app.movies.csv-location`. O nome `movielist.csv.location` é
  aceito como alias e prevalece se os dois forem informados.

```bash
java -jar build/libs/movies-0.0.1-SNAPSHOT.jar --app.movies.csv-location=file:/caminho/outro.csv
java -jar build/libs/movies-0.0.1-SNAPSHOT.jar --movielist.csv.location=file:/caminho/outro.csv
./gradlew bootRun --args='--app.movies.csv-location=file:/caminho/outro.csv'
```

Se o arquivo não existir ou for inválido, a aplicação **não sobe** e a mensagem aponta o problema.

## Como rodar os testes

```bash
./gradlew test
```

Todos os testes são de integração: contexto Spring real, H2 real e, nos cenários por HTTP, Tomcat real. Não há mocks nem
testes unitários.

Os testes usam o seu próprio dataset oficial, `src/test/resources/scenarios/official.csv`, configurado em
`src/test/resources/config/application.properties`. Por isso trocar `src/main/resources/Movielist.csv` não afeta a suíte.

| Classe | O que garante |
|---|---|
| `AwardIntervalsEndpointTest` | Resposta do CSV oficial idêntica ao oráculo; contrato REST (`200`, JSON, `405`, `404`); OpenAPI e Swagger UI publicados |
| `AwardIntervalsScenariosTest` | Aplicação inteira por HTTP, um CSV por cenário (empates, adjacência, uma só vitória, mesmo ano, colunas reordenadas…) |
| `CsvScenariosTest` | Carga: CSVs válidos, CSVs inválidos (falham no boot), ids na ordem do CSV, rollback, alias da propriedade |
| `OfficialDatasetLoadTest` | Módulo `movies` com o CSV oficial: 206 filmes, 42 vencedores, 98 vitórias distintas |
| `DataLoadedBeforeWebServerTest` | A carga termina antes de a porta abrir |
| `AwardIntervalsWarmUpBeforePortTest` | O cache já está aquecido quando a porta abre, com o resultado de depois da carga |
| `AwardIntervalsCacheTest`, `AwardIntervalsCacheConcurrencyTest` | Cálculo feito uma única vez, inclusive com 32 chamadas concorrentes |
| `ModularityTest` | Fronteiras entre módulos (Spring Modulith) |

### Oráculo independente

Os valores esperados **não** vêm do código Java. São gerados por `tools/oracle/oracle.py` (Python, sem dependências),
que reimplementa a regra de forma independente:

```bash
tools/oracle/generate-fixtures.sh                                        # regenera os esperados das fixtures
python3 -I tools/oracle/oracle.py src/main/resources/Movielist.csv        # resposta esperada para um CSV
```

O script também confere que o oráculo rejeita todos os CSVs de `scenarios/invalid/`.

## Formato do CSV aceito

```
year;title;studios;producers;winner
1980;Can't Stop the Music;Associated Film Distribution;Allan Carr;yes
1980;Cruising;Lorimar Productions, United Artists;Jerry Weintraub;
```

- **Codificação:** UTF-8, com ou sem BOM. Finais de linha LF ou CRLF.
- **Delimitador:** `;` por padrão. `,` é detectado quando o cabeçalho reconhece mais colunas conhecidas com ele.
- **Aspas (RFC 4180):** campos entre aspas podem conter o delimitador e quebras de linha; `""` representa uma aspa.
- **Cabeçalho:** colunas localizadas pelo **nome**, em qualquer ordem, sem diferenciar maiúsculas.
  - Obrigatórias: `year`, `title`, `producers`.
  - Opcionais: `winner` (ausente = nenhum vencedor) e `studios`.
  - Colunas extras são ignoradas. Coluna duplicada é erro.
- **`winner`:** `yes`, sem diferenciar maiúsculas, é vencedor. Qualquer outro valor, inclusive vazio, não é.
- **`producers`:** nomes separados por `,` (com ou sem espaço), `, and` e ` and `. Os nomes são aparados e não se
  repetem dentro do mesmo filme.
- **Linhas em branco** são ignoradas.

Erros que **impedem a aplicação de subir**, sempre com a linha física e o conteúdo quando se aplica:

| Situação | Mensagem (exemplo) |
|---|---|
| Arquivo vazio | `arquivo CSV vazio: cabeçalho ausente` |
| Coluna obrigatória ausente | `colunas obrigatórias ausentes: [year] (cabeçalho encontrado: [...])` |
| Coluna duplicada | `coluna duplicada no cabeçalho: year` |
| Nº de colunas diferente do cabeçalho | `linha 3: 2 colunas, esperado 5: [2001, B]` |
| `year` não numérico | `linha 3: year inválido 'abc': [abc, B, S, P, yes]` |
| `producers` vazio | `linha 3: producers vazio: [...]` |
| Aspas não fechadas | `linha 3: aspas não fechadas até o fim do arquivo` |
| Texto depois de fechar aspas | `linha 2: aspas inesperadas: ...` |

Nenhuma linha é descartada em silêncio. A carga é uma única transação: ou entram todos os filmes, ou nenhum.

## Contrato do endpoint

`GET /producers/award-intervals`

- `200 OK` com `Content-Type: application/json`.
- `POST`, `PUT`, `PATCH` e `DELETE` respondem `405`.

```json
{
  "min": [{"producer": "Joel Silver", "interval": 1, "previousWin": 1990, "followingWin": 1991}],
  "max": [{"producer": "Matthew Vaughn", "interval": 13, "previousWin": 2002, "followingWin": 2015}]
}
```

| Campo | Tipo | Significado |
|---|---|---|
| `min` / `max` | array | Pares com o menor / maior intervalo. Vazios se nenhum produtor tiver 2+ vitórias. |
| `producer` | string | Nome do produtor |
| `interval` | inteiro | `followingWin - previousWin` |
| `previousWin` / `followingWin` | inteiro | Anos das duas vitórias adjacentes |

Os itens vêm ordenados por `previousWin` e depois por `producer`. O caminho não é definido pelo enunciado (ver S15).

### Swagger / OpenAPI

Com a aplicação no ar (springdoc-openapi):

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Especificação OpenAPI 3.1 (JSON): <http://localhost:8080/v3/api-docs>

## Regras de negócio e suposições

O enunciado define o formato da resposta, mas não os pontos abaixo. As suposições foram aprovadas pelo autor na sessão
de desenvolvimento e estão cobertas por testes.

| # | Regra |
|---|---|
| S1 | Empates no menor ou no maior intervalo: todos os pares empatados são retornados. |
| S2 | Intervalo é a diferença entre vitórias **adjacentes** do mesmo produtor: 2000→2005→2010 gera 2 pares, não 3. Se um produtor tem dois pares empatados no extremo, os dois entram. |
| S3 | Produtor com uma só vitória não gera intervalo. |
| S4 | Duas vitórias do mesmo produtor no mesmo ano contam como uma. |
| S5 | Sem produtor com 2+ vitórias: `{"min": [], "max": []}` com `200`. |
| S6 | Colunas lidas pelo nome do cabeçalho, em qualquer ordem. |
| S7 | Obrigatórias: `year`, `title`, `producers`. Opcionais: `winner` e `studios`. Coluna obrigatória ausente derruba o boot. |
| S8 | `winner = yes`, sem diferenciar maiúsculas, é vencedor. |
| S9 | Produtores separados por `,` (com ou sem espaço), `, and` e ` and `, com trim e sem repetição no mesmo filme. |
| S10 | Sem normalização de grafia: `Michael DeLuca` e `Michael De Luca` são produtores distintos. |
| S11 | Delimitador `;` ou `,` (detectado pelo cabeçalho); RFC 4180; BOM e CRLF. |
| S12 | Itens ordenados por `previousWin` e depois `producer` (o PDF não define ordem; é consistente com o exemplo dele). |
| S13 | Caminho do CSV configurável (`app.movies.csv-location`, alias `movielist.csv.location`); padrão `classpath:Movielist.csv`. |
| S14 | Linha inválida derruba o boot com nº da linha e conteúdo, sem descartar em silêncio. Linha com mais ou menos colunas que o cabeçalho é inválida. |
| S15 | Caminho do endpoint: `GET /producers/award-intervals` (escolha do desenvolvimento; o PDF não define). |

## Arquitetura

Monólito modular com Spring Modulith. São dois módulos, cada um num pacote sem subpacotes:

```
com.outsera
├── MoviesApplication
├── movies/   dono dos dados: schema, carga do CSV e consulta
│     API pública: ProducerWins (interface) e ProducerWin (record). Todo o resto é package-private.
└── awards/   regra de negócio (cálculo dos intervalos), cache e controller REST
      @ApplicationModule(allowedDependencies = "movies"): depende só da API pública de movies.
```

- **Fronteiras em duas camadas.** As classes internas são `package-private`, então o **compilador** impede que `awards`
  as use. O `ModularityTest` (`ApplicationModules.verify()`) cobre o resto: dependências não declaradas e ciclos. Ele
  só roda nos testes; se um módulo ganhar subpacotes `internal`, só ele protege.
- **Carga do CSV.** `MovieLoader` implementa `SmartInitializingSingleton`, que roda antes de o Tomcat abrir a porta. Um
  `ApplicationRunner` rodaria depois e deixaria a API respondendo vazio durante a carga. Leitura e validação vêm antes
  de qualquer escrita; a escrita usa lotes (`batchUpdate`) numa única transação (`TransactionTemplate`).
- **Banco.** H2 em memória, com nome único por contexto, e só chaves primárias. Índices foram medidos e rejeitados
  (`docs/performance.md`). A consulta de `movies` já devolve as vitórias distintas ordenadas por produtor e ano; `awards`
  calcula em uma passada.

### Isto é over-engineering para um endpoint?

Em parte, sim. Dois módulos para um endpoint é desproporcional e custa uma interface, um record, um `package-info` e uma
indireção a mais. Em troca, a regra fica sem SQL, o armazenamento fica trocável e o limite entre módulos é verificado por
teste. Para limitar o custo, a API de `movies` tem **um único método**, não há eventos nem camadas duplicadas, e o
cálculo é uma classe pura. Se o projeto ficasse só com este endpoint, um único pacote bastaria.

## Cache e seu limite

O resultado é calculado **uma única vez**, antes de a porta abrir. `AwardIntervalsWarmUp` é um `SmartLifecycle` de fase
0, que inicia depois da carga do CSV e antes do servidor web. `AwardIntervalsCache` guarda o valor com `volatile` e dupla
verificação em `synchronized`.

- **Condição de validade:** os dados só mudam na carga, que termina antes de a porta abrir, e nada escreve em `movies` em
  tempo de execução. Enquanto isso for verdade, o valor nunca fica desatualizado, e não há expiração nem invalidação.
- **Limite:** se um dia houver escrita em runtime, o cache **passa a servir dados velhos**. Seria preciso que `movies`
  publicasse um evento na sua API pública após cada alteração confirmada, e que `awards` o ouvisse para descartar o
  valor. Isso **não** está implementado.

## Desempenho

Com um CSV sintético de 1.000.000 de filmes, gerado fora do repositório:

- A carga leva cerca de 6 a 7 s e a porta abre em cerca de 8 s.
- A primeira requisição leva cerca de 98 ms e as seguintes cerca de 10 ms, com resposta idêntica ao oráculo.
- O boot precisa de heap de cerca de 2 GB; com `-Xmx1g` ele falha com `OutOfMemoryError`.

Números, método e o A/B de índices estão em [`docs/performance.md`](docs/performance.md). Gerador e medidor ficam em
`tools/perf/`.

## Limitações conhecidas

- **O caminho do endpoint não está no enunciado** (S15). Um avaliador que chame outra URL recebe 404.
- **Memória.** `MovieCsvReader` lê o arquivo inteiro e mantém linhas e lotes em memória antes de gravar. Para CSVs muito
  maiores seria preciso streaming.
- **S9 é heurística.** `"Frank Mancuso, Jr."` viraria dois produtores, e `Sid and Marty Krofft` também é dividido.
- **Testes intermitentes.** Dois testes que fazem HTTP real em porta aleatória falharam uma vez cada: 2 falhas em 106
  execuções completas contadas. Nenhuma se repetiu nas 90 execuções seguintes à segunda falha. A causa não foi
  identificada.
- **Primeira execução precisa de internet**, para baixar o Gradle, as dependências e o JDK 25. Só foi verificada em macOS
  (arm64).

## Uso de IA

O desenvolvimento foi feito com o Claude Code. O registro fiel das interações, com prompts literais, erros e medições,
está em [`AI_LOG.md`](AI_LOG.md).
