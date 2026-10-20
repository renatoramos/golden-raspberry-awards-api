# Registro de uso de IA (AI_LOG)

Registro fiel da sessão de desenvolvimento deste projeto com um agente de código. Os prompts do autor estão
**literais e na ordem**. Para cada um estão o que o agente fez e a evidência (comando, arquivo ou teste). Erros do
agente, decisões, alternativas descartadas e medições estão nas seções seguintes. Nada foi acrescentado que não tenha
acontecido na sessão.

## Ferramentas

- **Agente:** Claude Code (CLI), em uma única sessão.
- **Modelos:** Claude Sonnet 5.5 do início até o pedido do README/AI_LOG. A partir daí, Claude Opus 5.5 (o autor trocou
  o modelo com `/model`).
- **Ferramentas usadas pelo agente:** só o shell (Bash). Comandos usados: `pdftotext`, `pdfinfo`, `file`, `xxd`,
  `iconv`, `grep`, `awk`, `curl`, `python3`, `java`, `gradle`/`./gradlew`, `git`, `ps`, `lsof`. Os arquivos foram
  escritos pelo shell (heredocs e pequenos scripts Python de substituição).
- **Não usados:** subagentes, skills, busca na web, conectores externos.
- **Ambiente:** macOS (Apple M3 Pro, 11 CPUs, 18 GB). JDKs locais 8, 11, 13, 17, 22, 23 e 24. O JDK 25 foi baixado pelo
  toolchain do Gradle.

## Prompts, ações e evidências

### 1. Requisitos e dados

```
Leia "Avaliação - Back-end.pdf" e inspecione Movielist.csv. Ainda NÃO escreva código nem crie arquivos de projeto.

Entregue:
1. Checklist numerado de TODOS os requisitos do PDF (funcionais, não funcionais, entrega e uso de IA), cada um
   com um critério verificável de aceite.
2. Análise dos dados do CSV com comandos que você rodou (não suponha): número de linhas, quantos vencedores,
   como a coluna "producers" combina vários nomes (separadores ", " e " and "; existe ", and"?), valores
   distintos da coluna winner, linhas com aspas, encoding, finais de linha, existência de BOM.
3. Casos de borda que o PDF sugere ("outros conjuntos de dados com cenários diferentes"): empate no menor/maior
   intervalo, produtor com uma única vitória, duas vitórias no mesmo ano, nenhum produtor com 2+ vitórias,
   CSV com colunas em outra ordem. Diga o que o PDF define e o que NÃO define, e proponha uma decisão para
   cada ambiguidade, marcando-a como suposição.
4. Calcule, com um script independente da futura implementação (Python), a resposta esperada para o CSV oficial.
   Guarde o script e a saída: eles serão o oráculo dos testes.

Não proponha stack ainda. Termine com a lista de suposições para eu aprovar.
```

**O que foi feito:**
- PDF extraído com `pdftotext -layout`. Checklist montado: F1–F5, N1–N5 e IA1–IA3.
- CSV analisado com `file`, `xxd`, `grep`, `awk`, `iconv` e `csv.DictReader`:
  - 207 linhas (206 de dados), 42 vencedores, `winner` só com `yes` ou vazio;
  - delimitador `;`, UTF-8 válido sem BOM, LF, nenhuma aspa;
  - 5 linhas com `", and"` (uma delas de vencedor);
  - anos 1980–2019; mais de um vencedor em 1986, 1990 e 2015; nenhum em 2007;
  - grafias `Michael DeLuca` e `Michael De Luca`.
- O agente mostrou que um split ingênuo gera produtores falsos (`"and Tom Hooper"`).
- Oráculo `oracle.py` escrito no diretório temporário da sessão. Resultado: min = Joel Silver (1, 1990→1991),
  max = Matthew Vaughn (13, 2002→2015). Só 4 produtores têm 2 ou mais vitórias.
- Suposições S1–S13 propostas para aprovação.

### 2. Aprovação e ajustes das suposições

```
Aprovo S1, S3, S4, S5, S6, S8, S9, S10 e S13 como estão.

  Ajustes:

  S2 — reescreva para ficar inequívoco: "intervalo = diferença entre duas vitórias ADJACENTES do mesmo produtor
  (2000→2005→2010 gera 2 pares, não 3). Se um mesmo produtor tiver dois pares empatados no menor/maior intervalo,
  os dois entram."

  S7 — defina quais colunas são obrigatórias: year, producers e winner. Sem winner o resultado seria vazio e
  silenciosamente errado, então tem que falhar no boot. title e studios NÃO entram no cálculo; se faltarem,
  não derrube a aplicação.

  S11 — mantenha ";" como padrão, mas detecte "," pelo cabeçalho. Aceite campos entre aspas (RFC 4180: "" escapado,
  ";" dentro de aspas, quebra de linha dentro de campo), além de BOM e CRLF.

  S12 — confirme que o PDF não define ordem. Mantenha previousWin e depois producer, que é consistente com o
  exemplo da página 2.

  Nova S14 — linha inválida (year não numérico, producers vazio, menos colunas que o cabeçalho): falhar no boot
  com o número da linha e o conteúdo, sem pular em silêncio. Pular linhas esconderia dado corrompido num
  dataset desconhecido.
```

