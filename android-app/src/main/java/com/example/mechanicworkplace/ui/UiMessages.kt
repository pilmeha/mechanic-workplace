package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.data.remote.onecmobile.OrderListItemDto
import com.example.mechanicworkplace.util.DateFormats
import java.math.BigDecimal

/** User-facing messages for the 1C domain errors. No credentials or payloads involved. */
fun Throwable.toUserMessage(forLogin: Boolean = false): String = when (this) {
    is OneCMobileException.Unauthorized ->
        if (forLogin) "Неверный логин или пароль" else "Требуется повторный вход"
    is OneCMobileException.Forbidden -> message ?: "Нет доступа к заказ-наряду"
    is OneCMobileException.NotFound -> message ?: "Заказ-наряд не найден"
    is OneCMobileException.Validation -> message ?: "Данные не прошли проверку"
    is OneCMobileException.Conflict -> message ?: "Конфликт состояния заказ-наряда"
    is OneCMobileException.TooLarge -> message ?: "Файл больше 10 МБ"
    is OneCMobileException.UnsupportedMediaType -> message ?: "Поддерживаются только JPEG и PNG"
    is OneCMobileException.Server -> message ?: "Внутренняя ошибка 1С"
    is OneCMobileException.Network -> message ?: "Нет соединения с 1С"
    is OneCMobileException.Unexpected -> "Неожиданный ответ 1С (HTTP ${status})"
    else -> message ?: "Неизвестная ошибка"
}

fun OrderListItemDto.displayStateName(stateNames: Map<String, String>): String =
    state.name.ifBlank { stateNames[state.code] ?: "" }

fun String?.displayDate(): String = DateFormats.display(this)

fun BigDecimal?.displayAmount(): String = this?.toPlainString() ?: "—"
