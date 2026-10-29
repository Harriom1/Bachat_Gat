# Deployment to a new Google Cloud project

Use this guide when the project and data resources do not yet exist.

## 1. Create the project

Create a globally unique project ID in the Google Cloud Console or with `gcloud projects create`. Attach billing before enabling billable services. Record the exact project ID; it cannot be changed later.

## 2. Enable services and create resources

Enable Firestore, Cloud Storage, Artifact Registry, Cloud Build, Cloud Run, Secret Manager, and Cloud Logging APIs.

Create a Firestore Native `(default)` database, a private Cloud Storage bucket, and an Artifact Registry Docker repository in the deployment region. Use uniform bucket-level access and public access prevention.

Do not manually create demo Firestore collections. Production starts with demo data disabled.

## 3. Configure identities and secrets

Grant the Cloud Run runtime service account access to Firestore, the private bucket, and the production secrets. Create strong random versions for `JWT_SECRET` and `PAYMENT_HMAC_SECRET` in Secret Manager.

## 4. Deploy

Replace the placeholders below with the new project ID and service URL, and update project-specific values in `cloudbuild.yaml`:

```powershell
gcloud config set project YOUR_PROJECT_ID
gcloud builds submit `
  --config=cloudbuild.yaml `
  --substitutions=_CORS_ALLOWED_ORIGINS=https://SERVICE_URL
```

## 5. Verify

Check `/actuator/health`, login with a real provisioned account, confirm Firestore records survive a restart, and test document authorization. Never publish secrets, JWTs, or private documents.
