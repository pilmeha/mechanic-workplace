package com.example.mechanicworkplace.repository

import com.example.mechanicworkplace.data.local.AppDatabase
import com.example.mechanicworkplace.data.local.CachedDefectEntity
import com.example.mechanicworkplace.data.local.CachedOrderDetailsEntity
import com.example.mechanicworkplace.data.local.CachedOrderEntity
import com.example.mechanicworkplace.data.local.CachedStateEntity
import com.example.mechanicworkplace.data.remote.OrdersDataSource
import com.example.mechanicworkplace.data.remote.onecmobile.AddDefectRequest
import com.example.mechanicworkplace.data.remote.onecmobile.DefectCatalogItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.DefectDto
import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileJson
import com.example.mechanicworkplace.data.remote.onecmobile.OrderCardDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderListItemDto
import com.example.mechanicworkplace.data.remote.onecmobile.OrderStateFilter
import com.example.mechanicworkplace.data.remote.onecmobile.PageDto
import com.example.mechanicworkplace.data.remote.onecmobile.PageParams
import com.example.mechanicworkplace.data.remote.onecmobile.SaveWorksRequest
import com.example.mechanicworkplace.data.remote.onecmobile.StateCatalogItemDto
import com.example.mechanicworkplace.sync.SyncQueueRepository
import com.example.mechanicworkplace.util.DateFormats
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

data class OrdersPageResult(val page: PageDto<OrderListItemDto>, val fromCache: Boolean)
data class OrderDetailsResult(val card: OrderCardDto, val fromCache: Boolean)
data class DefectAddResult(val card: OrderCardDto?, val needsConfirmation: Boolean)

interface OrdersRepository {
    suspend fun getOrders(
        filter: OrderStateFilter = OrderStateFilter.ALL,
        search: String = "",
        limit: Int = PageParams.DEFAULT_LIMIT,
        offset: Int = 0,
        forceRefresh: Boolean = false
    ): OrdersPageResult

    suspend fun getOrder(id: String, forceRefresh: Boolean = false): OrderDetailsResult
    suspend fun getStates(forceRefresh: Boolean = false): List<StateCatalogItemDto>
    suspend fun getDefects(search: String = "", forceRefresh: Boolean = false): List<DefectCatalogItemDto>

    // Writes are enqueued; the returned card is the current cached card (optimistic UI reads later).
    suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto
    suspend fun postOrder(orderId: String): OrderCardDto
    suspend fun addDefect(orderId: String, request: AddDefectRequest): DefectAddResult
}

