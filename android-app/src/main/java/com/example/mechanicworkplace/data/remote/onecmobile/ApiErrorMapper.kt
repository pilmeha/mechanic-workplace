package com.example.mechanicworkplace.data.remote.onecmobile

/**
 * Domain errors mapped from 1C HTTP statuses.
 *
 * `Conflict.message` keeps the original server message on purpose: the UI needs it to
 * distinguish "уже проведён" from "превышен лимит фото" (both are HTTP 409).
 */
sealed class OneCMobileException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class Unauthorized(message: String = "Требуется авторизация") : OneCMobileException(message)

    class Forbidden(message: String) : OneCMobileException(message)

    class NotFound(message: String) : OneCMobileException(message)

    class Validation(message: String) : OneCMobileException(message)

    class Conflict(message: String) : OneCMobileException(message)

    class TooLarge(message: String) : OneCMobileException(message)

    class UnsupportedMediaType(message: String) : OneCMobileException(message)

    class Server(message: String) : OneCMobileException(message)

    class Network(message: String, cause: Throwable? = null) : OneCMobileException(message, cause)

    class Unexpected(
        val status: Int,
        message: String,
        cause: Throwable? = null
    ) : OneCMobileException(message, cause)
}

/** Maps HTTP status + envelope error to a domain exception without ever parsing a 401 body. */
object ApiErrorMapper {
    fun map(status: Int, error: ApiError?): OneCMobileException {
        val serverMessage = error?.message?.takeIf { it.isNotBlank() }
        return when (status) {
            400 -> OneCMobileException.Validation(serverMessage ?: "Данные не прошли проверку")
            401 -> OneCMobileException.Unauthorized()
            403 -> OneCMobileException.Forbidden(serverMessage ?: "Нет доступа к заказ-наряду")
            404 -> OneCMobileException.NotFound(serverMessage ?: "Заказ-наряд не найден")
            409 -> OneCMobileException.Conflict(
                serverMessage ?: "Конфликт состояния заказ-наряда"
            )
            413 -> OneCMobileException.TooLarge(serverMessage ?: "Файл больше 10 МБ")
            415 -> OneCMobileException.UnsupportedMediaType(
                serverMessage ?: "Поддерживаются только JPEG и PNG"
            )
            500 -> OneCMobileException.Server(serverMessage ?: "Внутренняя ошибка 1С")
            else -> OneCMobileException.Unexpected(
                status,
                serverMessage ?: "Неожиданный ответ 1С (HTTP $status)"
            )
        }
    }
}
