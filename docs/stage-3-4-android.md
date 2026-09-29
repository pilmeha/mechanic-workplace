# Stage 3–4: Android + Offline

## Stack

- Kotlin 2.3.21
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Jetpack Compose BOM 2026.08.00
- Room 2.8.5
- WorkManager 2.11.2
- DataStore 1.2.1
- Ktor Client 3.5.2
- targetSdk 36 / compileSdk 37

AGP 9 has built-in Kotlin, so the Android module intentionally does not apply `org.jetbrains.kotlin.android`.

## Android layers

```text
UI (Compose)
  ↓
ViewModel
  ↓
MechanicRepository
  ├── Ktor ApiClient      -> Backend `/api/v1`
  ├── Room                -> local cache + sync_queue
  ├── DataStore           -> JWT/session
  └── SyncScheduler       -> WorkManager
```

## Offline rules

Read operations:

1. Try backend.
2. If backend succeeds, cache result in Room.
3. If backend fails, return cached data.

Write operations:

1. Update cached work order locally immediately.
2. Add mutation to `sync_queue` with a fresh UUID `idempotencyKey`.
3. Enqueue unique WorkManager job.
4. WorkManager runs when a network connection is available.
5. Worker sends the exact same idempotency key to backend.
6. On success, queue item is deleted.
7. On temporary failure, item returns to PENDING and WorkManager retries.
8. After five failed attempts, item is marked FAILED and remains visible for diagnostics.

## Photos

Selected gallery files and camera files are copied into:

```text
/files/pending-photos/
```

The queue stores the absolute local path. The original `content://` URI therefore does not need to remain valid after the Activity is recreated.

## Local queue record

```text
id
 type
 workOrderId
 operationId
 payloadJson
 localFilePath
 fileName
 mimeType
 idempotencyKey
 status
 attempts
 lastError
 createdAt
```
