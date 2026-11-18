package com.example.mechanicworkplace.testutil

import com.example.mechanicworkplace.data.auth.AuthGateway
import com.example.mechanicworkplace.data.remote.OrdersDataSource
import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.ClientDto
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.DefectDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderCardDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderListItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderStateFilter
import com.example.mechanicworkplace.data.remote.onecmobile.PageDto
import com.example.mechanicworkplace.data.remote.onecmobile.PhotoMetaDto
import com.example.mechanicworkplace.data.remote.onecmobile.PostResultDto
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import com.example.mechanicworkplace.data.remote.onecmobile.StateCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.StateRefDto
import com.example.mechanicworkplace.data.remote.onecmobile.UploadPhotoRequest
import com.example.mechanicworkplace.data.remote.onecmobile.UserDto
import com.example.mechanicworkplace.data.remote.onecmobile.VehicleDto
import com.example.mechanicworkplace.data.remote.onecmobile.WorkDto
import com.example.mechanicworkplace.data.remote.onecmobile.WorkExecutorDto
import com.example.mechanicworkplace.data.remote.onecmobile.WorkRefDto
import com.example.mechanicworkplace.repository.DefectAddResult
import com.example.mechanicworkplace.repository.OrderDetailsResult
import com.example.mechanicworkplace.repository.OrdersPageResult
import com.example.mechanicworkplace.repository.OrdersRepository
import java.math.BigDecimal

class FakeAuthGateway : AuthGateway {
    var loginResult: Result<UserDto> = Result.success(testUser())
    var logoutCalls = 0
    var restoreResult = false
    val loginCalls = mutableListOf<Pair<String, String>>()

    override suspend fun login(login: String, password: String): UserDto {
        loginCalls += login to password
        return loginResult.getOrThrow()
    }

    override suspend fun logout() {
        logoutCalls++
    }

    override suspend fun restoreSession(): Boolean = restoreResult
}

data class OrdersCall(
    val filter: OrderStateFilter,
    val search: String,
    val limit: Int,
    val offset: Int,
    val forceRefresh: Boolean
)

class FakeOrdersRepository : OrdersRepository {
    val ordersCalls = mutableListOf<OrdersCall>()
    val ordersResults = ArrayDeque<Result<OrdersPageResult>>()

    var statesResult: Result<List<StateCatalogItemDto>> = Result.success(emptyList())
    var statesCalls = 0

    val detailsResults = mutableMapOf<String, Result<OrderDetailsResult>>()
    val defectsResults = mutableMapOf<String, Result<List<DefectCatalogItemDto>>>()

    var saveWorksResult: Result<OrderCardDto> = Result.failure(IllegalStateException("not set"))
    var postOrderResult: Result<OrderCardDto> = Result.failure(IllegalStateException("not set"))
    var addDefectResult: Result<DefectAddResult> = Result.failure(IllegalStateException("not set"))
    val saveWorksCalls = mutableListOf<Pair<String, SaveWorksRequest>>()
    val addDefectCalls = mutableListOf<Pair<String, AddDefectRequest>>()
    var postOrderCalls = 0

    override suspend fun getOrders(
        filter: OrderStateFilter,
        search: String,
        limit: Int,
        offset: Int,
        forceRefresh: Boolean
    ): OrdersPageResult {
        ordersCalls += OrdersCall(filter, search, limit, offset, forceRefresh)
        return ordersResults.removeFirstOrNull()?.getOrThrow()
            ?: error("No queued orders result")
    }

    override suspend fun getOrder(id: String, forceRefresh: Boolean): OrderDetailsResult =
        detailsResults[id]?.getOrThrow() ?: error("No details result for $id")

    override suspend fun getStates(forceRefresh: Boolean): List<StateCatalogItemDto> {
        statesCalls++
        return statesResult.getOrThrow()
    }

    override suspend fun getDefects(
        search: String,
        forceRefresh: Boolean
    ): List<DefectCatalogItemDto> =
        defectsResults[search.trim().lowercase()]?.getOrThrow() ?: emptyList()

    override suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto {
        saveWorksCalls += orderId to request
        return saveWorksResult.getOrThrow()
    }

    override suspend fun postOrder(orderId: String): OrderCardDto {
        postOrderCalls++
        return postOrderResult.getOrThrow()
    }

    override suspend fun addDefect(orderId: String, request: AddDefectRequest): DefectAddResult {
        addDefectCalls += orderId to request
        return addDefectResult.getOrThrow()
    }
}

class FakeOrdersDataSource : OrdersDataSource {
    var ordersHandler: suspend (
        OrderStateFilter,
        String?,
        String?,
        String?,
        Int,
        Int
    ) -> PageDto<OrderListItemDto> = { _, _, _, _, _, _ -> error("orders handler not set") }