**O que foi feito:**
- Oráculo v2 com detecção de delimitador pelo cabeçalho, colunas obrigatórias e erro por linha. Testado com 10 CSVs de
  borda (adjacência, BOM/CRLF/vírgula/colunas reordenadas, aspas multilinha, colunas ausentes, `year` inválido, linha
  curta).
- A saída do CSV oficial ficou idêntica à v1 (conferido com `diff`).
- O agente confirmou no PDF que a ordem não é definida e que o exemplo da página 2 é compatível com S12.
- Propôs S14a (linha com **mais** colunas também falha) e S14b (`producers` vazio falha mesmo em linha não vencedora).

### 3. Stack

```
ok aprovado, vamos para stack
```

```
A stack deste projeto já está decidida. Não proponha alternativas nem "melhorias": use exatamente esta.

  | Camada          | Escolha                                                                      |
  |-----------------|------------------------------------------------------------------------------|
  | Linguagem       | Java 25 (LTS), fixado por toolchain do Gradle                                |
  | Framework       | Spring Boot 4.1.1 (Spring Framework 7, Tomcat 11)                            |
  | Build           | Gradle 9.8.2 com Kotlin DSL e wrapper (./gradlew); sem Maven                 |
  | Plugins Gradle  | org.springframework.boot 4.1.1, io.spring.dependency-management 1.1.7,       |
  |                 | org.gradle.toolchains.foojay-resolver-convention 1.0.0                       |
  | Web             | spring-boot-starter-webmvc                                                   |
  | Acesso a dados  | spring-boot-starter-jdbc (JdbcTemplate/JdbcClient). Sem JPA e sem Hibernate  |
  | Banco           | H2 em memória, runtimeOnly, na versão gerenciada pelo Boot (2.4.x)           |
  | Modularidade    | Spring Modulith 2.1.1 via BOM: starter-core + starter-test                   |
  | Testes          | JUnit 5 + AssertJ + MockMvcTester (spring-boot-starter-webmvc-test);         |
  |                 | junit-platform-launcher em testRuntimeOnly                                   |

  Tarefa 1 — verificar, não substituir: confirme que cada versão existe (Maven Central, services.gradle.org,
  plugin portal), mostrando comando e resultado. Se alguma não existir ou for incompatível, PARE e me diga.

  Tarefa 2 — gerar só o esqueleto, sem código de negócio: groupId com.outsera, artifact movies, classe
  MoviesApplication, settings com foojay, build com toolchain 25, wrapper 9.8.2, application.properties com
  spring.sql.init.mode=always e SEM spring.datasource.url (H2 de nome único por contexto), .gitignore.

  Tarefa 3 — provar o toolchain: ./gradlew build sob o JDK 25 e sob um JDK mais antigo (17), mostrando que o
  Gradle baixa o JDK 25 sozinho.
```

**O que foi feito:**
- Tarefa 1: cada artefato consultado com `curl` (Maven Central, plugin portal, services.gradle.org), todos com HTTP 200.
  Versões gerenciadas pelo Boot 4.1.1: H2 2.4.240, Spring Framework 7.0.9, Tomcat 11.0.24, JUnit Jupiter 6.0.3,
  AssertJ 3.27.7. JDK 25 conferido na API do foojay.
- Tarefa 2: esqueleto criado. O wrapper foi gerado com o Gradle 9.8.1 do Homebrew rodando sob o Corretto 17.
- Tarefa 3, com `GRADLE_USER_HOME` vazio:
  - sob o JDK 17: `BUILD SUCCESSFUL in 33s`, e o Temurin 25.0.4.1 foi baixado sozinho;
  - bytecode major 69 (Java 25);
  - sob o JDK 25 baixado: `BUILD SUCCESSFUL in 6s`;
  - `java -jar` subiu com Spring Boot 4.1.1 e Tomcat 11.0.24 em 0,786 s.
- Erro do agente nesta etapa: E2 (ver seção de erros).

### 4. Arquitetura

```
Proponha a arquitetura antes de implementar. Quero um monólito modular com Spring Modulith, com dois módulos:
- `movies`: dono dos dados (schema, carga do CSV, consulta). Expõe UMA API pública: uma interface que devolve
  as vitórias distintas (produtor, ano) ordenadas, e o record dessa linha. Todo o resto package-private.
- `awards`: regra de negócio (cálculo dos intervalos) + controller REST. Depende só da API pública de `movies`;
  não conhece tabelas nem SQL.

Discuta com honestidade o risco de over-engineering para um único endpoint e como mitigar (README explicando).
Informe o que o Spring Modulith considera "interno" (só subpacotes do módulo) e o que o compilador protege
(package-private), porque isso define onde as classes devem ficar.

Entregue um plano com a árvore de pacotes, as assinaturas públicas e a ordem de implementação em TDD.
Não implemente ainda. Aguarde meu OK.
```

**O que foi feito:** plano entregue (texto, sem código), com:
- a comparação entre a proteção do Modulith (subpacotes internos, checada em teste) e a do compilador
  (`package-private`), e a escolha de módulos planos;
