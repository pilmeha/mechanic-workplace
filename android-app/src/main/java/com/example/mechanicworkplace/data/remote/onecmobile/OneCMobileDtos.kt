package com.example.mechanicworkplace.data.remote.onecmobile

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * DTOs for the 1C HTTP service contract v1.2 (`/hs/mobile/v1/`).
 *
 * Contract notes enforced here:
 * - camelCase field names, no 1C internal Russian names;
 * - amounts are [BigDecimal], never Float/Double;
 * - dates stay as original ISO strings (see [com.example.mechanicworkplace.util.DateFormats]);
 * - ids are plain strings without braces;
 * - `worksCount`, `hasPhotos`, `plate`, `mileage`, and `photos` inside the order card are
 *   intentionally absent: the server does not guarantee them.
 *   TODO(contract-clarification): the full contract example still contains worksCount and
 *   hasPhotos even though the text says they are absent; the app follows the text.
 * - `lineId` must be sent back unchanged when saving works.
 */

@Serializable
data class UserDto(
    val userId: String,
    val name: String,
    val employeeId: String,
    /** Informational only: the contract defines no permission matrix. */
    val isMaster: Boolean = false
)

@Serializable
data class ClientDto(
    val id: String,
    val name: String
)

@Serializable
data class VehicleDto(
    val id: String,
    val model: String,
    val vin: String
)

@Serializable
data class SummaryOrderDto(
    val id: String,
    val number: String
)

@Serializable
data class StateRefDto(
    val code: String,
    val name: String
)

@Serializable
data class StateCatalogItemDto(
    val id: String,
    val name: String,
    val order: Int? = null
)

@Serializable
data class OrderListItemDto(
    val id: String,
    val number: String,
    val date: String? = null,
    val state: StateRefDto,
    val client: ClientDto? = null,
    val vehicle: VehicleDto? = null,
    val plannedDate: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val sumWorks: BigDecimal? = null,
    val posted: Boolean = false
)

@Serializable
data class WorkRefDto(
    val id: String,
    val name: String
)

@Serializable
data class WorkExecutorDto(
    val employeeId: String,
    val name: String? = null,
    val shopId: String? = null,
    val shopName: String? = null,
    val percent: Int? = null
)

@Serializable
data class WorkDto(
    val lineId: String? = null,
    val work: WorkRefDto,
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val normHour: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val price: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val sum: BigDecimal? = null,
    val vatRate: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val sumVat: BigDecimal? = null,
    val note: String? = null,
    val executors: List<WorkExecutorDto> = emptyList()
)

@Serializable
data class DefectDto(
    val reasonId: String,
    val reason: String? = null,
    val content: String? = null,
    val type: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val normHour: BigDecimal? = null
)

@Serializable
data class DefectCatalogItemDto(
    val id: String,
    val name: String,
    val code: String? = null,
    val type: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val normHour: BigDecimal? = null
)

@Serializable
data class OrderCardDto(
    val id: String,
    val number: String,
    val date: String? = null,
    val state: StateRefDto,
    val posted: Boolean = false,
    val client: ClientDto? = null,
    val summaryOrder: SummaryOrderDto? = null,
    val vehicles: List<VehicleDto> = emptyList(),
    val description: String? = null,
    val works: List<WorkDto> = emptyList(),
    val defects: List<DefectDto> = emptyList()
)

@Serializable
data class PhotoMetaDto(
    val id: String,
    val fileName: String,
    val size: Long = 0,
    val uploadedBy: String? = null,
    val uploadedAt: String? = null,
    val canDelete: Boolean = false
)

@Serializable
data class PageDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0
)

/** `/states`, `/defects`, `/orders/{id}/photos` return `items` + `total` without pagination. */
@Serializable
data class ItemsDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0
)

@Serializable
data class LoginResultDto(
    val user: UserDto
)

@Serializable
data class PostResultDto(
    val posted: Boolean,
    val state: StateRefDto
)

@Serializable
data class UploadPhotoResultDto(
    val photo: PhotoMetaDto
)

@Serializable
data class OkResultDto(
    val ok: Boolean = true
)

// ---------- write requests ----------

@Serializable
data class SaveWorksRequest(
    val works: List<SaveWorkRowDto>
)

/**
 * One row of the full replacement of `Автоработы`.
 *
 * [lineId] is sent only for existing rows and must be preserved verbatim; it is omitted for
 * new rows. `workId` is mandatory. `shopId` is never sent: the server takes the shop from
 * the employee card.
 *
 * TODO(workId catalog): the contract has no `GET /works` catalog, so the UI must not offer
 * "add work" yet. Do not hardcode work UUIDs; wait for the catalog endpoint or an explicit
 * decision from the lead.
 */
@Serializable
data class SaveWorkRowDto(
    val lineId: String? = null,
    val workId: String,
    @Serializable(with = BigDecimalSerializer::class) val quantity: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val price: BigDecimal? = null,
    val note: String? = null,
    val executors: List<SaveExecutorDto>? = null
)

/**
 * Executor row of a work line.
 *
 * TODO(employee catalog): the contract has no employee directory, so existing executors are
 * returned unchanged; adding a new executor requires a catalog endpoint or an explicit
 * decision. `percent` may be omitted when there is a single executor.
 * TODO(contract-clarification): does omitting `executors` clear the server-side executor
 * table? Until confirmed, existing executors are sent back verbatim (employeeId + percent).
 */
@Serializable
data class SaveExecutorDto(
    val employeeId: String,
    val percent: Int? = null
)

@Serializable
data class AddDefectRequest(
    val reasonId: String,
    val content: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val normHour: BigDecimal? = null
)

@Serializable
data class UploadPhotoRequest(
    val fileName: String,
    val contentType: String,
    val contentBase64: String
) {
    override fun toString(): String =
        "UploadPhotoRequest(fileName=$fileName, contentType=$contentType, contentBase64=<redacted>)"
}

/**
 * Filter values of `GET /orders?state=`.
 * `null` means no state filter (all orders assigned to the current mechanic).
 */
enum class OrderStateFilter(val queryValue: String?) {
    ALL(null),
    CURRENT("current"),
    UPCOMING("upcoming"),
    CLOSED("closed")
}
