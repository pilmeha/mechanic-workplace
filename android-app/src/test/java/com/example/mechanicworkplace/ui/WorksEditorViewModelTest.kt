package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.repository.OrderDetailsResult
import com.example.mechanicworkplace.testutil.FakeOrdersRepository
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorksEditorViewModelTest {

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
    fun `save preserves lineId workId executors and does not add shopId`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(OrderDetailsResult(testOrderCard(), false))
            saveWorksResult = Result.success(testOrderCard())
        }
        val viewModel = WorksEditorViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.rows.size)
        viewModel.updateQuantity("line-1", " 3 ")
        viewModel.updatePrice("line-1", "1600,50")
        viewModel.updateNote("line-1", "  заменено  ")
        viewModel.save()
        advanceUntilIdle()

        val request = repository.saveWorksCalls.single().second
        val row = request.works.single()
        assertEquals("line-1", row.lineId)
        assertEquals("work-1", row.workId)
        assertEquals("3", row.quantity?.toPlainString())
        assertEquals("1600.50", row.price?.toPlainString())
        assertEquals("заменено", row.note)
        assertEquals("employee-1", row.executors?.single()?.employeeId)
        assertEquals(100, row.executors?.single()?.percent)
        assertEquals(1, viewModel.state.value.savedVersion)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `save maps 409 conflict`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(OrderDetailsResult(testOrderCard(), false))
            saveWorksResult = Result.failure(
                OneCMobileException.Conflict("Заказ-наряд уже проведён")
            )
        }
        val viewModel = WorksEditorViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals("Заказ-наряд уже проведён", viewModel.state.value.saveError)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `save maps 400 validation`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(OrderDetailsResult(testOrderCard(), false))
            saveWorksResult = Result.failure(
                OneCMobileException.Validation("Строка 2: не указана работа")
            )
        }
        val viewModel = WorksEditorViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals("Строка 2: не указана работа", viewModel.state.value.saveError)
    }

    @Test
    fun `posted order cannot be saved`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(
                OrderDetailsResult(testOrderCard(posted = true), false)
            )
        }
        val viewModel = WorksEditorViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isPosted)
        assertEquals(0, repository.saveWorksCalls.size)
        assertNotNull(viewModel.state.value.saveError)
    }

    @Test
    fun `row without lineId is rejected before request`() = runTest(dispatcher) {
        val card = testOrderCard().copy(
            works = testOrderCard().works.map { it.copy(lineId = null) }
        )
        val repository = FakeOrdersRepository().apply {
            detailsResults["order-1"] = Result.success(OrderDetailsResult(card, false))
        }
        val viewModel = WorksEditorViewModel(repository)
        viewModel.load("order-1")
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(0, repository.saveWorksCalls.size)
        assertNotNull(viewModel.state.value.saveError)
    }
}