- a discussão de over-engineering;
- árvore de pacotes, assinaturas públicas e ordem de TDD;
- quatro decisões para o autor: D1 mover `MoviesApplication` para `com.outsera`; D2 parser CSV próprio, sem dependência
  nova; D3 manter `ModularityTest`; D4 caminho do endpoint.

### 5. Módulo `movies`

```
OK no plano. Implemente o módulo `movies` em TDD: escreva primeiro o teste de integração que falha,
com o valor esperado vindo do oráculo do Prompt 1, depois o código.

Requisitos da carga do CSV:
- Roda ao iniciar a aplicação e DEVE terminar antes de o servidor web abrir a porta. Atenção: ApplicationRunner
  roda depois do Tomcat abrir a porta e deixaria a API respondendo vazio durante a carga. Use
  SmartInitializingSingleton + TransactionTemplate (atômico: tudo ou nada). Prove com um teste ou medição.
- Colunas localizadas pelo NOME no cabeçalho (year, title, producers obrigatórias; winner e studios opcionais),
  em qualquer ordem; erro claro se faltar uma obrigatória.
- Parser próprio mínimo (RFC 4180): campos entre aspas, "" escapado, quebra de linha dentro de campo, BOM,
  CRLF, separador ";" ou "," detectado pelo cabeçalho. winner = "yes" sem diferenciar maiúsculas.
- Nomes de produtores separados por "," e "and" (incluindo ", and"), com trim, sem duplicar nomes no mesmo filme.
- Inserção em lote (batchUpdate) para filmes e produtores; ids vindos da ordem do CSV.
- Nome do arquivo no classpath: exatamente `Movielist.csv` (como no desafio), configurável por propriedade.

Schema: só as chaves primárias por enquanto. NÃO crie índices especulativos; qualquer índice só entra com
medição.

Mostre a saída de `./gradlew test` e o log "N filmes carregados".
```

**O que foi feito:**
- `MoviesApplication` movida para `com.outsera` (D1). Oráculo copiado para `tools/oracle/`, com modo `--wins`.
- Fixtures criadas em `src/test/resources/scenarios/{valid,invalid}` e script `tools/oracle/generate-fixtures.sh`.
- O agente apontou que o prompt mudava S7 (agora `title` obrigatória e `winner` opcional) e S9 (`,` sem espaço também
  separa). Seguiu o prompt e atualizou o oráculo, com a saída oficial inalterada.
- Testes escritos primeiro. Vermelhos: `NoSuchBeanDefinitionException` para `ProducerWins`, e CSVs inválidos subindo sem
  erro.
- Implementação: `MovieCsvReader`, `MovieCsvImporter`, `MovieLoader` (`SmartInitializingSingleton` +
  `TransactionTemplate` + `batchUpdate`), `JdbcProducerWins` e `schema.sql` só com PKs. A coluna virou `release_year`
  porque `YEAR` é palavra reservada no H2.
- Provas por mutação temporária, com o original restaurado depois:
  - loader trocado por `ApplicationRunner` → `DataLoadedBeforeWebServerTest` falhou (esperado 98);
  - transação removida → teste de rollback falhou com 3 filmes órfãos.
- Resultado: 30 testes, 0 falhas. Log do jar real: `206 filmes carregados …` 13 ms antes de `Tomcat started`.
- Erros do agente nesta etapa: E5, E6, E7 e E8.

### 6. Cobrança do módulo `awards`

```
cade as demais coisas do projeto ? a parte de awrds ?
```

**O que foi feito:**
- O agente tinha encerrado a entrega anterior pedindo OK para começar `awards`, embora o plano já estivesse aprovado.
- Fixtures `scenarios/awards/*` geradas pelo oráculo.
- Testes vermelhos (404 no lugar de 200 e 405). Implementação: `AwardIntervalsCalculator`, `AwardIntervalsController`,
  records de resposta e `package-info` com `allowedDependencies = "movies"`. `ModularityTest` adicionado.
- Mutação `allowedDependencies = {}` → `verify()` falhou apontando `awards → movies`.
- Jar real: `GET` deu 200 com a resposta oficial e `POST` deu 405. Resultado: 47 testes, 0 falhas.
- README e um primeiro registro de IA (`docs/ai-interactions.md`) escritos. Esse registro foi depois substituído por este
  arquivo.
- Erro do agente nesta etapa: E9.

### 7. Desempenho em escala

```
Avalie desempenho com dados em escala. Passo a passo e com números:

1. Gere um CSV sintético de 1.000.000 de filmes no mesmo formato do oficial (~20% vencedores, 100 mil
   produtores, 30% dos filmes com 2 ou 3 produtores). Guarde-o FORA do repositório.
2. Calcule a resposta esperada com um script Python independente e confira que a API devolve o mesmo.
3. Suba o jar real (não o teste) apontando para esse CSV e meça: tempo até a porta responder, tempo por
   requisição (10 chamadas), tamanho da resposta, memória (RSS). Cuidado: só meça depois do log de carga
   concluída, senão a medição é inválida.
4. Rode EXPLAIN ANALYZE da consulta de vitórias e faça um A/B de índices candidatos (por exemplo em
   movie(winner, release_year)) com 3 rodadas cada. Mantenha um índice SOMENTE se a medição provar ganho;
   se piorar, remova e registre o resultado.
5. Separe com instrumentação temporária: tempo da query, tempo do cálculo em Java e serialização, frio x
   aquecido. Remova a instrumentação ao final.
6. Se a requisição passar de ~10 ms, calcule o resultado uma vez (os dados só mudam na carga, antes da porta
   abrir) com aquecimento em ApplicationReadyEvent e dupla verificação sincronizada. Documente a condição de
   validade do cache e o que seria preciso para aceitar escrita em runtime (invalidação por evento do módulo
   `movies`), sem implementar isso.

Entregue uma tabela antes/depois e rode a suíte completa de novo.
```

