# Deploy AI-RTS (free tier)

**Vercel is not compatible** — it targets static sites and serverless Node/Edge functions, not a long-running Java Spring Boot JVM.

This guide uses:

| Layer | Free service | Why |
|-------|--------------|-----|
| API (JVM) | [Render](https://render.com) free web service | Docker + always-on HTTP (sleeps after ~15 min idle on free tier) |
| Database | [Neon](https://neon.tech) free PostgreSQL | Persistent test history; survives API restarts |

Alternatives: Railway (limited free credits), Fly.io (small free allowance), Oracle Cloud always-free VM (more setup).

---

## 1. Create Neon PostgreSQL (free)

1. Sign up at https://neon.tech
2. Create a project (e.g. `ai-rts`)
3. Copy the **connection string** from the dashboard (format `postgresql://user:pass@host/neondb?sslmode=require`)

No schema setup needed — Flyway runs `V1__init.sql` on first API start.

---

## 2. Deploy API to Render (free)

### Option A — Blueprint (recommended)

1. Push this repo to GitHub
2. Go to https://dashboard.render.com → **New** → **Blueprint**
3. Connect the repo; Render reads [`render.yaml`](../render.yaml)
4. Set secrets when prompted:
   - `DATABASE_URL` — Neon connection string
   - `GITHUB_TOKEN` — GitHub PAT with `repo` read (for PR diff fetch)
5. Deploy

### Option B — Manual Docker web service

1. **New → Web Service** → connect GitHub repo
2. Settings:
   - **Runtime**: Docker
   - **Dockerfile path**: `ai-rts/api/Dockerfile`
   - **Docker context**: `ai-rts`
   - **Health check path**: `/actuator/health`
3. Environment variables:

| Variable | Value |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | Neon `postgresql://...` connection string |
| `AI_RTS_API_TOKEN` | random secret (CLI + GitHub Actions use this) |
| `GITHUB_TOKEN` | GitHub PAT |
| `PORT` | *(Render sets automatically)* |

4. **Create Web Service** — first build takes ~5–10 min (Maven inside Docker)

Your API URL will be like: `https://ai-rts-api.onrender.com`

Verify:

```bash
curl https://YOUR-APP.onrender.com/actuator/health
```

---

## 3. Wire GitHub Actions

In your **application repo** (the Java project whose tests you want to select), add secrets:

| Secret | Example |
|--------|---------|
| `AI_RTS_API_BASE_URL` | `https://ai-rts-api.onrender.com` |
| `AI_RTS_API_TOKEN` | same as Render env |
| `AI_RTS_REPO_ID` | your repo name (optional) |

Workflows already in this repo:

- `ai-rts-history-ingest.yml` — main branch → ingest Surefire XML
- `ai-rts-pr-select.yml` — PR → recommend → run subset

---

## 4. ONNX model (Phase 3)

The API ships with `rts-v1.onnx` (logistic regression trained on synthetic data aligned to Java features).

Retrain when you have real CI labels:

```bash
pip install -r ai-rts/model/requirements.txt
python ai-rts/model/train_and_export.py
git add ai-rts/api/src/main/resources/models/
# redeploy Render
```

Config:

| Env | Default |
|-----|---------|
| `AI_RTS_MODEL_PATH` | `classpath:models/rts-v1.onnx` |
| `AI_RTS_MODEL_ENABLED` | `true` |

---

## 5. Local prod-like stack

```bash
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL=postgresql://user:pass@host/neondb?sslmode=require
export AI_RTS_API_TOKEN=dev-secret
java -jar ai-rts/api/target/api-0.1.0-SNAPSHOT.jar
```

Or build Docker locally:

```bash
docker build -f ai-rts/api/Dockerfile -t ai-rts-api ai-rts
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL="postgresql://..." \
  -e AI_RTS_API_TOKEN=dev-secret \
  ai-rts-api
```

---

## Free-tier caveats

- **Render free**: service sleeps after inactivity; first request after sleep may take 30–60s (cold start)
- **Neon free**: 0.5 GB storage, compute suspends when idle — fine for MVP history
- **No Vercel**: use Render/Railway/Fly for the API; Neon only for Postgres (not the JVM)

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `401 Unauthorized` from API | Set `AI_RTS_API_TOKEN` on server and CLI/GitHub secret |
| Empty `recommendedSubset` | Run history ingest on `main` first (`ai-rts-history-ingest.yml`) |
| PR diff always empty | Set `GITHUB_TOKEN` on Render |
| DB connection failed | Use full Neon URL in `DATABASE_URL`; ensure `sslmode=require` |
| ONNX falls back to heuristic | Check logs for model load errors; verify `rts-v1.onnx` in JAR |
| Render crash `libstdc++.so.6` | Use Jammy-based Docker image (not Alpine); redeploy latest `api/Dockerfile` |
| App exits on ONNX load error | Fixed: ONNX failures fall back to heuristic without killing the JVM |
