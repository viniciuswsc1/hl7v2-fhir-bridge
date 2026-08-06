CREATE TABLE raw_message (
    id           BIGSERIAL PRIMARY KEY,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    control_id   TEXT NOT NULL,
    message_type TEXT NOT NULL,
    payload      TEXT NOT NULL,
    sha256       TEXT NOT NULL,
    CONSTRAINT uq_raw_message_control_id UNIQUE (control_id)
);

CREATE TABLE patient (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    given_name  TEXT NOT NULL,
    family_name TEXT NOT NULL,
    birth_date  DATE,
    sex         TEXT,
    cns         TEXT,
    cpf         TEXT
);

-- resource_id is kept separate from id (the row's technical PK) so a future
-- replay feature can insert a new version for the same resource_id without
-- changing the URL that GET /fhir/{type}/{id} already handed out.
CREATE TABLE fhir_resource (
    id                 BIGSERIAL PRIMARY KEY,
    resource_type      TEXT NOT NULL,
    resource_id        TEXT NOT NULL,
    version            INT NOT NULL DEFAULT 1,
    content            JSONB NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_message_id  BIGINT NOT NULL REFERENCES raw_message(id),
    CONSTRAINT uq_fhir_resource_type_id_version UNIQUE (resource_type, resource_id, version)
);
