package com.chai.aiskin.features

import com.chai.aiskin.FakeAnalysisRepository
import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.features.history.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `empty state`() {
        val vm = HistoryViewModel(FakeAnalysisRepository(0))
        assertTrue(vm.state.value.isEmpty)
    }

    @Test fun `loads first page then paginates without duplicates`() {
        val repo = FakeAnalysisRepository(25)
        val vm = HistoryViewModel(repo)
        assertEquals(10, vm.state.value.items.size)
        vm.loadMore()
        vm.loadMore()
        assertEquals(25, vm.state.value.items.size)
        assertNull(vm.state.value.nextCursor)
        assertEquals(25, vm.state.value.items.map { it.id }.toSet().size)
        assertEquals(listOf(null, "id10", "id20"), repo.historyCalls)
        vm.loadMore() // no more pages → no request
        assertEquals(3, repo.historyCalls.size)
    }

    @Test fun `initial error state and retry`() {
        val repo = FakeAnalysisRepository(3).apply { failHistory = AppError.Network() }
        val vm = HistoryViewModel(repo)
        assertTrue(vm.state.value.error is AppError.Network)
        repo.failHistory = null
        vm.refresh()
        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.items.size)
    }

    @Test fun `selection allows at most two`() {
        val vm = HistoryViewModel(FakeAnalysisRepository(5))
        vm.toggleSelect("id1"); vm.toggleSelect("id2"); vm.toggleSelect("id3")
        assertEquals(setOf("id1", "id2"), vm.state.value.selected)
        assertTrue(vm.state.value.canCompare)
        vm.toggleSelect("id1")
        assertFalse(vm.state.value.canCompare)
    }

    @Test fun `delete removes item, failure rolls back`() {
        val repo = FakeAnalysisRepository(3)
        val vm = HistoryViewModel(repo)
        vm.delete("id1")
        assertEquals(listOf("id2", "id3"), vm.state.value.items.map { it.id })
        repo.failDelete = AppError.Server()
        vm.delete("id2")
        assertEquals(listOf("id2", "id3"), vm.state.value.items.map { it.id })
        assertEquals(AppError.Server().message, vm.state.value.message)
    }
}
