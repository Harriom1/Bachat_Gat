# BachatGat Project Guidelines

These guidelines apply to future changes in this project.

## Deployment target

- The application is intended for deployment on Google Cloud Platform (GCP).
- Prefer GCP-compatible configuration, services, logging, and deployment patterns.
- Do not commit credentials, service-account keys, API keys, passwords, tokens, or personal data.

## Data protection

This application handles members' personal and financial information. Future changes must preserve:

- Authentication and role-based authorization.
- Group-level data isolation and least-privilege access.
- Server-side validation and authorization; client-side checks are not sufficient.
- Safe output encoding to prevent XSS and injection attacks.
- Secure handling of uploads, exports, logs, and error messages.
- HTTPS in production and secure cookie/session settings.
- Auditability without exposing unnecessary personal information.

## Change discipline

- Keep UI changes narrowly scoped and do not alter financial calculations or security behavior unless explicitly requested.
- Validate changes with the relevant build/tests and `git diff --check`.
- Every completed change must include an exact redo/undo command. Prefer a command scoped to the files changed in that request.
