# Local development

## Requirements

- JDK 21
- Maven 3.9+
- Google Cloud CLI
- Firestore Emulator

## Start the emulator

```powershell
gcloud emulators firestore start --host-port=127.0.0.1:8081
```

Keep the emulator terminal open. In a second terminal, from the repository root:

```powershell
$env:FIRESTORE_EMULATOR_HOST = "127.0.0.1:8081"
$env:FIRESTORE_EMULATOR_ENABLED = "true"
$env:GCP_PROJECT_ID = "bachat-gat-local"
$env:APP_DEMO_DATA_ENABLED = "true"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:JWT_SECRET = "local-only-change-me"
mvn spring-boot:run
```

Open `http://localhost:8080/login.html`.

## Verify

```powershell
mvn test
```

Local demo data is allowed only for development. Never use `APP_DEMO_DATA_ENABLED=true` against a real Firestore project.
