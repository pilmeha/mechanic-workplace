package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.repository.DefectAddResult
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailsWriteTest {

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
    fun `post updates card and closes the flow`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            postOrderResult = Result.success(testOrderCard(posted = true))
        }
        val viewModel = OrderDetailsViewModel(repository)

        viewModel.postOrder("order-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.card?.posted == true)
        assertFalse(viewModel.state.value.isPosting)
        assertEquals(1, viewModel.state.value.postVersion)
        assertEquals(1, repository.postOrderCalls)
    }

    @Test
    fun `post maps non-recoverable 400`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            postOrderResult = Result.failure(
                OneCMobileException.Validation("Не заполнен склад")
            )
        }
        val viewModel = OrderDetailsViewModel(repository)

        viewModel.postOrder("order-1")
        advanceUntilIdle()

        assertEquals("Не заполнен склад", viewModel.state.value.actionError)
        assertFalse(viewModel.state.value.isPosting)
    }

    @Test
    fun `post maps 409`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            postOrderResult = Result.failure(OneCMobileException.Conflict("Конфликт"))
        }
        val viewModel = OrderDetailsViewModel(repository)

        viewModel.postOrder("order-1")
        advanceUntilIdle()

        assertEquals("Конфликт", viewModel.state.value.actionError)
    }

    @Test
    fun `addDefect maps 403 and 409`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            addDefectResult = Result.failure(OneCMobileException.Forbidden("Нет доступа"))
        }
        val viewModel = OrderDetailsViewModel(repository)
        viewModel.selectDefect(testDefect())

        viewModel.addDefect("order-1", "reason-1", null, null)
        advanceUntilIdle()
        assertEquals("Нет доступа", viewModel.state.value.actionError)

        repository.addDefectResult = Result.failure(
            OneCMobileException.Conflict("Заказ-наряд проведён")
        )
        viewModel.addDefect("order-1", "reason-1", null, null)
        advanceUntilIdle()
        assertEquals("Заказ-наряд проведён", viewModel.state.value.actionError)
    }

    @Test
    fun `addDefect sends reasonId content and normHour`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            addDefectResult = Result.success(DefectAddResult(testOrderCard(), false))
        }
        val viewModel = OrderDetailsViewModel(repository)
        viewModel.selectDefect(testDefect())

        viewModel.addDefect(
            orderId = "order-1",
            reasonId = "reason-1",
            content = "Течь масла",
            normHour = BigDecimal("0.5")
        )
        advanceUntilIdle()

        val request = repository.addDefectCalls.single().second
        assertEquals("reason-1", request.reasonId)
        assertEquals("Течь масла", request.content)
        assertEquals("0.5", request.normHour?.toPlainString())
        assertNull(viewModel.state.value.selectedDefect)
        assertFalse(viewModel.state.value.needsConfirmation)
    }

    @Test
    fun `ambiguous defect result requires confirmation`() = runTest(dispatcher) {
        val repository = FakeOrdersRepository().apply {
            addDefectResult = Result.success(DefectAddResult(null, true))
        }
        val viewModel = OrderDetailsViewModel(repository)
        viewModel.selectDefect(testDefect())

        viewModel.addDefect("order-1", "reason-1", null, null)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.needsConfirmation)
        assertNotNull(viewModel.state.value.selectedDefect)
        assertNotNull(viewModel.state.value.actionError)
    }
}
