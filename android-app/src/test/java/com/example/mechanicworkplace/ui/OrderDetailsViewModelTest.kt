package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.repository.OrderDetailsResult
import com.example.mechanicworkplace.testutil.FakeOrdersRepository
import com.example.mechanicworkplace.testutil.testDefect
import com.example.mechanicworkplace.testutil.testOrderCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailsViewModelTest {

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
    fun `load exposes card and posted flag`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(
                OrderDetailsResult(testOrderCard(id = "order-1", posted = true), false)
            )
        }

        val viewModel = OrderDetailsViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.card?.posted == true)
        assertEquals("line-1", state.card?.works?.single()?.lineId)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `cached details are marked as offline`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(
                OrderDetailsResult(testOrderCard(), fromCache = true)
            )
        }

        val viewModel = OrderDetailsViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.fromCache)
    }

    @Test
    fun `not found error is mapped for the UI`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["missing"] = Result.failure(
                OneCMobileException.NotFound("Заказ-наряд не найден")
            )
        }

        val viewModel = OrderDetailsViewModel(repository)
        viewModel.load("missing")
        advanceUntilIdle()

        assertEquals("Заказ-наряд не найден", viewModel.state.value.error)
        assertNull(viewModel.state.value.card)
    }

    @Test
    fun `defect catalog opens loads and selects`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(OrderDetailsResult(testOrderCard(), false))
            defectsResults[""] = Result.success(listOf(testDefect()))
        }

        val viewModel = OrderDetailsViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        viewModel.showDefects()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDefectsDialog)
        assertEquals(1, viewModel.state.value.defectItems.size)

        viewModel.selectDefect(testDefect())
        assertFalse(viewModel.state.value.showDefectsDialog)
        assertEquals("Износ амортизатора", viewModel.state.value.selectedDefect?.name)
    }

    @Test
    fun `refresh failure keeps existing card and shows error`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(OrderDetailsResult(testOrderCard(), false))
        }
        val viewModel = OrderDetailsViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        repository.detailsResults["order-1"] = Result.failure(
            OneCMobileException.Network("Нет сети")
        )
        viewModel.refresh("order-1")
        advanceUntilIdle()

        assertEquals("Нет сети", viewModel.state.value.error)
        assertTrue(viewModel.state.value.card != null)
    }
}