**O que foi feito** (números na seção de medições):
- `tools/perf/gen_big.py` (semente 42) gerou 1.000.000 filmes, 20,0% vencedores e 30,0% com 2 ou 3 produtores, com
  100.000 produtores distintos. O CSV ficou no diretório temporário da sessão, fora do repositório.
- Oráculo: 279.550 vitórias distintas, `min` com 18.992 itens e `max` com 29.
- `tools/perf/measure.py` sobe o jar real, só mede depois do log de carga (com `assert`) e compara cada corpo com o
  oráculo.
- Instrumentação e A/B de índices feitos numa **cópia descartável** do projeto, fora do repositório. O `grep` confirmou
  que nada disso entrou no código.
- `EXPLAIN ANALYZE`: o baseline varre 1.000.001 linhas de `movie`; os índices A, B e D usam índice (200.111 linhas); o
  índice C é ignorado pelo otimizador.
- **Nenhum índice foi mantido** (justificativa na seção de decisões).
- Cache implementado em TDD: teste vermelho com um contador sobre o `ProducerWins` real (5 leituras em 5 chamadas),
  depois `AwardIntervalsCache` com aquecimento em `ApplicationReadyEvent`, como pedido. Teste de concorrência com 32
  threads. Mutações: sem a 2ª verificação → 10 leituras em vez de 1; sem `synchronized` → falha.
- `docs/performance.md` escrito. Resultado: 50 testes, 0 falhas.
- Erros do agente nesta etapa: E10 e E11.

### 8. Aquecimento antes da porta

```
o aquecimento tem que ser antes da porta disponivel usa um warmup no codigo e refaz o teste
```

**O que foi feito:**
- Teste vermelho `AwardIntervalsWarmUpBeforePortTest`: leituras da base no instante do `WebServerInitializedEvent` = 0.
- `AwardIntervalsWarmUp` (`SmartLifecycle`, fase 0) criado; o `@EventListener(ApplicationReadyEvent)` foi removido do
  cache.
- Mutação: fase trocada para `Integer.MAX_VALUE` → o teste voltou a dar 0.
- Medição refeita em 5 execuções. A 1ª requisição caiu de ~1,1 s para 98,5 ms (mediana).
- Resultado: 52 testes, 0 falhas.
- Erros do agente nesta etapa: E12, E13 e E14.

### 9. Auditoria final

```
Faça uma auditoria final linha a linha contra o checklist do Prompt 1. Para cada requisito: status, evidência
(comando/arquivo/teste) e o que falta. Verifique também, com comandos:

- Substituir src/main/resources/Movielist.csv por outro dataset com o mesmo nome funciona sem mexer em código.
- `--movielist.csv.location=file:/caminho/outro.csv` funciona via jar.
- O projeto roda do zero em clone limpo: `git clone` num diretório temporário, `./gradlew test` sob um JDK
  diferente do de desenvolvimento.
- Nenhum arquivo gerado (build/, .gradle/) está versionado.

Liste riscos residuais honestos (ex.: carga de 1M leva ~10 s; cache não invalida em escrita). Não esconda falhas.
```

**O que foi feito:**
- O projeto ainda não era repositório git. O agente criou um repositório temporário a partir da árvore inteira (com
  `build/` e `.gradle/`), fez commit lá e um `git clone`:
  - 99 arquivos versionados, nada de `build/` ou `.gradle/` (só o `gradle-wrapper.jar`, que deve ser versionado);
  - a credencial do item E2 não aparece em nenhum arquivo.
- Clone com `GRADLE_USER_HOME` vazio e launcher **JDK 24**: `BUILD SUCCESSFUL in 35s`, 52 testes, 0 falhas.
- **Falhas encontradas e relatadas:**
  1. `--movielist.csv.location` era ignorado em silêncio (o jar carregava o CSV embutido).
  2. Com o `Movielist.csv` de produção trocado, a aplicação funcionava, mas 9 testes quebravam.
  3. O projeto não era repositório git (N5).
- Outras verificações: `./gradlew bootRun` funcionou no clone; nenhum Mockito ou `@Mock`; H2 `jdbc:h2:mem:`, sem
  `*.mv.db` no disco. Com 1M linhas: `-Xmx2g` funciona; `-Xmx1g` e `-Xmx512m` falham com `OutOfMemoryError`.
- Erro do agente nesta etapa: E15.

### 10. Aplicar as correções

```
om aplique esses pontos
```

