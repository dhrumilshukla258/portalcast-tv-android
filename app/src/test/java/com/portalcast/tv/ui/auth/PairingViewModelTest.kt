package com.portalcast.tv.ui.auth

import com.portalcast.tv.data.net.ApiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The pairing loop against a scripted repository and virtual time. `viewModelScope`
 * runs on Dispatchers.Main, which is swapped for the test dispatcher so `delay`
 * advances with the scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `requests a code on enter and polls every 4 seconds`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = PairingViewModel(repo)

        vm.onScreenEntered()
        runCurrent()
        assertEquals(1, repo.codeRequests)
        assertTrue(vm.state.value is PairingState.Showing)
        assertEquals("device-1", (vm.state.value as PairingState.Showing).code.deviceCode)
        assertEquals("no poll before the first interval", 0, repo.polls)

        advanceTimeBy(3_999); runCurrent()
        assertEquals(0, repo.polls)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, repo.polls)

        advanceTimeBy(4_000); runCurrent()
        assertEquals(2, repo.polls)
        advanceTimeBy(40_000); runCurrent()
        assertEquals(12, repo.polls)
        assertEquals(listOf("device-1"), repo.polledCodes.distinct())
        assertTrue(vm.state.value is PairingState.Showing)
        vm.onScreenLeft() // the loop runs on viewModelScope; stop it or runTest never returns
    }

    @Test
    fun `never has more than one poll in flight, even when polls are slow`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply { pollDurationMs = 10_000 }
        val vm = PairingViewModel(repo)
        vm.onScreenEntered()
        runCurrent()

        // poll #1 starts at 4 s and returns at 14 s; the next 4 s wait starts only then.
        advanceTimeBy(4_000); runCurrent()
        assertEquals(1, repo.polls)
        advanceTimeBy(9_999); runCurrent()
        assertEquals(1, repo.polls)
        advanceTimeBy(4_001); runCurrent()
        assertEquals(2, repo.polls)
        advanceTimeBy(100_000); runCurrent()
        assertEquals(1, repo.maxPollsInFlight)
        vm.onScreenLeft() // the loop runs on viewModelScope; stop it or runTest never returns
    }

    @Test
    fun `authorized accepts tokens exactly once and stops polling`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply {
            pollResults.addAll(listOf(FakeAuthRepository.pending(), FakeAuthRepository.pending(), FakeAuthRepository.authorized()))
        }
        val vm = PairingViewModel(repo)
        vm.onScreenEntered()
        runCurrent()

        advanceTimeBy(12_000); runCurrent()
        assertEquals(3, repo.polls)
        assertEquals(1, repo.acceptCalls)
        assertEquals(PairingState.Authorized, vm.state.value)
        assertEquals("tv@example.com", repo.session.value?.user?.email)

        advanceTimeBy(60_000); runCurrent()
        assertEquals("polling must stop after authorized", 3, repo.polls)
        assertEquals(1, repo.acceptCalls)
        assertEquals(1, repo.codeRequests)
        vm.onScreenLeft() // the loop runs on viewModelScope; stop it or runTest never returns
    }

    @Test
    fun `expired stops polling and regenerate restarts the loop with a new code`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply { pollResults.add(FakeAuthRepository.expired()) }
        val vm = PairingViewModel(repo)
        vm.onScreenEntered()
        runCurrent()

        advanceTimeBy(4_000); runCurrent()
        assertEquals(1, repo.polls)
        assertEquals(PairingState.Expired, vm.state.value)
        advanceTimeBy(60_000); runCurrent()
        assertEquals("polling must stop after expired", 1, repo.polls)
        assertEquals(0, repo.acceptCalls)

        // Re-entering the screen while expired must not silently request a code.
        vm.onScreenLeft()
        vm.onScreenEntered()
        runCurrent()
        assertEquals(1, repo.codeRequests)
        assertEquals(PairingState.Expired, vm.state.value)

        vm.regenerate()
        runCurrent()
        assertEquals(2, repo.codeRequests)
        assertEquals("device-2", (vm.state.value as PairingState.Showing).code.deviceCode)
        advanceTimeBy(4_000); runCurrent()
        assertEquals(2, repo.polls)
        assertEquals("device-2", repo.polledCodes.last())
        advanceTimeBy(8_000); runCurrent()
        assertEquals(4, repo.polls)
        vm.onScreenLeft() // the loop runs on viewModelScope; stop it or runTest never returns
    }

    @Test
    fun `poll failure shows the message and stops`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply { pollResults.add(Result.failure(ApiError.Forbidden("User is disabled"))) }
        val vm = PairingViewModel(repo)
        vm.onScreenEntered()
        runCurrent()

        advanceTimeBy(4_000); runCurrent()
        assertEquals(PairingState.Failed("User is disabled"), vm.state.value)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, repo.polls)
        assertEquals(0, repo.acceptCalls)
        vm.onScreenLeft() // the loop runs on viewModelScope; stop it or runTest never returns
    }

    @Test
    fun `code request failure shows the message`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply { codeResult = Result.failure(ApiError.Network(java.io.IOException("timeout"))) }
        val vm = PairingViewModel(repo)
        vm.onScreenEntered()
        runCurrent()
        assertTrue(vm.state.value is PairingState.Failed)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, repo.polls)
        vm.onScreenLeft() // the loop runs on viewModelScope; stop it or runTest never returns
    }

    @Test
    fun `leaving the screen cancels polling and returning resumes the same code`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = PairingViewModel(repo)
        vm.onScreenEntered()
        runCurrent()
        advanceTimeBy(8_000); runCurrent()
        assertEquals(2, repo.polls)

        vm.onScreenLeft()
        advanceTimeBy(60_000); runCurrent()
        assertEquals("no polls while the screen is away", 2, repo.polls)

        vm.onScreenEntered()
        runCurrent()
        assertEquals("resume must reuse the code, not request a new one", 1, repo.codeRequests)
        advanceTimeBy(4_000); runCurrent()
        assertEquals(3, repo.polls)
        assertEquals("device-1", repo.polledCodes.last())
        vm.onScreenLeft() // the loop runs on viewModelScope; stop it or runTest never returns
    }
}
