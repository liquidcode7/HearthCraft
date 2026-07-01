package com.liquidcode7.hearthcraft

import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.db.dao.PlayerStateDao
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayerRepositoryGrimoireTest {

    // Minimal in-memory fake DAO for testing grimoire methods.
    private lateinit var fakeDao: FakePlayerStateDao
    private lateinit var repo: PlayerRepository

    @Before
    fun setUp() {
        fakeDao = FakePlayerStateDao()
        repo = PlayerRepository(fakeDao)
    }

    @Test
    fun `observeFoundGrimoireIds returns empty set initially`() = runBlocking {
        fakeDao.state = PlayerState(id = 0, foundGrimoireIds = "")
        val ids = repo.observeFoundGrimoireIds().first()
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `discoverGrimoire adds id to foundGrimoireIds`() = runBlocking {
        fakeDao.state = PlayerState(id = 0)
        repo.discoverGrimoire("cooking_t2")
        val ids = repo.observeFoundGrimoireIds().first()
        assertTrue(ids.contains("cooking_t2"))
    }

    @Test
    fun `discoverGrimoire is idempotent`() = runBlocking {
        fakeDao.state = PlayerState(id = 0)
        repo.discoverGrimoire("cooking_t2")
        repo.discoverGrimoire("cooking_t2")
        assertEquals(1, repo.observeFoundGrimoireIds().first().size)
    }

    @Test
    fun `discoverGrimoires adds multiple ids`() = runBlocking {
        fakeDao.state = PlayerState(id = 0)
        repo.discoverGrimoires(listOf("draught_t2", "hoh_t1"))
        val ids = repo.observeFoundGrimoireIds().first()
        assertTrue(ids.contains("draught_t2"))
        assertTrue(ids.contains("hoh_t1"))
        assertEquals(2, ids.size)
    }

    @Test
    fun `discoverGrimoires with empty collection is a no-op`() = runBlocking {
        fakeDao.state = PlayerState(id = 0)
        repo.discoverGrimoires(emptyList())
        assertEquals(0, repo.observeFoundGrimoireIds().first().size)
    }

    @Test
    fun `getFoundGrimoireIds returns current ids as a one-shot read`() = runBlocking {
        fakeDao.state = PlayerState(id = 0, foundGrimoireIds = "cooking_t2,hoh_t1")
        val ids = repo.getFoundGrimoireIds()
        assertEquals(setOf("cooking_t2", "hoh_t1"), ids)
    }

    @Test
    fun `getDiscoveredRecipeIds returns current ids as a one-shot read`() = runBlocking {
        fakeDao.state = PlayerState(id = 0, discoveredRecipeIds = "potency_draught")
        val ids = repo.getDiscoveredRecipeIds()
        assertEquals(setOf("potency_draught"), ids)
    }
}

// Minimal fake DAO — only the methods PlayerRepository actually calls.
class FakePlayerStateDao : PlayerStateDao {
    var state: PlayerState? = null
    private val flow = MutableStateFlow<PlayerState?>(null)

    override fun observe(): Flow<PlayerState?> {
        flow.value = state
        return flow
    }

    override suspend fun get(): PlayerState? = state

    override suspend fun upsert(state: PlayerState) {
        this.state = state
        flow.value = state
    }

    // Stubs for methods not under test.
    override suspend fun addMoney(amount: Int) {}
    override suspend fun addGatheringXp(xp: Int) {}
    override suspend fun addCookingXp(xp: Int) {}
    override suspend fun spendMoney(amount: Int): Int = 0
    override suspend fun setSecondBand(bandId: String) {}
}
