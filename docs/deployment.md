# Deployment

The site has two parts: the React frontend, built to static files, and the Java API, which runs as a
Docker container built from `backend/Dockerfile`. The frontend calls the API from the browser, so the API
must allow the site's origin through `ALLOWED_ORIGINS`.

## Backend

### Environment variables

| Name | Set by | Value |
| --- | --- | --- |
| `ALLOWED_ORIGINS` | You, on the host | Comma-separated frontend origins allowed to call the API, with no trailing slash |
| `PORT` | The host, optionally | The port the container must listen on; the app reads it and defaults to 8080 |

Nothing secret is needed. The app has no database, accounts or API keys.

### Check it

Open `<api-url>/api/health`. It returns:

```json
{"status":"ok"}
```

Use this path as the host's health check.

### Memory and storage

- **Memory:** the image sets `-XX:MaxRAMPercentage=75` so the Java heap stays inside the container's
  memory limit, and uploads are capped at 200,000 rows.
- **Storage:** runs are kept in memory only, so every run is lost when the service restarts or redeploys.

### Build and run the image locally

With Docker installed, from the repository root:

```bash
docker build -t txnscreen-api backend
docker run --rm -p 8080:8080 -e ALLOWED_ORIGINS=http://localhost:5173 txnscreen-api
curl http://localhost:8080/api/health
```

## Frontend

Build from `frontend/` with `npm run build`. The output is in `frontend/dist`. Set `VITE_API_BASE_URL` to
the API's URL, with no trailing slash, at build time.
