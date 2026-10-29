# Deployment to an existing Google Cloud project

The current Bachat Gat deployment uses:

| Resource | Value |
|---|---|
| Project | `bachat-gat-development` |
| Region | `asia-south1` |
| Firestore database | `(default)` |
| Storage bucket | `bachat-gat-development-docs-2026` |
| Artifact Registry repository | `bachatgat-app` |
| Cloud Run service | `bachatgat-service` |

## Authenticate and select the project

```powershell
gcloud auth login
gcloud auth application-default login
gcloud config set project bachat-gat-development
```

Create production secrets in Secret Manager and grant the Cloud Run runtime service account `roles/secretmanager.secretAccessor`. Never commit service-account JSON keys.

## Build and deploy

From the repository root:

```powershell
gcloud builds submit `
  --config=cloudbuild.yaml `
  --substitutions=_CORS_ALLOWED_ORIGINS=https://bachatgat-service-230889435212.asia-south1.run.app
```

The build packages the application, builds and pushes the container, and deploys Cloud Run with production demo data disabled. Confirm the build ends with `STATUS: SUCCESS`.

## Verify

```powershell
$serviceUrl = gcloud run services describe bachatgat-service `
  --project=bachat-gat-development `
  --region=asia-south1 `
  --format="value(status.url)"

Invoke-RestMethod "$serviceUrl/actuator/health"
```

The health response must report `UP`. Test login and group-authorized access using a real account.

## Emergency administrator recovery

`ADMIN_PASSWORD_RESET_TOKEN` must be added only for a controlled password reset, then removed immediately. Never place the token or password in source control, screenshots, shell history, or chat.
