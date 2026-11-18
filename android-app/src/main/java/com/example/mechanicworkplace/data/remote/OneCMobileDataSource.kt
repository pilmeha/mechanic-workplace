package com.example.mechanicworkplace.data.remote

import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileApi
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

/** Default [OrdersDataSource]: thin delegation to the 1C HTTP client. */
class OneCMobileDataSource(
    private val api: OneCMobileApi
) : OrdersDataSource {
    override suspend fun getOrders(
        filter: OrderStateFilter,
        dateFrom: String?,
        dateTo: String?,
        search: String?,
        limit: Int,
        offset: Int
    ): PageDto<OrderListItemDto> = api.getOrders(filter, dateFrom, dateTo, search, limit, offset)

    override suspend fun getOrder(id: String): OrderCardDto = api.getOrder(id)

    override suspend fun getStates(): List<StateCatalogItemDto> = api.getStates()

    override suspend fun getDefects(search: String?): List<DefectCatalogItemDto> =
        api.getDefects(search)

    override suspend fun getPhotos(orderId: String): List<PhotoMetaDto> = api.getPhotos(orderId)

    override suspend fun getPhotoBinary(orderId: String, photoId: String): ByteArray =
        api.getPhotoBinary(orderId, photoId)

    override suspend fun deletePhoto(orderId: String, photoId: String) =
        api.deletePhoto(orderId, photoId)

    override suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto =
        api.saveWorks(orderId, request)

    override suspend fun postOrder(orderId: String): PostResultDto = api.postOrder(orderId)

    override suspend fun addDefect(orderId: String, request: AddDefectRequest): OrderCardDto =
        api.addDefect(orderId, request)

    override suspend fun uploadPhoto(orderId: String, request: UploadPhotoRequest): PhotoMetaDto =
        api.uploadPhoto(orderId, request)
}
