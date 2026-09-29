# Android ↔ Backend flow

## Login

```text
Compose Login
   ↓
AuthViewModel
   ↓
MechanicRepository.login()
   ↓
Ktor ApiClient
   ↓
POST /api/v1/auth/login
   ↓
JWT
   ↓
DataStore
```

## Reading

```text
HomeViewModel
   ↓
Repository.loadHome()
   ├── backend OK → Room cache + UI
   └── backend fail → Room cache + UI
```

## Offline mutation

```text
Compose action
   ↓
Repository
   ├── update Room immediately
   └── insert sync_queue
          ↓
     WorkManager
          ↓ (NetworkType.CONNECTED)
     SyncWorker
          ↓
     Ktor ApiClient
          ↓ Idempotency-Key
     Backend
          ↓
     Outbox → 1C
```
