# Nevis Search API

A search service for wealth advisors: store clients and their documents, then search across both with one query.

- **Clients** are found by name, email or description, ignoring case and matching parts of words: `NevisWealth`
  (or just `nevis`) returns the client with email `john.doe@neviswealth.com`.
- **Documents** are found by meaning as well as by words: `address proof` returns a document that contains
  `utility bill`, and `retiring early` finds meeting notes saying the client "intends to stop working at 60".
- **Summaries** (the optional feature): Claude summarises each document in the background after upload; the
  summary is returned with the document.

Stack: Java 21, Spring Boot 4, PostgreSQL 17 (source of truth), Elasticsearch 9 (search, including vector search),
an embedding model that runs inside the app (all-MiniLM-L6-v2, no API key needed), and optionally Claude for
summaries.

## Setup

### Run with Docker

```bash
docker compose up --build
```

This starts PostgreSQL, Elasticsearch and the API on <http://localhost:8080>. The first build downloads
dependencies and takes a few minutes.

To enable document summaries, put an Anthropic API key in a `.env` file next to `docker-compose.yml` (the file is
gitignored), then start as above:

```bash
echo "ANTHROPIC_API_KEY=sk-ant-..." > .env
```

### Run from source

Requires JDK 21. Start only the databases with Docker, then run the app:

```bash
docker compose up -d db elasticsearch
./gradlew bootRun
```

### Demo data

```bash
scripts/seed.sh                      # against http://localhost:8080
scripts/seed.sh http://other-host    # or any other base URL
```

### Tests

```bash
./gradlew test
```

Requires Docker: the tests start PostgreSQL 17 and Elasticsearch 9.4.6 with
[Testcontainers](https://testcontainers.com/), the same versions as `docker-compose.yml`, and exercise the API
over HTTP against them. Search behaviour is defined by the Elasticsearch index settings, so it is tested this way
rather than with mocks.

## API

Interactive documentation: <http://localhost:8080/swagger-ui.html> · OpenAPI spec:
[`docs/openapi.yaml`](docs/openapi.yaml) (also served at `/v3/api-docs`).

| Method | Path                       | Description                    | Success | Errors   |
|--------|----------------------------|--------------------------------|---------|----------|
| POST   | `/clients`                 | Create a client                | 201     | 400, 409 |
| POST   | `/clients/{id}/documents`  | Add a document to a client     | 201     | 400, 404 |
| GET    | `/search?q=`               | Search clients and documents   | 200     | 400      |
| GET    | `/clients`                 | List all clients, by name      | 200     |          |
| GET    | `/clients/{id}`            | Get a client                   | 200     | 404      |
| GET    | `/clients/{id}/documents`  | List a client's documents, newest first | 200 | 404 |
| GET    | `/documents/{id}`          | Get a document                 | 200     | 404      |

Additions to the specified models:

- Emails are stored in lower case and must be unique (`409 Conflict`).
- `Document` also returns `summary` once Claude has written one.
- Search results carry `type`, `score` and `highlights` (the matching fragments, so you can see *why* a result
  matched) next to the `client` or `document`.
- Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`application/problem+json`);
  validation errors list the offending fields under `errors`.

## Examples

Real responses after `scripts/seed.sh` (ids and timestamps will differ).

### Create a client and a document

```bash
curl -X POST localhost:8080/clients -H 'Content-Type: application/json' -d '{
  "first_name": "John", "last_name": "Doe", "email": "john.doe@neviswealth.com",
  "description": "Retired teacher, cautious investor interested in ESG funds",
  "social_links": ["https://linkedin.com/in/johndoe"]
}'
```

```json
{
  "id": "ffe75d65-31f1-45cb-9b1a-3e9e61982a1f",
  "first_name": "John",
  "last_name": "Doe",
  "email": "john.doe@neviswealth.com",
  "description": "Retired teacher, cautious investor interested in ESG funds",
  "social_links": ["https://linkedin.com/in/johndoe"]
}
```

```bash
curl -X POST localhost:8080/clients/ffe75d65-31f1-45cb-9b1a-3e9e61982a1f/documents \
  -H 'Content-Type: application/json' -d '{
  "title": "Scan 2024-03-14",
  "content": "Utility bill from EDF Energy for March 2024. Account holder: John Doe, 12 Baker Street, London NW1 6XE. Amount due: 84.20 GBP."
}'
```

### Find a client by email domain

```bash
curl 'localhost:8080/search?q=NevisWealth'
```

