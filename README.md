# Mobile Mechanic Workplace

Этапы 1–2: стабильный API-контракт + запускаемый Kotlin/Ktor backend.

## Архитектура

```text
Android Kotlin
    |
    | HTTPS / JSON
    v
Ktor Backend
    |
    +--> PostgreSQL
    +--> File storage
    +--> Outbox worker
    |
    | HTTPS / JSON
    v
1C:Альфа-Авто 6 adapter
```

1С пока неизвестна, поэтому backend запускается в `ONEC_MODE=DEMO`. Это позволяет уже сейчас разрабатывать Android и тестировать весь lifecycle заказа.

## Быстрый старт

Требуется Docker Desktop.

```bash
docker compose up --build
```

Открыть:

- `http://localhost:8080/docs` — Swagger UI
- `http://localhost:8080/health` — health check

Demo login:

```text
mechanic01 / demo12345
mechanic02 / demo12345
```

## Документация

- `docs/stage-1-contract.md`
- `docs/stage-2-backend.md`
- `docs/one-c-http-contract.md`
- `backend/src/main/resources/openapi/openapi.yaml`

## Stage 3–4 Android

The Android client is in `android-app/`.

It contains:

- Jetpack Compose UI
- Ktor Client
- JWT session in DataStore
- Room cache
- offline sync queue
- WorkManager retry worker
- camera and gallery photo attachment

See `docs/run-local.md` for the complete local startup and verification procedure.
