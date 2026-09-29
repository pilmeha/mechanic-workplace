# Полный локальный запуск

## 1. Требования

Рекомендуемая среда:

- Windows 10/11
- Android Studio Quail 4 (2026.1.4)
- JDK 17+ для Android/AGP; JDK 21 подходит для текущего backend
- Docker Desktop
- Android SDK Platform 37 + Android SDK Build-Tools 36.0.0
- Android Emulator с API 35/36/37 или физический Android-телефон
- Gradle 9.6.1, если запускать Gradle из терминала без wrapper

AGP 9.4.0 требует Gradle 9.6.x и JDK 17+. Проект использует compileSdk 37; AGP 9.4 поддерживает API 37. Gradle 9.6.1 — патч-релиз ветки 9.6.x.

## 1.1 Gradle Wrapper

В архиве есть `gradle/wrapper/gradle-wrapper.properties`, но бинарный `gradle-wrapper.jar` не включён, потому что его корректнее генерировать на вашей машине.

Самый простой вариант в Android Studio — использовать её локальную установку Gradle. Либо один раз установите Gradle 9.6.1 и выполните:

```powershell
cd C:\path\to\mechanic-workplace
.\scripts\bootstrap-wrapper.ps1
```

После этого появятся `gradlew`/`gradlew.bat`, и все последующие команды можно выполнять через wrapper.


Откройте PowerShell в корне проекта:

```powershell
cd C:\path\to\mechanic-workplace
```

Запустите PostgreSQL + backend:

```powershell
docker compose up --build -d
```

Проверить контейнеры:

```powershell
docker compose ps
```

Проверить health:

```powershell
Invoke-WebRequest http://localhost:8080/health | Select-Object -ExpandProperty Content
Invoke-WebRequest http://localhost:8080/ready | Select-Object -ExpandProperty Content
```

Ожидается HTTP 200.

Swagger:

```text
http://localhost:8080/docs
```

## 3. Проверить backend без Android

Логин:

```powershell
$body = @{ login = "mechanic01"; password = "demo12345" } | ConvertTo-Json
$r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login -ContentType "application/json" -Body $body
$token = $r.accessToken
$token
```

Получить заказы:

```powershell
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/v1/work-orders -Headers $headers | ConvertTo-Json -Depth 10
```

Получить конкретный заказ:

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/v1/work-orders/{ID} -Headers $headers | ConvertTo-Json -Depth 20
```

`{ID}` замените реальным UUID из предыдущего ответа.

Проверить очередь backend:

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/v1/sync/status -Headers $headers | ConvertTo-Json
```

## 4. Запуск Android Studio

Откройте именно корень:

```text
mechanic-workplace/
```

Android Studio увидит два Gradle-модуля:

```text
backend
android-app
```

Для Android приложения выбирайте конфигурацию `android-app`.

Если Android Studio просит создать/обновить Gradle Wrapper, используйте Gradle 9.6.1.

## 5. SDK

В SDK Manager установите:

```text
Android SDK Platform 37
Android SDK Build-Tools 36.0.0
Android SDK Platform-Tools
Android Emulator
```

## 6. Android Emulator

Создайте Pixel с Android API 35/36/37.

Для emulator Android адрес backend на ПК:

```text
http://10.0.2.2:8080/
```

Это уже стоит в `android-app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
```

## 7. Запуск приложения

Выберите `android-app` → эмулятор → Run.

Логин:

```text
mechanic01
Демо пароль:
demo12345
```

После входа приложение запросит:

```http
GET /api/v1/work-orders
```

и покажет текущий заказ + минимум три ближайших автомобиля.

## 8. Физический телефон через USB

Самый удобный способ разработки — `adb reverse`.

Подключите телефон с USB debugging и выполните:

```powershell
adb devices
adb reverse tcp:8080 tcp:8080
```

Затем в `android-app/build.gradle.kts` временно замените:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8080/\"")
```

Пересоберите и запустите приложение.

Альтернатива — телефон и ПК в одной LAN. Тогда можно указать LAN IP ПК, например:

```text
http://192.168.1.100:8080/
```

и убедиться, что Windows Firewall разрешает входящий TCP 8080.

## 9. Проверка полного сценария

### Сценарий A: online

1. Войти.
2. Открыть текущий заказ.
3. Нажать `Начать работу`.
4. Убедиться, что статус стал `В работе`.
5. Нажать `Завершить`.
6. Ввести описание.
7. Добавить неисправность.
8. Добавить 5 фотографий.
9. В backend посмотреть `/api/v1/sync/status`.

### Сценарий B: offline

1. Один раз открыть заказ, чтобы он попал в Room.
2. Выключить интернет в эмуляторе/телефоне.
3. Открыть заказ.
4. Нажать `Начать работу` или добавить неисправность.
5. Добавить фото.
6. Убедиться, что интерфейс не теряет изменения.
7. На главном экране появится счётчик очереди.
8. Вернуть интернет.
9. WorkManager автоматически отправит очередь.
10. В `/api/v1/sync/status` должно уменьшиться число pending/processing.

## 10. Проверка идемпотентности

Вызов завершения работы должен содержать:

```http
Idempotency-Key: <UUID>
```

Один и тот же UUID можно отправить повторно. Backend вернёт сохранённый результат вместо создания второй операции.

## 11. Где смотреть результат

Android:

- экран приложения
- Logcat → фильтр `com.example.mechanicworkplace`

Backend:

```powershell
docker compose logs -f backend
```

PostgreSQL:

```powershell
docker compose exec postgres psql -U mechanic -d mechanic
```

Swagger:

```text
http://localhost:8080/docs
```

Состояние интеграционной очереди:

```text
GET /api/v1/sync/status
```

## 12. 1С пока не нужна

Сейчас backend запускается с:

```text
ONEC_MODE=DEMO
```

Поэтому Android уже можно полностью демонстрировать без реальной Альфа-Авто.

Когда будет доступна 1С:

```text
ONEC_MODE=HTTP
ONEC_BASE_URL=https://.../hs/mechanic/v1
ONEC_USERNAME=...
ONEC_PASSWORD=...
```

Нужно будет реализовать mapping реальных объектов Альфа-Авто в `HttpOneCClient`.
