# 0001 - jsonb para armazenar recursos FHIR

## Status

Aceito (rascunho, reescrever com minhas palavras).

## Contexto

`fhir_resource.content` guarda o `Observation` (e futuramente outros recursos FHIR) inteiro
serializado, numa coluna `jsonb`, em vez de colunas normalizadas por campo do recurso.

## Decisão

Serializar o recurso FHIR (via `HAPI FHIR JSON parser`) e gravar o resultado direto em `jsonb`.

## Trade-offs

Prós:
- Recursos FHIR variam muito de forma entre tipos (`Observation` != `Patient` != `Encounter`);
  normalizar cada um em colunas próprias significa uma tabela nova a cada recurso novo.
- `jsonb` do Postgres aceita índice GIN e operadores de query se algum dia precisar consultar
  por dentro do conteúdo, sem precisar disso agora.
- Não fingimos ser um servidor FHIR de busca/consulta. O conteúdo é lido de volta como está,
  não interrogado campo a campo pelo banco.

Contras:
- Sem integridade referencial nem checagem de schema no nível do banco para o conteúdo interno
  (`subject.reference`, por exemplo, não é uma FK de verdade).
- Query/filtro por campo interno do JSON exige operadores `jsonb` específicos, não SQL comum.
- Versionamento é responsabilidade da aplicação (coluna `version` + `resource_id` estável),
  não algo que o `jsonb` resolve sozinho.

## Alternativas consideradas

- Schema relacional normalizado por tipo de recurso: descartado, overhead de schema grande
  para algo que não pretende ser um servidor FHIR conformante.
- Um documento externo (Mongo etc.): descartado, stack fixa em Postgres, mais uma peça em
  movimento sem justificativa clara para o escopo do projeto.
