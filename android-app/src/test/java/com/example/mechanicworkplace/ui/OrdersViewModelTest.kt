package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.data.remote.onecmobile.OrderStateFilter
import com.example.mechanicworkplace.data.remote.onecmobile.StateCatalogItemDto
import com.example.mechanicworkplace.repository.OrdersPageResult
import com.example.mechanicworkplace.testutil.FakeOrdersRepository
import com.example.mechanicworkplace.testutil.testOrderItem
import com.example.mechanicworkplace.testutil.testPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load exposes page and state names`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            statesResult = Result.success(listOf(StateCatalogItemDto("state-1", "В работе", 10)))
            ordersResults.add(Result.success(OrdersPageResult(testPage(listOf(testOrderItem()), total = 2), false)))
        }

        val viewModel = OrdersViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.items.size)
        assertEquals(2, state.total)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
        assertEquals("В работе", state.stateNames["state-1"])
        assertEquals(OrderStateFilter.ALL, repository.ordersCalls.single().filter)
        assertEquals(0, repository.ordersCalls.single().offset)
        assertEquals(50, repository.ordersCalls.single().limit)
    }

    @Test
    fun `loadMore appends next page and keeps offset`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            statesResult = Result.success(emptyList())
            ordersResults.add(Result.success(OrdersPageResult(testPage(listOf(testOrderItem()), total = 2), false)))
            ordersResults.add(
                Result.success(
                    OrdersPageResult(testPage(listOf(testOrderItem(id = "order-2", number = "2")), total = 2), false)
                )
            )
        }

        val viewModel = OrdersViewModel(repository)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.items.size)
        assertEquals(1, repository.ordersCalls.last().offset)
        assertFalse(state.hasMore)
    }

    @Test
    fun `filter change resets offset and applies filter`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            statesResult = Result.success(emptyList())
            ordersResults.add(Result.success(OrdersPageResult(testPage(listOf(testOrderItem()), total = 1), false)))
            ordersResults.add(Result.success(OrdersPageResult(testPage(emptyList(), total = 0), false)))
        }

        val viewModel = OrdersViewModel(repository)
        advanceUntilIdle()
        viewModel.selectFilter(OrderStateFilter.CURRENT)
        advanceUntilIdle()

        val lastCall = repository.ordersCalls.last()
        assertEquals(OrderStateFilter.CURRENT, lastCall.filter)
        assertEquals(0, lastCall.offset)
        assertEquals(0, viewModel.state.value.items.size)
    }

    @Test
    fun `network failure without cache exposes error`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            statesResult = Result.success(emptyList())
            ordersResults.add(Result.failure(OneCMobileException.Network("Нет сети")))
        }

        val viewModel = OrdersViewModel(repository)
        advanceUntilIdle()

        assertEquals("Нет сети", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(0, viewModel.state.value.items.size)
    }

    @Test
    fun `cached page is marked as offline`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            statesResult = Result.success(emptyList())
            ordersResults.add(
                Result.success(
                    OrdersPageResult(testPage(listOf(testOrderItem()), total = 1), fromCache = true)
                )
            )
        }

        val viewModel = OrdersViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.fromCache)
    }

    @Test
    fun `search is debounced and sends the query`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            statesResult = Result.success(emptyList())
            ordersResults.add(Result.success(OrdersPageResult(testPage(listOf(testOrderItem()), total = 1), false)))
            ordersResults.add(Result.success(OrdersPageResult(testPage(emptyList(), total = 0), false)))
        }

        val viewModel = OrdersViewModel(repository)
        advanceUntilIdle()
        val callsBeforeSearch = repository.ordersCalls.size

        viewModel.updateSearch("ABC 123")
        advanceTimeBy(349)
        assertEquals(callsBeforeSearch, repository.ordersCalls.size)

        advanceUntilIdle()
        assertEquals("ABC 123", repository.ordersCalls.last().search)
    }
}