**O que foi feito:**
- O autor não escolheu entre alias e renomear. O agente manteve `app.movies.csv-location` e acrescentou o alias
  `movielist.csv.location`, que prevalece. Testes vermelhos primeiro, depois verdes.
- Testes herméticos: `src/test/resources/scenarios/official.csv`, `src/test/resources/config/application.properties` e
  referências trocadas.
- Num diretório temporário com o `Movielist.csv` de produção trocado por outro dataset, a suíte rodou 54 testes, verde nas 5 seguintes.
- Pelo jar: `--movielist.csv.location=file:…` carregou 206 filmes do arquivo externo.
- `git init` feito (ver item 12).
- Erros do agente nesta etapa: E16.

### 11. Mensagem no meio do turno

```
só não comite ainda
```

**O que foi feito:** o repositório foi inicializado (`git init`), mas **nada foi stageado nem commitado**. Nenhum push.

### 12. Este documento

```
Escreva o README.md: como rodar a aplicação, como rodar os testes, formato do CSV aceito, contrato do endpoint,
regras de negócio (inclusive as suposições aprovadas), arquitetura modular e o limite do cache.

Depois escreva o AI_LOG.md. Ele é exigido pelo enunciado e será avaliado, então precisa ser FIEL:
- Liste, na ordem, TODOS os meus prompts desta sessão, literais, e para cada um o que você fez e a evidência.
- Registre os seus erros e correções reais (o que falhou, como foi percebido, como foi corrigido).
- Registre decisões de design e as alternativas descartadas.
- Inclua a tabela de medições feitas de verdade.
- Informe as ferramentas usadas e que partes eu aprovei ou mudei.
Não invente nem reescreva a história. Se algo não aconteceu na sessão, não entra.
```

**Contexto:** este pedido chegou no meio da investigação dos testes intermitentes. As respostas do agente a ele foram
interrompidas duas vezes por um classificador de segurança do sistema. O conteúdo retido não ficou disponível para o
agente; o estado do projeto foi reconferido depois (`git status`, `ls`). Entre as duas interrupções o autor escreveu:

```
tentar novamente
```

Depois o autor trocou o modelo para Claude Opus 5.5 com `/model` e reenviou o mesmo pedido, com texto idêntico.

**O que foi feito:**
- `README.md` reescrito.
- Este `AI_LOG.md` escrito.
- `docs/ai-interactions.md` removido: era um resumo escrito pelo próprio agente, substituído por este registro.

### 13. Feedback do avaliador: testes que não dependiam do `Movielist.csv`

Sessão posterior, depois do commit `4b90aa1`, com Claude Opus 5.5. Ferramentas: shell, leitura e edição de arquivos e
uma `git worktree` temporária.

```
recebi essa msg do avalidador vamos entender juntos o que aconteceu ?

Paola Ferreira:
 Oi, Renato! Equipe técnica solicitou um ajuste no teste, pode verificar e me retornar com a correção?

Itens observados:
- Requisito não funcional 2: Após modificar o arquivo em src/main/resources nenhum teste falhou. O teste de integração deve garantir que os dados retornados na API estão de acordo com o conteúdo do arquivo padrão, e deve falhar se o arquivo for modificado de forma que qualquer aspecto do resultado mude.
```

**O que foi feito:** diagnóstico, sem mudar código. A causa era a decisão dos "testes herméticos" do item 10:
`src/test/resources/config/application.properties` apontava `app.movies.csv-location` para
`scenarios/official.csv`, uma cópia byte a byte igual (`cmp`) do `Movielist.csv`. Nenhum teste lia o arquivo de
`src/main/resources`, então alterá-lo não derrubava nada. A asserção do endpoint já era estrita (`isStrictlyEqualTo` no
JSON inteiro); o problema estava na fonte dos dados.

```
removendo o arquivo application.properties resolve o problema ?
```

**O que foi feito:** experimento numa `git worktree` descartável. Sem o arquivo, a suíte passou com o CSV original.
Com a vitória de 1991 do Joel Silver trocada para 1992 no `Movielist.csv`, 3 testes falharam (`AwardIntervalsEndpointTest`
×2, `OfficialDatasetLoadTest` ×1). A worktree foi removida e o repositório do autor ficou intacto.

```
perfeito ajuste esses pontos, deixe os teste apontando para o Movielist.csv
```

**O que foi feito:**
- Removidos `src/test/resources/config/application.properties` e `src/test/resources/scenarios/official.csv`.
- `AwardIntervalsCacheConcurrencyTest` e `CsvScenariosTest` passaram a usar `classpath:Movielist.csv`. O javadoc de
  `OfficialDatasetLoadTest` foi atualizado.
- `tools/oracle/generate-fixtures.sh` passou a gerar `official.intervals.json` e `official.wins.tsv` a partir de
  `src/main/resources/Movielist.csv`. Ao rodá-lo, nenhuma fixture mudou.
- `README.md` atualizado (seção de testes e aviso em "Usando outro CSV").
- Suíte: 56 testes, 0 falhas.
- Mutações no `Movielist.csv` (cada uma desfeita com `git checkout` antes da seguinte):

