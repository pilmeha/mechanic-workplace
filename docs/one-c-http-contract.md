# Контракт HTTP-сервиса 1С для этапа интеграции

Этот контракт предлагается реализовать в опубликованной информационной базе 1С отдельным HTTP-сервисом.

Базовый путь:

```text
/hs/mechanic/v1
```

## Start operation

```http
POST /operations/{operationId}/start
Content-Type: application/json
Idempotency-Key: <outbox-event-uuid>
```

```json
{
  "operationId": "OP-1-1",
  "status": "IN_PROGRESS",
  "startedAt": "2026-09-17T10:15:00Z"
}
```

## Complete operation

```http
POST /operations/{operationId}/complete
Content-Type: application/json
Idempotency-Key: <outbox-event-uuid>
```

```json
{
  "operationId": "OP-1-1",
  "status": "COMPLETED",
  "completedAt": "2026-09-17T10:30:00Z",
  "description": "Работа выполнена"
}
```

## Issue

```http
POST /issues
Content-Type: application/json
Idempotency-Key: <outbox-event-uuid>
```

```json
{
  "issueId": "uuid",
  "workOrderId": "WO-1",
  "operationId": "OP-1-1",
  "description": "Течь правого амортизатора",
  "additionalWorkRequired": true
}
```

## File

```http
POST /files
Content-Type: application/json
```

Для демонстрационного стенда допускается JSON с Base64. Для production предпочтительнее multipart/binary endpoint.

```json
{
  "attachmentId": "uuid",
  "workOrderId": "WO-1",
  "operationId": "OP-1-1",
  "fileName": "photo.jpg",
  "contentType": "image/jpeg",
  "contentBase64": "..."
}
```

## Успешный ответ

```http
200 OK
X-1C-Object-Id: <identifier>
```

```json
{
  "success": true
}
```

## Ошибка

Использовать стандартные HTTP-коды:

- `400` — неверный JSON/параметры
- `401`/`403` — проблема авторизации
- `404` — объект 1С не найден
- `409` — конфликт состояния
- `500` — внутренняя ошибка 1С

Важно: это **наш согласованный интеграционный контракт**, а не утверждение о фактическом API Альфа-Авто 6. Когда будет доступна конфигурация, адаптер 1С сопоставит этот контракт с реальными объектами базы.

## Идемпотентность

1С должна хранить/распознавать `Idempotency-Key` минимум для операций записи. Повторение того же ключа должно вернуть тот же результат без повторного создания/изменения бизнес-объекта. Это защищает от ситуации, когда Backend получил ответ 1С, но упал до фиксации статуса outbox-события.