class CachedOrdersRepository(
    private val db: AppDatabase,
    private val dataSource: OrdersDataSource,
    private val syncQueue: SyncQueueRepository,
    private val json: Json = OneCMobileJson
) : OrdersRepository {

    private val orderDao = db.orderCacheDao()
    private val catalogDao = db.catalogCacheDao()

    override suspend fun getOrders(
        filter: OrderStateFilter,
        search: String,
        limit: Int,
        offset: Int,
        forceRefresh: Boolean
    ): OrdersPageResult {
        val normalizedSearch = search.trim()
        val normalizedLimit = PageParams.normalizeLimit(limit)
        val normalizedOffset = PageParams.normalizeOffset(offset)

        if (!forceRefresh) {
            val cached = cachedPage(filter, normalizedSearch, normalizedLimit, normalizedOffset)
            if (cached.items.isNotEmpty()) return OrdersPageResult(cached, fromCache = false)
        }

        return try {
            val page = dataSource.getOrders(
                filter = filter,
                dateFrom = null,
                dateTo = null,
                search = normalizedSearch.takeIf { it.isNotEmpty() },
                limit = normalizedLimit,
                offset = normalizedOffset
            )
            orderDao.upsertOrders(page.items.map(::toEntity))
            OrdersPageResult(page, fromCache = false)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val cached = cachedPage(filter, normalizedSearch, normalizedLimit, normalizedOffset)
            if (cached.items.isNotEmpty() || cached.total > 0) {
                OrdersPageResult(cached, fromCache = true)
            } else {
                throw t
            }
        }
    }

    override suspend fun getOrder(id: String, forceRefresh: Boolean): OrderDetailsResult {
        if (!forceRefresh) {
            orderDao.getDetails(id)?.let { cached ->
                return OrderDetailsResult(
                    card = json.decodeFromString(OrderCardDto.serializer(), cached.json),
                    fromCache = false
                )
            }
        }
        return try {
            val card = dataSource.getOrder(id)
            cacheDetails(card)
            OrderDetailsResult(card, fromCache = false)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val cached = orderDao.getDetails(id) ?: throw t
            OrderDetailsResult(json.decodeFromString(OrderCardDto.serializer(), cached.json), true)
        }
    }

    override suspend fun getStates(forceRefresh: Boolean): List<StateCatalogItemDto> {
        if (!forceRefresh) {
            val cached = catalogDao.getStates()
            if (cached.isNotEmpty()) {
                return cached.map { StateCatalogItemDto(it.id, it.name, it.sortOrder) }
            }
        }
        return try {
            val states = dataSource.getStates()
            catalogDao.upsertStates(
                states.map {
                    CachedStateEntity(it.id, it.name, it.order, System.currentTimeMillis())
                }
            )
            states
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val cached = catalogDao.getStates()
            if (cached.isEmpty()) throw t
            cached.map { StateCatalogItemDto(it.id, it.name, it.sortOrder) }
        }
    }

    override suspend fun getDefects(
        search: String,
        forceRefresh: Boolean
    ): List<DefectCatalogItemDto> {
        val query = search.trim()
        if (!forceRefresh) {
            val cached = catalogDao.getDefects(query)
            if (cached.isNotEmpty()) return cached.map { it.toDto() }
        }
        return try {
            val defects = dataSource.getDefects(query.takeIf { it.isNotEmpty() })
            catalogDao.upsertDefects(
                defects.map {
                    CachedDefectEntity(
                        id = it.id,
                        name = it.name,
                        code = it.code,
                        type = it.type,
                        normHour = it.normHour?.toPlainString(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
            )
            defects
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val cached = catalogDao.getDefects(query)
            if (cached.isEmpty()) throw t
            cached.map { it.toDto() }
        }
    }

    override suspend fun saveWorks(orderId: String, request: SaveWorksRequest): OrderCardDto {
        syncQueue.enqueueSaveWorks(orderId, request)
        return getOrder(orderId).card
    }

    override suspend fun postOrder(orderId: String): OrderCardDto {
        syncQueue.enqueuePostOrder(orderId)
        return getOrder(orderId).card
    }

    override suspend fun addDefect(
        orderId: String,
        request: AddDefectRequest
    ): DefectAddResult {
        val card = getOrder(orderId).card
        val beforeCount = card.defects.count { it.matches(request) }
        syncQueue.enqueueAddDefect(orderId, request, beforeCount)
        return DefectAddResult(card, needsConfirmation = false)
    }

    private suspend fun cachedPage(
        filter: OrderStateFilter,
        search: String,
        limit: Int,
        offset: Int
    ): PageDto<OrderListItemDto> {
        val rows = if (search.isNotEmpty()) {
            orderDao.searchOrders(search)
        } else {
            orderDao.getAllOrders()
        }
        val now = LocalDateTime.now()
        val filtered = rows
            .map { json.decodeFromString(OrderListItemDto.serializer(), it.json) }
            .filter { order ->
                val closed = order.state.name in CLOSED_STATES
                when (filter) {
                    OrderStateFilter.ALL -> true
                    OrderStateFilter.CLOSED -> closed
                    OrderStateFilter.CURRENT -> !closed
                    OrderStateFilter.UPCOMING -> !closed && isUpcoming(order.plannedDate, now)
                }
            }
        val pageItems = filtered.drop(offset).take(limit)
        return PageDto(
            items = pageItems,
            total = filtered.size,
            limit = limit,
            offset = offset
        )
    }

    private fun isUpcoming(date: String?, now: LocalDateTime): Boolean {
        val parsed = DateFormats.parse(date) ?: return false
        return parsed.isAfter(now)
    }

    private fun toEntity(order: OrderListItemDto) = CachedOrderEntity(
        id = order.id,
        number = order.number,
        date = order.date,
        stateCode = order.state.code,
        stateName = order.state.name,
        clientId = order.client?.id,
        clientName = order.client?.name,
        vehicleId = order.vehicle?.id,
        vehicleModel = order.vehicle?.model,
        vehicleVin = order.vehicle?.vin,
        plannedDate = order.plannedDate,
        sumWorks = order.sumWorks?.toPlainString(),
        posted = order.posted,
        json = json.encodeToString(OrderListItemDto.serializer(), order),
        updatedAt = System.currentTimeMillis()
    )

    private suspend fun cacheDetails(card: OrderCardDto) {
        orderDao.upsertDetails(
            CachedOrderDetailsEntity(
                id = card.id,
                posted = card.posted,
                json = json.encodeToString(OrderCardDto.serializer(), card),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun CachedDefectEntity.toDto() = DefectCatalogItemDto(
        id = id,
        name = name,
        code = code,
        type = type,
        normHour = normHour?.toBigDecimalOrNull()
    )

    private fun DefectDto.matches(request: AddDefectRequest): Boolean {
        if (reasonId != request.reasonId) return false
        if (content?.trim().orEmpty() != request.content?.trim().orEmpty()) return false
        val left = normHour
        val right = request.normHour
        return if (left == null || right == null) {
            left == null && right == null
        } else {
            left.compareTo(right) == 0
        }
    }

    private companion object {
        val CLOSED_STATES = setOf("Выполнен", "Закрыт")
    }
}