| Mutação | Testes que falharam |
|---|---|
| Vitória do Joel Silver 1991 → 1992 | 3 de 56 |
| `Matthew Vaughn` → `Matthew Vaughns` (filme vencedor de 2002) | 4 de 56 |
| `Road House` (1989, não vencedor) vira vencedor | 6 de 56 |

- Erros do agente nesta etapa: E17, E18.

## Erros do agente e correções

| # | Etapa | O que falhou | Como foi percebido | Correção |
|---|---|---|---|---|
| E1 | 1 | Um `echo ===` no zsh abortou o comando (exit 1) depois de o oráculo já ter impresso a saída | Erro `== not found` | Verificações refeitas num script separado |
| E2 | 3 | Ao procurar JDKs locais, o agente rodou `cat ~/.gradle/gradle.properties` e **exibiu credenciais de um repositório Nexus** na saída da sessão | Visto na saída do comando | Avisado ao autor na mesma etapa, com recomendação de rotacionar a senha. A credencial não foi usada nem gravada; a auditoria (item 10) confirmou com `git grep` que ela não está no projeto |
| E3 | 5 | O oráculo aceitava `"A"x`, travava (traceback) com arquivo vazio, e a fixture `empty_producers` estava errada (a célula `" and "` virava um produtor chamado `and`) | O oráculo não rejeitou todos os CSVs de `invalid/` | `csv.reader(strict=True)`, erro explícito para arquivo vazio, fixture refeita com `producers` só de espaços |
| E4 | 5 | Para achar as auto-configurações do Boot 4, o agente procurou `SqlInitializationAutoConfiguration`, que não existe nessa versão | Busca nos jars sem resultado | Usado `DataSourceInitializationAutoConfiguration`, listado no `AutoConfiguration.imports` |
| E5 | 5 | Os testes de CSV inválido falharam com o código já certo (a contagem exata não foi registrada; a saída estava truncada): o teste usava `rootCause()`, e a exceção não tem causa encadeada | Mensagem `Expecting actual throwable to have a root cause` | `NestedExceptionUtils.getMostSpecificCause` |
| E6 | 5 | O teste do log `206 filmes carregados` dependia da ordem: o contexto era criado no outro teste da classe | Uma saída truncada com `head` parecia toda verde; a contagem pelos XMLs mostrou 1 falha | Asserção movida para um teste que cria o próprio contexto |
| E7 | 6 | `producer_too_long.csv` estava em `invalid/`, mas é um CSV válido (falha só no banco) | `generate-fixtures.sh` acusou que o oráculo o aceitava | Movido para `scenarios/db_failure/` |
| E8 | 6 | Erro de compilação: assinatura errada de `MockMvcTester.method` | `./gradlew test` | `.method(m).uri(path)` |
| E9 | 5→6 | O agente parou depois do módulo `movies` pedindo OK para `awards`, embora o plano estivesse aprovado | Pergunta do autor (item 6) | `awards` implementado em seguida |
| E10 | 8 | O primeiro A/B de índices tinha deriva: o baseline piorava a cada rodada (64→76→86 ms) | Leitura das rodadas | Refeito com ordem rotacionada e `System.gc()` antes de cada medição, e decidido pelo ponta a ponta |
| E11 | 8 | Com aquecimento em `ApplicationReadyEvent`, a 1ª requisição continuou em ~1,1 s | Medição | Hipótese (a requisição chega durante o aquecimento) confirmada esperando 3 s: 86–114 ms. O autor então exigiu aquecimento antes da porta (item 9) |
| E12 | 9 | O teste-sonda registrava o mesmo bean duas vezes (`NoUniqueBeanDefinitionException`) | Falha por motivo errado | `@Bean` redundante removido; o vermelho passou a ser o esperado (0 leituras) |
| E13 | 9 | A justificativa contra `SmartInitializingSingleton` (callbacks na ordem de registro, `awards` antes de `movies`) **foi um raciocínio, não um experimento**: não foi demonstrada na sessão | Revisão ao escrever este registro | Registrado aqui. O que foi testado é a ordem da solução adotada (`SmartLifecycle` fase 0 antes da porta, com mutação) |
| E14 | 9 | Uma execução da medição teve carga de 12,5 s contra ~6 s nas demais | Leitura dos resultados | Mais 2 execuções rodadas e mediana de 5 reportada; a causa não foi isolada |
| E15 | 10 | O `timeout` não existe no macOS, e o `grep` do agente escondeu o erro (as medições de heap pareciam vazias) | Logs inexistentes | Refeito com `perl -e 'alarm 150; exec @ARGV'` |
| E16 | 11 | Com os testes herméticos, os 8 cenários de `AwardIntervalsScenariosTest` quebraram: `SpringApplicationBuilder.properties()` define propriedades **padrão**, de precedência mínima, e o novo `config/application.properties` passou à frente | Suíte vermelha | CSV do cenário passado como argumento de linha de comando |
| E17 | 10 | Os "testes herméticos" desligaram a suíte do `src/main/resources/Movielist.csv`, contrariando o requisito não funcional 2: alterar o arquivo não derrubava nenhum teste | Feedback do avaliador (item 13) | Testes apontados para `Movielist.csv` e cópia removida; provado com 3 mutações (item 13) |
| E18 | 13 | A primeira execução do experimento terminou com exit 1, que parecia falha de teste. Era o Gradle rodando na JVM 11 padrão do shell ("Gradle requires JVM 17 or later") | Saída completa do `./gradlew` | Rodado com `JAVA_HOME` do JDK 24 |

