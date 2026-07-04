package com.liquidcode7.hearthcraft.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.HohSession
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.data.db.dao.HohSessionDao
import com.liquidcode7.hearthcraft.worker.HohWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ApplyResult(
    val timerMs: Long,
    val grade: Int,
    val allWoundsCleared: Boolean
)

@Singleton
class HohRepository @Inject constructor(
    private val memberDao: BandMemberStateDao,
    private val hohSessionDao: HohSessionDao,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    @ApplicationContext private val context: Context
) {

    private val WOUND_FLOOR_MS = mapOf(
        "physical"   to  4 * 3_600_000L,
        "will"       to  3 * 3_600_000L,
        "corruption" to  6 * 3_600_000L,
        "poison"     to  4 * 3_600_000L,
        "disease"    to  5 * 3_600_000L
    )

    private val TIER_REDUCTION = mapOf(
        1 to floatArrayOf(1.00f, 0.75f, 0.50f, 0.375f, 0.25f),
        2 to floatArrayOf(1.00f, 0.70f, 0.45f, 0.340f, 0.22f),
        3 to floatArrayOf(1.00f, 0.65f, 0.40f, 0.310f, 0.20f),
        4 to floatArrayOf(1.00f, 0.60f, 0.35f, 0.280f, 0.18f)
    )

    suspend fun applyPreparedItem(
        memberId: String,
        recipeId: String,
        grade: Int
    ): ApplyResult {
        val member = memberDao.get(memberId) ?: error("Member $memberId not found")
        val recipe = gameData.recipes.find { it.id == recipeId }
            ?: error("Recipe $recipeId not found")

        inventory.removePreparedFood(recipeId, grade)

        val tier = recipe.tier.coerceIn(1, 4)

        val session = hohSessionDao.get(memberId) ?: HohSession(memberId)

        val nowMs = System.currentTimeMillis()
        val elapsed = if (member.hohTimerStartMs > 0L)
            (nowMs - member.hohTimerStartMs).coerceAtLeast(0L)
        else
            session.elapsedMsAtLastTreatment

        val prevTreated = session.treatedTypes.split(",").filter { it.isNotBlank() }.toMutableSet()
        val newlyTreated = recipe.treatsWoundTypes.toSet()
        val allTreated = prevTreated + newlyTreated

        val bestGrade = maxOf(session.bestGrade, grade)
        val bestTier = maxOf(session.bestTier, tier)

        val memberWoundTypes = member.woundTypes.split(",").filter { it.isNotBlank() }
        val allWoundsCleared = memberWoundTypes.isNotEmpty() && allTreated.containsAll(memberWoundTypes)

        val newTimer = calcTimer(memberWoundTypes, allTreated.toList(), bestGrade, bestTier)
        val creditedTimer = (newTimer - elapsed).coerceAtLeast(0L)

        hohSessionDao.upsert(
            session.copy(
                treatedTypes = allTreated.joinToString(","),
                bestGrade = bestGrade,
                bestTier = bestTier,
                elapsedMsAtLastTreatment = elapsed
            )
        )

        memberDao.setHohTimer(memberId, nowMs, creditedTimer)
        memberDao.setRecoveryBuff(memberId, bestGrade, bestTier, pending = true)

        val xp = PlayerRepository.XP_HOH_APPLY +
            if (allWoundsCleared) PlayerRepository.XP_HOH_CLEAR_BONUS else 0
        player.addHohXp(xp)

        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("hoh_$memberId")
        workManager.enqueue(HohWorker.buildRequest(memberId, creditedTimer))

        return ApplyResult(timerMs = creditedTimer, grade = grade, allWoundsCleared = allWoundsCleared)
    }

    fun calcTimer(
        woundTypes: List<String>,
        treatedTypes: List<String>,
        grade: Int,
        tier: Int
    ): Long {
        val treated = treatedTypes.toSet()
        val reductions = TIER_REDUCTION[tier.coerceIn(1, 4)]!!
        val factor = reductions[grade.coerceIn(0, 4)]
        return woundTypes.sumOf { type ->
            val floor = WOUND_FLOOR_MS[type] ?: 3_600_000L
            if (type in treated) (floor * factor).toLong() else floor
        }
    }
}