    var orderHandler: suspend (String) -> OrderCardDto = { error("order handler not set") }
    var statesHandler: suspend () -> List<StateCatalogItemDto> = { emptyList() }
    var defectsHandler: suspend (String?) -> List<DefectCatalogItemDto> = { emptyList() }
    var photosHandler: suspend (String) -> List<PhotoMetaDto> = { emptyList() }
    var uploadPhotoHandler: suspend (String, UploadPhotoRequest) -> PhotoMetaDto =
        { _, _ -> error("uploadPhoto handler not set") }
    var saveWorksHandler: suspend (String, SaveWorksRequest) -> OrderCardDto =
        { _, _ -> error("saveWorks handler not set") }
    var postOrderHandler: suspend (String) -> PostResultDto =
        { error("postOrder handler not set") }
    var addDefectHandler: suspend (String, AddDefectRequest) -> OrderCardDto =
        { _, _ -> error("addDefect handler not set") }

    override suspend fun getOrders(
        filter: OrderStateFilter,
        dateFrom: String?,
        dateTo: String?,
        search: String?,
        limit: Int,
        offset: Int
    ): PageDto<OrderListItemDto> = ordersHandler(filter, dateFrom, dateTo, search, limit, offset)

    override suspend fun getOrder(id: String): OrderCardDto = orderHandler(id)
    override suspend fun getStates(): List<StateCatalogItemDto> = statesHandler()
    override suspend fun getDefects(search: String?): List<DefectCatalogItemDto> =
        defectsHandler(search)

    override suspend fun getPhotos(orderId: String): List<PhotoMetaDto> = photosHandler(orderId)
    override suspend fun getPhotoBinary(orderId: String, photoId: String): ByteArray =
        error("not used")

    override suspend fun deletePhoto(orderId: String, photoId: String) = error("not used")
    override suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto =
        saveWorksHandler(orderId, request)

    override suspend fun postOrder(orderId: String): PostResultDto = postOrderHandler(orderId)

    override suspend fun addDefect(orderId: String, request: AddDefectRequest): OrderCardDto =
        addDefectHandler(orderId, request)

    override suspend fun uploadPhoto(orderId: String, request: UploadPhotoRequest): PhotoMetaDto =
        uploadPhotoHandler(orderId, request)
}

fun testUser(): UserDto = UserDto(
    userId = "user-1",
    name = "Иванов Иван",
    employeeId = "employee-1",
    isMaster = true
)

fun testOrderItem(
    id: String = "order-1",
    number: String = "00000001234",
    stateName: String = "В работе",
    posted: Boolean = false,
    vehicle: VehicleDto? = VehicleDto("vehicle-1", "Lada Vesta", "XTA123")
): OrderListItemDto = OrderListItemDto(
    id = id,
    number = number,
    date = "2026-11-12T09:00:00",
    state = StateRefDto("state-1", stateName),
    client = ClientDto("client-1", "ООО Ромашка"),
    vehicle = vehicle,
    plannedDate = "2026-11-13T18:00:00",
    sumWorks = BigDecimal("12500.00"),
    posted = posted
)

fun testOrderCard(
    id: String = "order-1",
    posted: Boolean = false
): OrderCardDto = OrderCardDto(
    id = id,
    number = "00000001234",
    date = "2026-11-12T09:00:00",
    state = StateRefDto("state-1", "В работе"),
    posted = posted,
    client = ClientDto("client-1", "ООО Ромашка"),
    vehicles = listOf(VehicleDto("vehicle-1", "Lada Vesta", "XTA123")),
    description = "Стук в подвеске",
    works = listOf(
        WorkDto(
            lineId = "line-1",
            work = WorkRefDto("work-1", "Замена амортизатора"),
            quantity = BigDecimal("2"),
            price = BigDecimal("1500.00"),
            sum = BigDecimal("3000.00"),
            vatRate = "НДС20",
            note = "заменено",
            executors = listOf(
                WorkExecutorDto(
                    employeeId = "employee-1",
                    name = "Петров П.",
                    shopId = "shop-1",
                    shopName = "Цех №1",
                    percent = 100
                )
            )
        )
    ),
    defects = listOf(
        DefectDto(
            reasonId = "reason-1",
            reason = "Износ амортизатора",
            content = "Течь масла",
            type = "Неисправность",
            normHour = BigDecimal("0.5")
        )
    )
)

fun testPage(
    items: List<OrderListItemDto>,
    total: Int = items.size
): PageDto<OrderListItemDto> = PageDto(
    items = items,
    total = total,
    limit = 50,
    offset = 0
)

fun testDefect(id: String = "reason-1", name: String = "Износ амортизатора"): DefectCatalogItemDto =
    DefectCatalogItemDto(
        id = id,
        name = name,
        code = "000123",
        type = "Неисправность",
        normHour = BigDecimal("0.5")
    )