## Decisões de design e alternativas descartadas

| Decisão | Alternativa descartada | Motivo / quem decidiu |
|---|---|---|
| Carga com `SmartInitializingSingleton` + `TransactionTemplate` | `ApplicationRunner` | Exigido pelo autor (item 5); o runner roda depois de a porta abrir. Provado por teste e mutação |
| Leitura e validação de todo o CSV antes de qualquer escrita | Validar enquanto insere | Erro no CSV não deixa nada gravado; a transação cobre falhas do banco |
| Módulos planos com classes `package-private` | Subpacotes `internal` (só o Modulith protegeria) | Proteção também pelo compilador; proposto pelo agente, plano aprovado pelo autor |
| `MoviesApplication` em `com.outsera` (D1) | Manter em `com.outsera.movies`, gerando o módulo `com.outsera.movies.movies` | Proposto pelo agente; plano aprovado |
| Parser CSV próprio (D2) | `commons-csv` | A stack foi fixada pelo autor sem essa dependência |
| `@Value` com padrão para o caminho do CSV | Record `@ConfigurationProperties` (estava no plano) | Uma chave só; decisão do agente, registrada no relatório |
| Coluna `release_year` | `year` | `YEAR` é palavra reservada no H2 2.x |
| Sem chave estrangeira em `movie_producer` | FK | O autor pediu "só as chaves primárias" |
| Nenhum índice | A `movie(winner, release_year)`, B `movie(winner)`, C `movie_producer(producer, movie_id)`, D `movie(winner, id, release_year)` | Medido: ganho de consulta não provado (faixas sobrepostas), C ignorado pelo otimizador, carga 1–3 s mais lenta, requisição não melhora; e com o cache a consulta roda uma vez |
| Cache do resultado inteiro | Otimizar só a consulta | A consulta era 62% do tempo aquecido e quase todo o tempo frio |
| Aquecimento em `SmartLifecycle` fase 0, antes da porta | (a) `ApplicationReadyEvent`, pedido no item 8 e usado primeiro; (b) `SmartInitializingSingleton` | (a) deixava a 1ª requisição em ~1,1 s e o autor a trocou (item 9); (b) descartado pelo raciocínio de ordem (ver E13) |
| Alias `movielist.csv.location` mantendo `app.movies.csv-location` | Renomear a propriedade | O autor não escolheu; o agente optou por não quebrar o nome já documentado |
| Testes com `src/test/resources/config/application.properties` | `application.properties` na raiz dos recursos de teste | O da raiz substituiria o principal (perdendo `spring.sql.init.mode=always`); `config/` soma-se a ele. **Revertido no item 13** (ver E17) |
| Testes do dataset oficial lendo `src/main/resources/Movielist.csv` (item 13) | Cópia própria em `src/test/resources` | Exigência do avaliador: a suíte tem de falhar quando o arquivo padrão muda; o autor pediu para apontar os testes para ele |
| Esperados dos testes gerados pelo oráculo Python e versionados | Esperados calculados pelo próprio código | Pedido do autor (item 1); evita o teste confirmar o próprio bug |
| Cenários de carga com `ApplicationContextRunner`; cenários de `awards` com a aplicação inteira via `SpringApplicationBuilder` | Um `@SpringBootTest` por classe e cenário | Um contexto real por CSV sem dezenas de classes |
| Instrumentação e A/B de índices numa cópia descartável | Instrumentar o repositório e remover depois | O repositório nunca recebeu código temporário |
| Caminho `GET /producers/award-intervals` (S15) | — | O PDF não define; o autor não indicou outro |

## O que o autor aprovou ou mudou

- **Item 2:** aprovou S1, S3, S4, S5, S6, S8, S9, S10 e S13. Reescreveu S2; definiu S7 (na época `year`, `producers`,
  `winner` obrigatórias); ampliou S11; confirmou S12; criou S14.
- **Item 3:** aprovou S14a e S14b ("ok aprovado"). **A stack inteira foi decisão do autor**, e o agente só verificou.
- **Item 4:** definiu a forma da arquitetura (dois módulos, uma interface pública). O agente detalhou.
- **Item 5:** "OK no plano" aprovou o plano, incluindo D1 a D4, que não foram respondidos um a um. O mesmo prompt mudou
  S7 (`title` obrigatória; `winner` e `studios` opcionais), ampliou S9 (`,` com ou sem espaço), impôs
  `SmartInitializingSingleton` + `TransactionTemplate`, o nome `Movielist.csv`, lotes e só PKs.
- **Item 8:** definiu o método de avaliação de desempenho e o desenho do cache (`ApplicationReadyEvent` + dupla
  verificação).
- **Item 9:** mudou o aquecimento para antes da porta.
- **Item 11:** aprovou as três correções da auditoria.
- **Item 12:** proibiu o commit por enquanto.
- **Item 13:** pediu que os testes apontassem para o `Movielist.csv` e aprovou os ajustes propostos.

## Medições feitas na sessão

