# Этап 1 — контракт проекта

## Граница систем

```text
Android → Backend API → 1C adapter → 1C:Альфа-Авто 6
```

Android никогда не обращается к внутренним объектам 1С напрямую.

## Стабильный API мобильного приложения

Базовый URL:

```text
/api/v1
```

### Публичные

- `POST /auth/login`
- `GET /health`
- `GET /ready`

### Авторизованные

- `GET /me`
- `GET /work-orders`
- `GET /work-orders/{id}`
- `POST /work-orders/{workOrderId}/operations/{operationId}/start`
- `POST /work-orders/{workOrderId}/operations/{operationId}/complete`
- `POST /work-orders/{workOrderId}/issues`
- `POST /work-orders/{workOrderId}/attachments`
- `GET /attachments/{id}/file`
- `GET /sync/status`

Полное описание находится в `backend/src/main/resources/openapi/openapi.yaml`.

## Идемпотентность

Все изменяющие запросы используют заголовок:

```http
Idempotency-Key: <UUID>
```

Мобильное приложение генерирует UUID до помещения операции в локальную очередь. Один UUID = одна бизнес-операция.

## Офлайн-семантика

Android сначала сохраняет операцию локально, затем отправляет её на Backend. Backend сохраняет изменение и outbox-событие в одной PostgreSQL-транзакции.

Это даёт:

```text
DB mutation + outbox insert = атомарно
```

После этого отдельный worker повторно доставляет событие в 1С.

## Модель сущностей

```text
User
Vehicle
WorkOrder
Operation
Issue
Attachment
```

## События интеграции

```text
OPERATION_STARTED
OPERATION_COMPLETED
ISSUE_CREATED
ATTACHMENT_UPLOADED
```

## Почему контракт не использует OData напрямую

Структура Альфа-Авто 6 нам пока неизвестна. OData в 1С автоматически предоставляет доступ к объектам конфигурации, а собственные HTTP-сервисы позволяют сформировать произвольный JSON/HTTP-контракт. Поэтому приложение привязывается к нашему стабильному API, а mapping в конкретные объекты Альфа-Авто остаётся внутри 1C adapter. Официальная документация 1С описывает оба механизма. 
