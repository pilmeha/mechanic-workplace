package com.example.mechanicworkplace.ui

import com.example.mechanicworkplace.data.remote.onecmobile.OneCMobileException
import com.example.mechanicworkplace.testutil.FakeAuthGateway
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

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
    fun `successful login clears loading and forwards credentials once`() = runTest(dispatcher) {
        val auth = FakeAuthGateway()
        val viewModel = LoginViewModel(auth)

        viewModel.login("user", "pass")
        advanceUntilIdle()

        assertEquals(listOf("user" to "pass"), auth.loginCalls)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `401 maps to invalid credentials message`() = runTest(dispatcher) {
        val auth = FakeAuthGateway().apply {
            loginResult = Result.failure(OneCMobileException.Unauthorized())
        }
        val viewModel = LoginViewModel(auth)

        viewModel.login("user", "wrong")
        advanceUntilIdle()

        assertEquals("Неверный логин или пароль", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `blank fields are rejected without a network call`() = runTest(dispatcher) {
        val auth = FakeAuthGateway()
        val viewModel = LoginViewModel(auth)

        viewModel.login("", "")
        advanceUntilIdle()

        assertEquals(0, auth.loginCalls.size)
        assertEquals("Введите логин и пароль", viewModel.state.value.error)
    }

    @Test
    fun `network error keeps its message`() = runTest(dispatcher) {
        val auth = FakeAuthGateway().apply {
            loginResult = Result.failure(OneCMobileException.Network("Нет соединения с 1С"))
        }
        val viewModel = LoginViewModel(auth)

        viewModel.login("user", "pass")
        advanceUntilIdle()

        assertEquals("Нет соединения с 1С", viewModel.state.value.error)
    }
}