Jar real, JDK 25, opções padrão da JVM, CSV sintético de 1.000.000 de filmes. Todas as medições começaram depois do log
`filmes carregados`, e todo corpo de resposta medido foi comparado com o oráculo e era idêntico.

### Antes × depois (cliente bate assim que a porta aceita conexões)

| Métrica | Sem cache (3 exec.) | Cache com `ApplicationReadyEvent` (3 exec.) | Aquecimento antes da porta (5 exec.) |
|---|---:|---:|---:|
| Carga do CSV | 7,24 s | 7,35 s | 6,31 s (6,05–12,50) |
| start → porta aceitando | 7,68 s | 7,81 s | 8,01 s (7,41–14,25) |
| start → 1ª resposta correta | 8,81 s | 8,91 s | 8,10 s |
| 1ª requisição | 1.066,5 ms | 1.109,3 ms | 98,5 ms (86,1–124,2) |
| Requisições 2–10, mediana | 87,3 ms | 9,4 ms | 9,8 ms (8,5–11,2) |
| Requisições 2–10, pior caso | 105,4 ms | 14,7 ms | 11,2 ms |
| Tamanho da resposta | 1,58 MB | 1,58 MB | 1,58 MB |

Com o cliente esperando 3 s depois de a porta abrir, a 1ª requisição levou: sem cache 1.058,5 / 1.102,8 / 1.242,1 ms;
com cache (`ApplicationReadyEvent`) 85,6 / 114,2 / 95,7 ms.

RSS observado entre 1,8 e 3,4 GB, variando com o GC. Nenhuma diferença de RSS foi atribuída ao cache.

### Fases da requisição sem cache (instrumentação temporária, 3 execuções)

| Fase | 1ª requisição (fria) | Requisições 2–10 (27 amostras, mediana) |
|---|---:|---:|
| Consulta SQL + mapeamento (279.550 linhas) | 867,9 / 1.173,5 / 809,9 ms | 39,0 ms (62%) |
| Cálculo em Java | 42,6 / 50,2 / 42,1 ms | 19,7 ms |
| Serialização JSON | 38,1 / 48,5 / 35,7 ms | 3,2 ms |

### A/B de índices (consulta isolada, mediana de 5 execuções por rodada)

| Candidato | 1º experimento (com deriva) | 2º experimento (ordem rotacionada + GC) | Criação + `analyze` (2º) |
|---|---|---|---:|
| baseline | 64,0 / 76,4 / 86,0 (média 75,5) | 44,4 / 50,7 / 74,5 (média 56,6) | — |
| A `movie(winner, release_year)` | 56,1 / 63,3 / 50,7 (56,7) | 48,2 / 49,0 / 53,3 (50,2) | 1.038 ms |
| B `movie(winner)` | 66,4 / 60,0 / 49,0 (58,5) | 72,2 / 49,6 / 58,6 (60,1) | 649 ms |
| C `movie_producer(producer, movie_id)` | 53,4 / 55,6 / 49,3 (52,8) | 51,0 / 76,5 / 52,0 (59,8) | 5.358 ms |
| D `movie(winner, id, release_year)` | 46,4 / 53,5 / 42,4 (47,4) | 49,8 / 51,4 / 52,3 (51,2) | 1.098 ms |

Ponta a ponta no jar real, com o índice no `schema.sql` (3 execuções cada, sem cache):

| Variante | Carga | Requisição aquecida (mediana por execução) |
|---|---|---|
| sem índice | 5,46 / 5,93 / 5,47 s | 65,7 / 67,8 / 87,7 ms |
| A | 8,40 / 7,47 / 6,98 s | 96,6 / 95,7 / 87,2 ms |
| D | 6,51 / 7,00 / 9,67 s | 85,2 / 109,5 / 82,8 ms |

### Heap com 1.000.000 de filmes

| `-Xmx` | Resultado |
|---|---|
| 2g | Sobe: carga 6,07 s, porta 7,44 s, 1ª requisição 92,9 ms, mediana 7,2 ms |
| 1g | `OutOfMemoryError: Java heap space`, a aplicação não sobe |
| 512m | A aplicação não sobe |

### Build e testes

| Medição | Resultado |
|---|---|
| `./gradlew build` sob JDK 17, `GRADLE_USER_HOME` vazio (baixa Gradle e JDK 25) | `BUILD SUCCESSFUL in 33s` |
| `./gradlew build` sob o JDK 25 baixado | `BUILD SUCCESSFUL in 6s` |
| Clone limpo, launcher JDK 24, `GRADLE_USER_HOME` vazio | `BUILD SUCCESSFUL in 35s`, 52 testes, 0 falhas |
| Boot do jar com o CSV oficial | `Started MoviesApplication in 1.274 seconds` |
| Número de testes ao fim de cada etapa | 30 (`movies`) → 47 (`awards`) → 50 (cache) → 52 (aquecimento antes da porta) → 54 (correções da auditoria) |

## Estado ao fim do registro

- Última execução completa da suíte (item 13): 56 testes, 0 falhas.
- Commit `4b90aa1` feito antes do item 13. As mudanças do item 13 ainda não foram commitadas.
- Não foram executados `/code-review` nem lint.
