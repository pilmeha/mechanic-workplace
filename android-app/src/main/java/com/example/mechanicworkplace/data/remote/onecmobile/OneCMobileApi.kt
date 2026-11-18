package com.example.mechanicworkplace.data.remote.onecmobile

/**
 * Network surface of the 1C mobile HTTP service (`/hs/mobile/v1/`).
 *
 * Implementations must send `Authorization: Basic ...` on every call, including the
 * binary photo download, and must never use JWT or Idempotency-Key.
 */
interface OneCMobileApi {
    /** `POST /auth/login` — no body, Basic header only. */
    suspend fun login(): UserDto

    /** `POST /auth/logout` — no-op on the server; local cleanup is done by AuthRepository. */
    suspend fun logout()

    suspend fun getOrders(
        filter: OrderStateFilter = OrderStateFilter.ALL,
        dateFrom: String? = null,
        dateTo: String? = null,
        search: String? = null,
        limit: Int = PageParams.DEFAULT_LIMIT,
        offset: Int = 0
    ): PageDto<OrderListItemDto>

    suspend fun getOrder(id: String): OrderCardDto

    suspend fun getStates(): List<StateCatalogItemDto>

    suspend fun getDefects(search: String? = null): List<DefectCatalogItemDto>

    suspend fun getPhotos(orderId: String): List<PhotoMetaDto>

    /** `GET /orders/{id}/photos/{photoId}` — raw image bytes, not an envelope. */
    suspend fun getPhotoBinary(orderId: String, photoId: String): ByteArray

    suspend fun deletePhoto(orderId: String, photoId: String)

    suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto

    suspend fun postOrder(orderId: String): PostResultDto

    suspend fun addDefect(orderId: String, request: AddDefectRequest): OrderCardDto

    suspend fun uploadPhoto(orderId: String, request: UploadPhotoRequest): PhotoMetaDto
}
