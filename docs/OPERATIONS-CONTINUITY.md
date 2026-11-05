# Bachat Gat Operations, Backup, Billing, and Recovery

This runbook preserves the existing application setup and explains what to do if the Google Cloud trial ends, if billing must move to another authorized account, or if the project must be rebuilt on another computer.

## 1. Current notification mechanism

The application already has a notification mechanism:

1. `ScheduledJobsService` runs the monthly collection generator and the daily loan due/overdue check.
2. The overdue check writes a `SystemNotification` through `NotificationService` into the Firestore `notifications` collection.
3. Authenticated users read only their group-authorized notifications through `GET /api/me/notifications` (the browser helper calls `/me/notifications`).
4. `/user/notifications.html` polls that endpoint every 60 seconds while the page is visible and refreshes when the tab becomes visible again.

This is application-level polling, not a browser push notification, WebSocket, or Firestore `onSnapshot` listener. It is a good initial-stage choice because it uses the existing authenticated API and Cloud Run setup, is easy to test, and has no extra service cost. Do not poll more frequently than necessary. A later push channel can be added if the product needs instant alerts, but it should still use the same authorization checks.

Important limitation: a notification is not an email, SMS, WhatsApp message, or mobile push notification. Email reports require the SMTP variables documented in `ENVIRONMENT-VARIABLES.md`; they are separate from in-app notifications.

## 2. Confirm the cloud project, Firestore database, bucket, and billing account

Run read-only commands in PowerShell:

```powershell
$projectId = "bachat-gat-development"
gcloud config get-value project
gcloud projects describe $projectId --format="table(projectId,projectNumber,name)"
gcloud firestore databases list --project=$projectId
gcloud firestore databases describe "(default)" --project=$projectId
gcloud storage buckets list --project=$projectId
gcloud billing projects describe $projectId
```

For this application, the previously configured production values were project `bachat-gat-development`, Firestore database `(default)`, region `asia-south1`, and private bucket `bachat-gat-development-docs-2026`. Verify them with the commands above before any migration or backup; do not rely on an old screenshot.

## 3. What happens when the free trial ends

The screenshot shows the trial ending on November 17, 2026. If the trial ends without upgrading, Google says workloads are shut down and a recovery/grace period is available. Resources and data may be permanently deleted after that grace period. Upgrade the current billing account before the end date if uninterrupted service is required. Upgrading means usage beyond credits/free quotas can become billable.

Do not create or use another account merely to evade charges. A legitimate project migration or billing-account change requires authorization and must comply with Google Cloud terms. A service account is only an identity; it does not carry free credits and does not transfer billing.

## 4. Moving to another authorized account

There are three different operations:

### A. Change the billing account on the same project

This keeps the project ID, Firestore database, bucket, Cloud Run service, and data in place. The new billing account must be active and the operator must have billing permissions on both accounts and permission to change the project billing account. In Console: **Billing → My projects → select project → Change billing**. Charges already incurred remain associated with the old billing account.

### B. Give a new service account access to the existing project

This changes runtime identity, not billing. Grant only the roles required by the application, grant Secret Manager access to the runtime identity, then deploy Cloud Run with `--service-account=SERVICE_ACCOUNT_EMAIL`. Never download or commit a private key unless there is a separately approved reason; prefer attached service identities and ADC.

### C. Create a new project under another authorized account

This is a real migration: create APIs, Firestore, a private bucket, Artifact Registry, secrets, IAM, and Cloud Run again; export/import Firestore; copy Storage objects; update `GCP_PROJECT_ID`, bucket, secrets, CORS, and deployment configuration; then test in the new project before changing users or DNS. The project ID itself cannot be changed in place.

## 5. Billing reduction controls

