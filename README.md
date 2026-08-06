# hl7-fhir-bridge

Um motor de tradução HL7 v2 -> FHIR: parsing, MPI, armazenamento imutável com auditoria. Não
reimplementar um servidor FHIR do zero.

**Não é um servidor FHIR conformante.** Não implementa a spec completa (busca, `_history`,
`CapabilityStatement`, validação de perfil etc)

## O que é

Motor de integração que recebe mensagens HL7 v2, guarda a mensagem crua sem alterar depois
e deriva recursos FHIR R4 a partir dela. O plano (ainda não implementado) inclui MPI
determinístico, mapeamento terminológico com quarentena, cifragem de campo sensível e
auditoria append-only garantida por permissão de banco.

## O que está implementado agora (fatia vertical 1)

Um caminho só, ponta a ponta:

```
POST /messages   (text/plain, corpo = mensagem ORU^R01)
  -> grava a mensagem crua (imutável, idempotente por MSH-10)
  -> parseia com HAPI HL7v2
  -> extrai paciente (PID) e primeiro resultado (OBX)
  -> monta um Observation FHIR R4 (HAPI FHIR)
  -> grava paciente e Observation (jsonb)

GET /fhir/Observation/{id}
  -> devolve o Observation serializado
```

### Decisões desta fatia

Reenviar a mesma mensagem (mesmo `control_id` / MSH-10) não duplica nada. A segunda
chamada devolve `200` com o `Observation` que já existe, não `409` (motivo tá comentado no
controller). Se a mensagem crua já tinha sido salva mas o mapeamento nunca deu certo, a
chamada tenta mapear de novo em vez de falhar direto.

A mensagem crua fica salva mesmo se o mapeamento falhar depois: o `INSERT` em
`raw_message` acontece antes do parse de PID/OBX, numa transação separada. PID ou OBX
malformado retorna `400`, mas não apaga o registro da mensagem.

Erro nunca devolve dado do paciente. As respostas de erro usam mensagens fixas, nunca o
texto da exceção do parser HL7 (que pode conter pedaço da mensagem original).

## O que ficou de fora por enquanto

- MPI: cada mensagem cria um paciente novo, sem dedupe por CNS/CPF/chave composta.
- Mapeamento terminológico: `Observation.code` usa o código bruto do OBX-3, sem tradução
  pra LOINC nem quarentena de código não mapeado.
- Cifragem de campo, blind index (HMAC), autenticação.
- ADT (A01/A08), `Encounter`, `DiagnosticReport`.
- MLLP / socket TCP. Ingestão por enquanto é só HTTP.
- Fila, DLQ, replay de mensagem.

## Stack

Java 21, Maven, Spring Boot 3.5, HAPI HL7v2 (`hapi-base` + `hapi-structures-v251`), HAPI
FHIR (`hapi-fhir-structures-r4`), PostgreSQL 16 + Flyway, Spring JDBC (`JdbcClient`, sem
JPA/Hibernate), JUnit 5 + AssertJ + Testcontainers.

## Como rodar

```bash
docker compose up --build
curl http://localhost:8080/actuator/health

curl -X POST http://localhost:8080/messages \
  -H "Content-Type: text/plain" \
  --data-binary @src/test/resources/sample-oru-r01.hl7
# -> {"id":1,"controlId":"MSG00001","duplicate":false,"observationId":"<uuid>"}

curl http://localhost:8080/fhir/Observation/<uuid>
```

`docker-compose.yml` sobe só dois serviços: `app` e `postgres`.

## Testes

```bash
mvn test
```

O teste de integração (`MessageIngestionIntegrationTest`) sobe um Postgres real com
Testcontainers e cobre o fluxo inteiro: ingestão, idempotência e rejeição de mensagem
malformada. Precisa de Docker rodando.

## Estrutura

```
com.hl7fhirbridge
├── ingestion/   POST /messages, RawMessage (append-only)
├── hl7/         parsing HAPI HL7v2 (Hl7Parser, OruR01Extractor)
├── patient/     Patient (sem MPI ainda, cada mensagem cria um novo)
├── fhir/        Observation FHIR R4, persistência jsonb, GET /fhir/Observation/{id}
└── error/       tratamento de erro sem vazar dado do paciente
```

Schema fica em `src/main/resources/db/migration/V1__init.sql`. Decisões de design maiores
estão em [`docs/adr/`](docs/adr/).

## ADRs

- [0001 - jsonb para recursos FHIR](docs/adr/0001-jsonb-para-recursos-fhir.md)
- [0002 - JDBC em vez de JPA](docs/adr/0002-jdbc-em-vez-de-jpa.md)
