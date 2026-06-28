package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.CombatReport
import com.liquidcode7.hearthcraft.data.db.dao.CombatReportDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CombatRepository @Inject constructor(private val dao: CombatReportDao) {
    suspend fun save(report: CombatReport) = dao.upsert(report)
    suspend fun get(bandId: String): CombatReport? = dao.get(bandId)
    fun observe(bandId: String): Flow<CombatReport?> = dao.observe(bandId)
    suspend fun clear(bandId: String) = dao.clear(bandId)
}
