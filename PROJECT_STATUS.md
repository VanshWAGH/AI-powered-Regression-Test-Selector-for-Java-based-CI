# AI-powered Regression Test Selector (AI-RTS) — Project Status

## Targets (MVP v1)
- **Inputs**: GitHub PR diff + historical JUnit/Allure-style test run data (JIRA intentionally **not used**)
- **Outputs**:
  - ranked list of tests to run (aiming 30–60% of suite)
  - recommended fast subset (target <10 minutes total)
- **Goals**: 90%+ regression recall, <10s latency per PR (with caching), Java 17, Maven, JUnit 5
- **Constraints**: pure Java where possible (ONNX optional), H2 for PoC, Spring Boot 3.x

## Architecture (implemented skeleton)
Data ingestion → Feature engineering → Model service (ONNX optional) → Recommendation engine → REST API/CLI → CI integration

## What is built now

### 1) Maven multi-module structure
Root folder: `ai-rts/`
- `ai-rts/pom.xml` (parent)
- `ai-rts/core/` (domain + logic)
- `ai-rts/api/` (Spring Boot REST)
- `ai-rts/cli/` (standalone runner)
- `ai-rts/integration/` (CI artifacts + simulation test)

### 2) Core module (`ai-rts/core`)
- **Entities**:
  - `TestRun` (testId, result, duration, timestamp, prId)
  - `CodeChange` (filePath, linesAdded, linesRemoved, methodsTouched)
  - `TestMetadata` (className, methodName, tags, type, avgDuration)
- **Repositories** (Spring Data JPA):
  - `TestRunRepository`, `CodeChangeRepository`, `TestMetadataRepository`
- **Services**:
  - `GitCloneService` (GitHub PR files diff ingestion via GitHub API; supports `GITHUB_TOKEN`)
  - `TestHistoryService` (loads metadata/runs from DB)
  - `TestHistoryIngestionService` (ingests JUnit XML + Allure result JSON into DB)
  - `FeatureExtractor` (builds 12 features per test)
  - `ModelService` (ONNX detection via reflection + heuristic fallback scoring)
  - `RecommendationEngine` (ranking + subset selection with time budget)
- **Unit tests**:
  - feature vector test + subset selection test
  - JUnit XML parser test + GitHub repo URL parser test

### 3) API module (`ai-rts/api`)
- Spring Boot app: `com.ai.rts.api.ApiApplication`
- Endpoint:
  - `POST /api/v1/{repoId}/{prId}/recommend`
  - `POST /api/v1/{repoId}/{prId}/history/ingest` (ingest JUnit/Allure history)
  - Request: `{ repoUrl, prNumber, testHistoryDays }`
  - Response: `{ rankedTests, recommendedSubset, metrics }`
- Fixed runtime issues:
  - executable jar packaging via Spring Boot `repackage`
  - repository/entity scanning enabled for `core`
  - explicit `@PathVariable` names to avoid reflection/`-parameters` issues
- H2 in-memory DB default configuration (`application.yml`)

### 4) CLI module (`ai-rts/cli`)
- `com.ai.rts.cli.Main` outputs a Surefire-style command string (currently placeholder test list)
- JUnit test validates CLI prints `mvn test -Dtest=...`

### 5) Integration module (`ai-rts/integration`)
- GitHub Actions workflow template
- Webhook JSON template
- Jenkins/GitLab snippets (optional)
- 500-test selection simulation test

## Known gaps / limitations right now (expected for MVP skeleton)
- **No automatic pipeline to fetch CI artifacts** yet (you must send JUnit/Allure documents to the ingest endpoint for now).
- **No seeding of sample history/metadata** so API may return empty ranked/subset lists until data exists.
- **Model inference** is fallback heuristic only; ONNX session execution not wired to an actual model file.
- **Latency/caching** not implemented yet (no local repo cache, no PR feature cache).
- **DB migrations** (Flyway) not added yet.

## How to run (current)

### Build
From `ai-rts/`:
```bash
mvn -pl api -am clean package
```

### Run API
```bash
java -jar api/target/api-0.1.0-SNAPSHOT.jar
```

### Call API (PowerShell example)
```powershell
$body = @{ repoUrl = "https://github.com/VanshWAGH-CS/ai-rts-test"; prNumber = 1; testHistoryDays = 30 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/ai-rts-test/1/recommend" -ContentType "application/json" -Body $body
```

## Phase-wise roadmap (what to do next)

### Phase 1 — Make it useful with real data (PoC)
- Implement **GitHub PR diff ingestion** ✅ (GitHub API PR files + `GITHUB_TOKEN`)
- Implement **JUnit XML + Allure ingestion** ✅ (via `/history/ingest`)
- Next: implement **artifact fetching** from GitHub Actions (download surefire/allure artifacts automatically)
- Add a **data seeder** for local demo:
  - insert sample `TestMetadata` + `TestRun` so API returns ranked tests immediately

### Phase 2 — Better features + selection quality
- Add richer coupling/features:
  - changed package overlap with test class package
  - recency-weighted fail rate
  - flakiness based on alternating outcomes
  - duration normalization + percentile bucket
- Add deterministic selection policies:
  - always include `critical` tag
  - enforce time budget first, then coverage-like constraints

### Phase 3 — Real ONNX inference (still pure Java)
- Define stable feature ordering/spec version
- Add ONNX model loading from file (config path)
- Implement actual inference + confidence logging
- Keep heuristic fallback on any model failure

### Phase 4 — CI-first hardening (GitHub Actions)
- Add workflow that:
  - collects test reports from previous runs (artifact download)
  - calls selector API
  - runs returned tests
- Add observability:
  - latency timers, selection size/reduction, fallback counts

### Phase 5 — Production readiness
- Switch H2 → PostgreSQL by profile
- Add Flyway migrations + retention policy
- Add auth (API key/JWT) for multi-repo usage
- Add integration tests that simulate 100 PRs with persisted history and validate recall/reduction metrics

