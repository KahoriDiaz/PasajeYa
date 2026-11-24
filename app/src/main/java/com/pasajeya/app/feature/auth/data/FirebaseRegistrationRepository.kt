package com.pasajeya.app.feature.auth.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import com.pasajeya.app.feature.auth.domain.BenefitType
import com.pasajeya.app.feature.auth.domain.OperatorRouteChoice
import com.pasajeya.app.core.util.hashedDni
import com.pasajeya.app.feature.auth.domain.RegistrationConfig
import com.pasajeya.app.feature.auth.domain.RegistrationRepository
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.UserRole
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Escribe `users/{uid}` directamente desde el cliente, sin Cloud Functions ni sessions. */
@Singleton
class FirebaseRegistrationRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : RegistrationRepository {

    override suspend fun savePassenger(
        uid: String,
        email: String,
        fullName: String,
        dni: String,
        birthDate: String,
        age: Int,
        benefitType: BenefitType,
        deviceId: String,
        universityCardCode: String?,
        universityCardExpiry: LocalDate?
    ) {
        val isUniversity = benefitType == BenefitType.UNIVERSITY
        val universityData = mutableMapOf<String, Any>()
        if (isUniversity) {
            universityData["benefitType"] = benefitType.wireValue
            universityCardCode?.let { code ->
                if (code.isNotBlank()) {
                    universityData["universityCardCode"] = code
                }
            }
            universityData["benefitDiscount"] = 0.50
            // El beneficio queda PEDIDO, no concedido: se activa recien cuando la persona
            // verifica su correo institucional. Si no, cualquiera escribe un codigo y
            // viaja a mitad de precio.
            universityData["universityStatus"] = "PENDING"
            universityCardExpiry?.let {
                universityData["universityCardExpiryDate"] = Timestamp(
                    it.atStartOfDay(ZoneId.systemDefault()).toInstant()
                )
            }
        } else {
            universityData["benefitType"] = benefitType.wireValue
        }

        write(
            uid,
            baseData(uid, email, fullName, dni, UserRole.PASSENGER) + mapOf(
                "birthDate" to birthDate,
                "age" to age,
                "activeDeviceId" to deviceId
            ) + universityData
        )
    }

    override suspend fun saveOperator(
        uid: String,
        email: String,
        fullName: String,
        dni: String,
        birthDate: String,
        age: Int,
        plate: String,
        route: OperatorRouteChoice,
        licenseNumber: String,
        licenseCategory: String,
        deviceId: String,
        companyId: String?,
        companyRuc: String?
    ) {
        // Si su empresa ya lo dio de alta por DNI, queda vinculado solo.
        val inviteRef = firestore.collection("driverInvites").document(hashedDni(dni))
        val invite = runCatching { inviteRef.get().await() }.getOrNull()
            ?.takeIf { it.exists() && it.getString("status") == "pending" }
        val invitedCompanyId = invite?.getString("companyId")

        val companyData = when {
            companyId != null ->
                mapOf("companyId" to companyId, "companyRuc" to companyRuc.orEmpty())

            invitedCompanyId != null -> mapOf("companyId" to invitedCompanyId)

            else -> emptyMap()
        }
        write(
            uid,
            baseData(uid, email, fullName, dni, UserRole.DRIVER) + mapOf(
                "licenseCategory" to licenseCategory,
                "licenseNumber" to licenseNumber,
                // Nadie verifico el brevete todavia: solo el aval de una empresa lo habilita.
                "licenseStatus" to if (companyData.isEmpty()) {
                    RegistrationConfig.LICENSE_STATUS_PENDING
                } else {
                    RegistrationConfig.LICENSE_STATUS_ENABLED
                },
                "driverStatus" to if (companyData.isEmpty()) {
                    "unverified"
                } else {
                    "verified_by_company"
                },
                "vehiclePlate" to plate,
                "assignedRouteId" to route.id,
                "birthDate" to birthDate,
                "age" to age,
                "plate" to plate,
                "assignedRoutes" to route.routeIds,
                "activeDeviceId" to deviceId
            ) + companyData
        )

        if (invite != null) {
            runCatching {
                inviteRef.set(
                    mapOf("status" to "accepted", "driverUid" to uid),
                    SetOptions.merge()
                ).await()
            }
        }
    }

    override suspend fun saveCompany(
        uid: String,
        email: String,
        fullName: String,
        dni: String,
        birthDate: String,
        age: Int,
        businessName: String,
        ruc: String,
        deviceId: String
    ) {
        write(
            uid,
            baseData(uid, email, fullName, dni, UserRole.COMPANY) + mapOf(
                "birthDate" to birthDate,
                "age" to age,
                "businessName" to businessName,
                "ruc" to ruc,
                "activeDeviceId" to deviceId
            )
        )
        try {
            firestore.collection("companies").document(uid).set(
                mapOf(
                    "ownerUid" to uid,
                    "businessName" to businessName,
                    "ruc" to ruc,
                    "status" to RegistrationConfig.STATUS_ACTIVE,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseNetworkException) {
            throw AccountException(AccountFailure.Network)
        } catch (error: FirebaseFirestoreException) {
            throw AccountException(AccountFailure.Unknown)
        }
    }

    override suspend fun findCompanyIdByRuc(ruc: String): String? = try {
        firestore.collection("companies")
            .whereEqualTo("ruc", ruc)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.id
    } catch (error: CancellationException) {
        throw error
    } catch (error: FirebaseNetworkException) {
        throw AccountException(AccountFailure.Network)
    } catch (error: FirebaseFirestoreException) {
        throw AccountException(AccountFailure.Unknown)
    }

    private fun baseData(
        uid: String,
        email: String,
        fullName: String,
        dni: String,
        role: UserRole
    ): Map<String, Any> = mapOf(
        "uid" to uid,
        "role" to role.wireValue,
        "email" to email,
        "fullName" to fullName,
        "firstName" to fullName,
        "lastName" to "",
        "dni" to dni,
        "status" to RegistrationConfig.STATUS_ACTIVE,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private suspend fun write(uid: String, data: Map<String, Any>) {
        try {
            firestore.collection("users").document(uid).set(data).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseNetworkException) {
            throw AccountException(AccountFailure.Network)
        } catch (error: FirebaseFirestoreException) {
            throw AccountException(
                when (error.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AccountFailure.Network
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> AccountFailure.NotAuthenticated
                    else -> AccountFailure.Unknown
                }
            )
        } catch (error: Exception) {
            throw AccountException(AccountFailure.Unknown)
        }
    }
}