```json
[
  {
    "type": "client",
    "score": 0.6931471,
    "highlights": { "email": ["john.doe@<em>neviswealth</em>.com"] },
    "client": {
      "id": "ffe75d65-31f1-45cb-9b1a-3e9e61982a1f",
      "first_name": "John",
      "last_name": "Doe",
      "email": "john.doe@neviswealth.com",
      "description": "Retired teacher, cautious investor interested in ESG funds",
      "social_links": ["https://linkedin.com/in/johndoe"]
    }
  }
]
```

`nevis`, `wealth`, `john`, `doe` and `teachers` find the same client.

### Find a document by a related term

```bash
curl 'localhost:8080/search?q=address%20proof'
```

The seeded bill never says "address proof"; it matches through the related terms `gas bill` and `utility bill`
(content shortened here; the full bill is about 1,200 characters):

```json
[
  {
    "type": "document",
    "score": 5.8375707,
    "highlights": {
      "content": [
        "EDF Energy - Your electricity and <em>gas bill</em>\nBill date: 14 March 2024. Account number: 6719 2204 581.",
        "Account holder: Mr John Doe\nSupply address: 12 Baker Street, London NW1 6XE\n\n<em>Utility bill</em> summary\nBalance"
      ]
    },
    "document": {
      "id": "888c5209-8f9d-4ef3-8a1e-fae13e0f9ea6",
      "client_id": "0e466fb8-1e94-4561-acea-c80f1d5bb8fc",
      "title": "Scan 2024-03-14",
      "content": "EDF Energy - Your electricity and gas bill\nBill date: 14 March 2024. Account number: 6719 2204 581. ...",
      "summary": "This is an EDF Energy electricity and gas bill dated 14 March 2024 for Mr John Doe at 12 Baker Street, London NW1 6XE (account 6719 2204 581), covering 12 February to 11 March 2024. The total due is 84.20 GBP after VAT and a 22.88 GBP Direct Debit discount, and it will be collected by Direct Debit on or after 28 March 2024. The gas meter reading was estimated, so an actual reading may need to be submitted via the app to correct the bill, and the EDF Simply Fixed tariff runs until 30 November 2024, with no exit fee for switching in the last 49 days.",
      "created_at": "2026-10-01T14:48:30.165861Z"
    }
  }
]
```

### Find a document by meaning

```bash
curl 'localhost:8080/search?q=retiring%20early'
```

The meeting notes never say "retiring early". `highlights.passage` is the passage that matched by meaning;
`highlights.content` shows the words that matched too (`retirement`, via stemming). Shortened:

```json
[
  {
    "type": "document",
    "score": 2.2981339,
    "highlights": {
      "content": [
        "<em>Retirement</em> plan. John wants to live off his pension savings and a modest drawdown from the ISA."
      ],
      "passage": [
        "Asset allocation. Because John intends to stop working at 60, in three years, we agreed to reduce the equity allocation from 70 to 60 percent and move the difference into short-dated bonds and a cash buffer. ..."
      ]
    },
    "document": {
      "id": "d1ceb114-690d-4528-ac35-ea8cfd720605",
      "title": "Annual review 2024",
      "...": "..."
    }
  }
]
```

### Errors

```bash
curl 'localhost:8080/search'
```

```json
{ "title": "Bad Request", "status": 400, "detail": "Validation failed", "instance": "/search", "errors": { "q": "is required" } }
```

```bash
curl -X POST localhost:8080/clients -H 'Content-Type: application/json' \
  -d '{"first_name": "J", "last_name": "D", "email": "John.Doe@neviswealth.com"}'
```

```json
{ "title": "Conflict", "status": 409, "detail": "Client with email john.doe@neviswealth.com already exists", "instance": "/clients" }
```

## How it works

**PostgreSQL is the source of truth; Elasticsearch is the search index.** Creating a client or document writes the
row and indexes it in Elasticsearch within the same database transaction: if Elasticsearch fails, the insert is
rolled back and the request can simply be retried. Indexing waits for an Elasticsearch refresh, so a new client or
document is searchable as soon as the `POST` returns.

At startup each index is checked against its definition file: an index that is missing, or was created from an
older version of the file, is (re)created and refilled from PostgreSQL. A fingerprint of the definition is stored
in the index metadata for this, so changes to analyzers or synonyms deploy without manual steps.

**Search** runs one query over two indices, `clients` and `documents`, defined entirely in
[`src/main/resources/elasticsearch`](src/main/resources/elasticsearch):