- Keep Cloud Run `min-instances=0` for a low-traffic application and keep `max-instances` bounded.
- Develop with the Firestore emulator; never run demo data against production.
- Keep the 60-second notification poll only on the visible notifications page.
- Avoid repeated full Firestore scans and unnecessary exports; Firestore export reads are billable.
- Use a private regional Standard bucket and lifecycle rules for temporary files.
- Delete obsolete container images only after confirming rollback images are retained.
- Run Cloud Build only for intentional releases.
- Create a Cloud Billing budget and alert thresholds at 50%, 80%, and 100%. A budget alert is not a hard spending cap; also use quotas, bounded Cloud Run scaling, and regular cost review.
- Review **Billing → Reports**, **Cost table**, and **Budgets & caps** weekly while the project is active.

## 6. Firestore backup before trial expiry or migration

Yes, back up the data before the trial ends or before any project/billing migration. The backup is especially important because the application contains member, savings, loan, repayment, transaction, and document metadata.

Create a separate private backup bucket with a location and owner you control. Then export Firestore:

```powershell
$projectId = "bachat-gat-development"
$backupBucket = "gs://REPLACE_WITH_PRIVATE_BACKUP_BUCKET"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
gcloud firestore export "$backupBucket/firestore/$stamp" --project=$projectId
```

Do not replace the placeholder until you have confirmed the bucket. Keep at least one copy outside the project that may lose billing access. A bucket in the same project is not sufficient as the only backup.

To restore into a test or replacement project:

```powershell
gcloud firestore import "gs://REPLACE_WITH_PRIVATE_BACKUP_BUCKET/firestore/EXPORT_TIMESTAMP" --project=REPLACEMENT_PROJECT_ID
```

Firestore export/import requires billing and has read/write implications. Perform a small test restore into an isolated project before relying on the backup.

## 7. Cloud Storage document backup

List objects first, then copy the private bucket to an encrypted local drive or an independently controlled backup bucket:

```powershell
$source = "gs://bachat-gat-development-docs-2026"
$destination = "D:\BachatGatBackups\documents"
gcloud storage ls --recursive $source
gcloud storage rsync --recursive $source $destination
```

Treat downloaded member and loan documents as confidential. Protect the backup drive, restrict access, and do not upload it to GitHub.

## 8. Save the complete application setup

Keep these items together:

- Git repository and the last known-good commit/tag.
- `README.md`, `PROJECT_GUIDE.html`, the three deployment guides, this runbook, and `ENVIRONMENT-VARIABLES.md`.
- Firestore export location and export timestamp.
- Cloud Storage backup location and timestamp.
- Project ID, project number, region, database ID, bucket, Artifact Registry repository, Cloud Run service name, and current service URL.
- Java, Maven, Google Cloud CLI, Docker, and emulator versions.
- The exact build/deploy command used and the last successful Cloud Run revision.

Capture tool versions without secrets:

```powershell
java -version 2>&1 | Tee-Object docs/tool-versions.txt
mvn -version 2>&1 | Tee-Object docs/tool-versions.txt -Append
gcloud version 2>&1 | Tee-Object docs/tool-versions.txt -Append
docker version 2>&1 | Tee-Object docs/tool-versions.txt -Append
```

The environment-variable names are listed in `ENVIRONMENT-VARIABLES.md`; values for passwords, JWT secrets, HMAC secrets, SMTP credentials, reset tokens, and service-account private keys must stay outside Git. Store production secret values in Secret Manager and record only their secret names.

## 9. Safe handoff checklist

1. Export Firestore and copy Cloud Storage documents.
2. Verify the export exists and perform a test restore.
3. Save the current Git commit and deployment metadata.
4. Confirm billing account and trial end date.
5. Decide whether to upgrade the current billing account, change to another authorized billing account, or migrate to a new project.
6. Recreate secrets and IAM in the destination; do not copy private keys into the repository.
7. Deploy to a non-production service or revision first.
8. Check `/actuator/health`, login, group isolation, document access, loan schedule, payments, and notification polling.
9. Only then route production traffic.

