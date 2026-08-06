# 0002 - Spring JDBC (JdbcClient) em vez de JPA/Hibernate

## Status

Aceito (rascunho — reescrever com minhas palavras).

## Contexto

Acesso a dados usa `Spring JdbcClient` com SQL escrito à mão, mapeado para records Java.
`hapi-fhir-jpaserver-starter` e Hibernate/JPA estão fora do stack por decisão de escopo do
projeto.

## Decisão

Repositórios (`RawMessageRepository`, `PatientRepository`, `FhirResourceRepository`) usam
`JdbcClient.sql(...)` com SQL explícito, mapeando o `ResultSet` direto para records.

## Trade-offs

Prós:
- SQL que roda é exatamente o SQL escrito — sem lazy loading, cascade ou dirty-checking
  implícitos, que são justamente o tipo de comportamento arriscado numa tabela append-only
  como `raw_message` (um `save()` acidental de uma entidade JPA gerenciada vira `UPDATE`).
- Fronteira de transação fica explícita no código (`@Transactional` em um método específico),
  não decidida por convenção de persistence context.
- Sem mapeamento objeto-relacional para manter sincronizado com o schema — um record e uma
  query, só isso.

Contras:
- Mais código repetitivo por repositório (cada query é escrita à mão).
- Sem dirty-checking/cascade automático — toda escrita é explícita, inclusive as óbvias.
- Mudança de schema exige atualizar SQL e record manualmente, sem checagem em tempo de
  compilação ligando os dois.

## Alternativas consideradas

- JPA/Hibernate — fora do stack por decisão de escopo (ver `hapi-fhir-jpaserver-starter`
  banido pela mesma razão: monta o projeto inteiro por baixo dos panos).
- MyBatis ou outro SQL mapper — não escolhido; `JdbcClient` já é padrão do Spring Boot 3.2+,
  zero dependência extra.
