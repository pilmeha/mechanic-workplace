# Этап 2 — Backend

## Стек

- Kotlin/JVM 21
- Ktor 3.5.2
- PostgreSQL
- HikariCP
- JWT
- kotlinx.serialization
- Docker

## Режимы 1С

```text
ONEC_MODE=DEMO
```

В DEMO backend не требует реальной 1С и считает доставку событий успешной.

Для следующего этапа:

```text
ONEC_MODE=HTTP
ONEC_BASE_URL=https://host.example/alpha/hs/mechanic/v1
ONEC_USERNAME=...
ONEC_PASSWORD=...
```

## Локальный запуск через Docker

```bash
docker compose up --build
```

После запуска:

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/docs`
- Health: `http://localhost:8080/health`

## Demo account

```text
login: mechanic01
password: demo12345
```

Вторая учетная запись для проверки изоляции заказ-нарядов:

```text
login: mechanic02
password: demo12345
```

## Основной сценарий

1. `POST /api/v1/auth/login`
2. `GET /api/v1/work-orders`
3. `GET /api/v1/work-orders/{id}`
4. `POST /api/v1/work-orders/{id}/operations/{operationId}/start`
5. `POST /api/v1/work-orders/{id}/operations/{operationId}/complete`
6. `POST /api/v1/work-orders/{id}/issues`
7. `POST /api/v1/work-orders/{id}/attachments`
8. `GET /api/v1/sync/status`

Все mutating-запросы должны получать собственный `Idempotency-Key`.