- **Clients:** names are lower-cased and accent-folded; emails are split into words at every non-alphanumeric
  character, so `john.doe@neviswealth.com` is indexed as `john`, `doe`, `neviswealth`, `com` and `NevisWealth`
  matches it. Names and emails are also indexed as slices of 3–20 characters, so part of a word matches too
  (`nevis`, `wealth`); whole-word matches score higher, so `john` ranks John Doe above Alice Johnson. Descriptions
  use English stemming (`teachers` matches `teacher`).
- **Documents:** title and content use English stemming and stop words. At query time a
  [synonym graph](src/main/resources/elasticsearch/documents.json) expands a concept into the document types that
  satisfy it: `address proof` also searches for `utility bill`, `bank statement`, `council tax`…; likewise for
  identity, income, tax, KYC, retirement, investments, risk profile, estate planning, insurance and mortgages.
  Expansion is one-way: searching `utility bill` does not return other proofs of address.
- **Documents by meaning (embeddings):** each document is split into passages (its title, then each paragraph,
  long ones cut at sentence ends), and every passage is turned into a 384-number vector by the all-MiniLM-L6-v2
  model, which runs inside the application. The vectors are stored in Elasticsearch as a nested `dense_vector`
  field. A query is embedded the same way, and Elasticsearch's kNN search finds the passages closest to it, so a
  document is found even when it shares no words with the query: `how much risk can she handle` finds a
  questionnaire saying the client "could tolerate a loss of up to 25 percent". A passage must reach a cosine
  similarity of 0.30 (`nevis.search.min-similarity`); on the demo data related passages scored 0.34–0.54 and
  unrelated ones at most 0.26, so `football results` finds nothing. The best passage is returned under
  `highlights.passage`.

Both run as one Elasticsearch query: a document matches by words (BM25, with synonyms and partial names) **or** by
meaning, and matching both ranks it higher. The synonyms still matter next to the embeddings: they encode
domain facts a general-purpose model doesn't reliably know, while the embeddings cover wording nobody wrote a
rule for. Results are ordered by score, at most 20 (`nevis.search.max-results`). The synonym list is plain
configuration: a domain expert can extend it without touching code.

**Summaries** are generated after the document is committed, on a small background pool, so uploads stay fast.
The summary is stored in PostgreSQL and added to the search index (without recomputing embeddings), and from then
on appears on the document and in search results. Without an API key, or while Claude is unavailable (e.g. overloaded), documents simply have no summary;
those still missing one are queued again at startup and every 5 minutes (`nevis.summary.retry-interval`).

## Configuration

| Environment variable               | Default                                   | Purpose                      |
|------------------------------------|-------------------------------------------|------------------------------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/nevis`, `nevis`, `nevis` | PostgreSQL connection |
| `ELASTICSEARCH_URL`                | `http://localhost:9200`                   | Elasticsearch connection     |
| `PORT`                             | `8080`                                    | HTTP port                    |
| `ANTHROPIC_API_KEY`                | unset                                     | Enables document summaries   |
| `ANTHROPIC_MODEL`                  | `claude-opus-5-5`                         | Model used for summaries     |

Never put the API key in `application.yml`, which is committed; use `.env` (Docker) or the environment.

With an API key set, document text is sent to the Anthropic API to be summarised. Requests opt in to the API's
server-side model fallback, so a request declined by the primary model's safety filters is retried on a fallback
model instead of failing.
## Deployment (Google Cloud)

The service runs on one Compute Engine VM (e2-medium, `europe-west1-b`) with the same `docker-compose.yml`; only
port 8080 is open to the internet. On every boot, [`deploy/gcp/startup.sh`](deploy/gcp/startup.sh) installs
Docker if needed, downloads the source from a private bucket, writes the Anthropic key from instance metadata
into `.env` and runs `docker compose up -d --build`. Data lives on the VM's disk and survives restarts.

Common commands (project `nevis-search-5923`, zone `europe-west1-b`):

```bash
# start / stop (a stopped VM only costs its disk); the public IP changes on every start
gcloud compute instances start nevis-search --zone europe-west1-b
gcloud compute instances stop nevis-search --zone europe-west1-b
gcloud compute instances describe nevis-search --zone europe-west1-b \
  --format="get(networkInterfaces[0].accessConfigs[0].natIP)"

# deploy new code: upload a source archive, then reboot so the startup script rebuilds
tar --exclude=./build --exclude=./.gradle --exclude=./.idea --exclude=./.env --exclude=./.git -czf nevis.tgz .
gsutil cp nevis.tgz gs://nevis-search-5923-deploy/nevis.tgz
gcloud compute instances reset nevis-search --zone europe-west1-b

# remove everything
gcloud projects delete nevis-search-5923
```
