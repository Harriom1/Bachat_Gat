# Environment-variable inventory

This file intentionally records names and purpose only. It contains no secret values.

| Variable | Purpose | Local | Production |
|---|---|---|---|
| `GCP_PROJECT_ID` | Google Cloud project ID | `bachat-gat-local` | Approved production project |
| `FIRESTORE_DATABASE` | Firestore database ID | `(default)` | `(default)` unless verified otherwise |
| `FIRESTORE_EMULATOR_HOST` | Emulator host and port | `127.0.0.1:8081` | Unset |
| `FIRESTORE_EMULATOR_ENABLED` | Select emulator mode | `true` | `false` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` | `prod` |
| `APP_DEMO_DATA_ENABLED` | Isolated seed data switch | `true` only with emulator | `false` |
| `STORAGE_BUCKET` | Private document bucket | Test bucket or unset | Approved private bucket |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins | Local origin | Exact HTTPS origin |
| `JWT_SECRET` | JWT signing secret | Local-only secret | Secret Manager |
| `JWT_EXPIRATION_MS` | JWT lifetime | Local policy | Approved policy |
| `JWT_ISSUER` | JWT issuer | `bachatgat-api` | `bachatgat-api` |
| `PAYMENT_HMAC_SECRET` | Payment webhook signature secret | Test secret | Secret Manager |
| `SMTP_HOST` | Optional report-mail host | Optional | Approved SMTP |
| `SMTP_PORT` | Optional report-mail port | `587` commonly | Approved SMTP |
| `SMTP_USERNAME` | Optional report-mail username | Outside Git | Secret Manager/local secret store |
| `SMTP_PASSWORD` | Optional report-mail password | Outside Git | Secret Manager |
| `SMTP_AUTH` | SMTP authentication switch | `true` commonly | Approved policy |
| `SMTP_STARTTLS` | SMTP TLS switch | `true` commonly | Approved policy |

Never commit `.env` files, service-account JSON keys, passwords, password hashes, JWT values, payment HMAC values, SMTP credentials, or password-reset tokens. Use Secret Manager for production and an ignored local secret mechanism for development.

