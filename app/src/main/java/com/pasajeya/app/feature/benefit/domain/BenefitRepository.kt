package com.pasajeya.app.feature.benefit.domain

import kotlinx.coroutines.flow.Flow

interface BenefitRepository {
    suspend fun requestBenefit(uid: String, universityName: String, studentCode: String): Result<Unit>
    fun observeBenefitStatus(uid: String): Flow<Benefit?>
}