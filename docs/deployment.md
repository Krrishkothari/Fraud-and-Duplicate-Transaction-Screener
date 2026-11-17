# Deployment

The site has two parts: the React frontend on Vercel, and the Java API on Render, because Vercel does not
run Java servers. The frontend calls the API from the browser, so the API must allow the site's origin
through `ALLOWED_ORIGINS`.

## Backend on Render

The API runs as a Docker container on a free Render web service, built from `backend/Dockerfile` as
described in `render.yaml` at the repository root.

### Create the service

1. Sign in to [Render](https://render.com) and connect the GitHub repository.
2. Choose **New**, then **Blueprint**, and select this repository. Render reads `render.yaml` and proposes
   one web service, `txnscreen-api`, on the free plan.
3. When Render asks for `ALLOWED_ORIGINS`, enter the frontend's origins, comma-separated, with no
   trailing slash. Until the custom domain is set up, use the Vercel URL, for example
   `https://txnscreen.vercel.app`. The final value is set in the frontend half of this guide.
4. Apply the blueprint. Render builds the image and starts the container. The build downloads every
   Maven dependency, so it takes a few minutes.

### Check it

Open `https://<service-name>.onrender.com/api/health`. It returns:

```json
{"status":"ok"}
```

Render also calls this path itself as the service's health check.

### Environment variables

| Name | Set by | Value |
| --- | --- | --- |
| `ALLOWED_ORIGINS` | You, in the Render dashboard | Comma-separated frontend origins allowed to call the API |
| `PORT` | Render | The port the container must listen on; the app reads it and defaults to 8080 |

Nothing secret is needed. The app has no database, accounts or API keys.

### Free plan limits

- **Sleep:** a free service sleeps after 15 minutes without traffic, and the next request takes about a
  minute to wake it. Open the site 2 minutes before a demo or viva. The frontend tells users when a request
  is slow because the server is starting up.
- **Memory:** the service has 512 MB of memory. The image sets `-XX:MaxRAMPercentage=75` so the Java heap
  stays inside it, and uploads are capped at 200,000 rows.
- **Storage:** the filesystem is temporary, and runs are kept in memory only, so every run is lost when the
  service sleeps, restarts or redeploys.

### Build and run the image locally

With Docker installed, from the repository root:

```bash
docker build -t txnscreen-api backend
docker run --rm -p 8080:8080 -e ALLOWED_ORIGINS=http://localhost:5173 txnscreen-api
curl http://localhost:8080/api/health
```
