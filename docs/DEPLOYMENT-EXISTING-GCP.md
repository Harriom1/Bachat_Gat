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
| Production login | [Bachat Gat sign-in](https://bachatgat-service-230889435212.asia-south1.run.app/login.html) |

## Authenticate and select the project

```powershell
gcloud auth login
gcloud auth application-default login
gcloud config set project bachat-gat-development
```

Create production secrets in Secret Manager and grant the Cloud Run runtime service account `roles/secretmanager.secretAccessor`. Never commit service-account JSON keys.

## Build and deploy

From the repository root, supply the verified Brevo sender email and Firebase web app API key from your deployment settings:

```powershell
gcloud builds submit `
  --config=cloudbuild.yaml `
  "--substitutions=_REPORT_EMAIL_FROM=$reportEmailFrom,_FIREBASE_WEB_API_KEY=$firebaseWebApiKey"
```

The build packages the application, builds and pushes the container, and deploys Cloud Run with production demo data disabled. Confirm the build ends with `STATUS: SUCCESS`.

Run the Java test suite against a fresh local Firestore emulator before submitting the build. The Docker build packages the JAR once. The existing Cloud Run scaling and resource settings apply.

### Static asset releases

Bump the `perf-v1` prefix together in the static pages, stylesheet and dictionary URLs, Spring's resource mappings, and the service worker when changing any release-prefixed asset. Those URLs carry a one-year immutable cache policy. HTML and the service worker script are revalidated. The offline shell saves the public landing and sign-in pages, the admin and member dashboard shells, and versioned static files.

The optional `window.BachatGatCachePolicy` object can override `ttlMs` and `maxAgeMs` for `adminDashboard`, `userDashboard`, and `groupDropdown` when set before `api.js` loads. The respective defaults are 30 seconds / 5 minutes, 45 seconds / 5 minutes, and 5 minutes / 10 minutes. Dashboard snapshots are for display. Payments use a fresh server check and a server-confirmed result.

## Verify

```powershell
$serviceUrl = gcloud run services describe bachatgat-service `
  --project=bachat-gat-development `
  --region=asia-south1 `
  --format="value(status.url)"

Invoke-RestMethod "$serviceUrl/actuator/health"
```

Verify a new browser session can sign in and access its own group after a cold start. Test the landing page, both dashboards, member registration, and the Collections pending view. Confirm versioned static files return `immutable` and authenticated API responses return `no-store`. Check cold and warm timings, dashboard request counts, Cloud Run startup and pending latency, and Firestore document reads before promoting the revision. Roll back by routing traffic to the prior Cloud Run revision if those checks fail.

Before promoting a revision, verify that both exact Cloud Run HTTPS origins in `cloudbuild.yaml` are in `CORS_ALLOWED_ORIGINS` and that both hostnames are authorized in Firebase Authentication for phone verification. If a custom domain is introduced, add its HTTPS origin to Cloud Run and its hostname to Firebase Authentication.

Confirm that health reports `UP` and that the password login endpoint accepts requests from both approved origins. A request with an empty JSON body should reach validation and return 400. A 403 with `Invalid CORS request` indicates an origin mismatch. Check that signed-out requests to protected endpoints are denied and test sign-in and group-scoped access with an authorized account.

Username and password sign-in uses the application API. Firebase handles phone verification, and Brevo handles email OTP and reports. Confirm that the Brevo sender is verified and that the Cloud Run service has its SMTP credentials from Secret Manager. Test OTP delivery to a controlled email inbox before promotion.

## Emergency administrator recovery

`ADMIN_PASSWORD_RESET_TOKEN` must be added only for a controlled password reset, then removed immediately. Never place the token or password in source control, screenshots, shell history, or chat.
