package com.example.mechanicworkplace.data.remote

import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderCardDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderListItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderStateFilter
import com.example.mechanicworkplace.data.remote.onecmobile.PageDto
import com.example.mechanicworkplace.data.remote.onecmobile.PageParams
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import com.example.mechanicworkplace.data.remote.onecmobile.PostResultDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import com.example.mechanicworkplace.data.remote.onecmobile.StateCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.UploadPhotoRequest

/**
 * Seam between the app and the 1C mobile service.
 *
 * The default (and only wired) implementation is OneCMobileDataSource. The legacy
 * Ktor backend is intentionally not adapted: it cannot represent works/post/defects/photos.
 *
 * TODO(phase 6): writes must go through the single SyncQueue path, never direct-online +
 * fallback double path. This interface exposes the network operations that the queue worker
 * will call; it is not itself the queue.
 */
interface OrdersDataSource {
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

    suspend fun getPhotoBinary(orderId: String, photoId: String): ByteArray

    suspend fun deletePhoto(orderId: String, photoId: String)

    suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto

    suspend fun postOrder(orderId: String): PostResultDto

    suspend fun addDefect(orderId: String, request: AddDefectRequest): OrderCardDto

    suspend fun uploadPhoto(orderId: String, request: UploadPhotoRequest): PhotoMetaDto
}
