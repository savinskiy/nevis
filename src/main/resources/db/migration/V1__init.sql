CREATE TABLE clients (
    id           UUID PRIMARY KEY,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(320) NOT NULL,
    description  TEXT,
    social_links TEXT ARRAY   NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_clients_email UNIQUE (email)
);

CREATE TABLE documents (
    id                UUID PRIMARY KEY,
    client_id         UUID         NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    title             VARCHAR(500) NOT NULL,
    content           TEXT         NOT NULL,
    summary           TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_documents_client_id ON documents (client_id);
