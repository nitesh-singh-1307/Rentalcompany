package com.example.speedalert

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import java.time.LocalDateTime
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody


class MainActivity: AppCompatActivity() {
    private lateinit var speedMonitorService: SpeedMonitorService
    private lateinit var telemetryService: SimulatedTelemetryService

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Device token: $token")
                // Send this token to your backend to store and use later
            }
        }

        // Sample Data
        val rental = Rental(
            id = "rental1",
            vehicleId = "vehicle123",
            customerId = "customerABC",
            speedLimit = 60.0,
            startTime = LocalDateTime.now().minusHours(1),
            endTime = LocalDateTime.now().plusHours(2)
        )

        val rentalRepo = InMemoryRentalRepository(listOf(rental))
        val alertService = FirebaseAlertService()
        speedMonitorService = SpeedMonitorService(rentalRepo, alertService)
        telemetryService = SimulatedTelemetryService()

        // Subscribe to vehicle speed updates
        telemetryService.subscribeToSpeedUpdates("vehicle123") { speed ->
            speedMonitorService.onSpeedUpdate("vehicle123", speed)
        }

        // Simulate speed
        telemetryService.simulateSpeed("vehicle123", 55.0) // Normal
        telemetryService.simulateSpeed("vehicle123", 65.0) // Exceeds limit
    }
}

// Rental.kt
data class Rental(
    val id: String,
    val vehicleId: String,
    val customerId: String,
    val speedLimit: Double,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
)

// RentalRepository.kt
interface RentalRepository {
    fun getActiveRentalByVehicle(vehicleId: String): Rental?
}

class InMemoryRentalRepository(private val rentals: List<Rental>) : RentalRepository {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun getActiveRentalByVehicle(vehicleId: String): Rental? {
        val now = LocalDateTime.now()
        return rentals.firstOrNull {
            it.vehicleId == vehicleId && now.isAfter(it.startTime) && now.isBefore(it.endTime)
        }
    }
}

// AlertService.kt
interface AlertService {
    fun notifyCompany(rental: Rental, message: String)
    fun alertUser(rental: Rental, message: String)
}

class FirebaseAlertService : AlertService {
    private val fcmServerKey = "YOUR_FCM_SERVER_KEY" // Replace with your actual FCM server key
    private val fcmUrl = "https://fcm.googleapis.com/fcm/send"
    private val client = OkHttpClient()

    override fun notifyCompany(rental: Rental, message: String) {
        // Simulated Firebase message to company
        Log.d("FirebaseAlert", "Notify Company: Rental ${rental.id}, Msg: $message")
        // Simulated payload structure
        val payload = mapOf(
            "to" to "/topics/company-alerts",
            "notification" to mapOf(
                "title" to "Speed Alert 🚨",
                "body" to message
            ),
            "data" to mapOf(
                "vehicleId" to rental.vehicleId,
                "rentalId" to rental.id
            )
        )

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = payload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(fcmUrl)
            .addHeader("Authorization", "key=$fcmServerKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("FirebaseAlert", "Failed to send FCM message: ${response.code} - ${response.message}")
            } else {
                Log.d("FirebaseAlert", "Successfully sent FCM message")
            }
        }

    }

    override fun alertUser(rental: Rental, message: String) {
        // Simulated Firebase message to user
        Log.d("FirebaseAlert", "Alert User: ${rental.customerId}, Msg: $message")

        val payload = mapOf(
            "to" to "DEVICE_FCM_TOKEN_FOR_${rental.customerId}", // Replace with actual token
            "notification" to mapOf(
                "title" to "Speed Limit Warning ⚠️",
                "body" to message
            ),
            "data" to mapOf(
                "vehicleId" to rental.vehicleId,
                "customerId" to rental.customerId
            )
        )


        // TODO: Implement Firebase notification to user's device using token
        // FirebaseMessaging.getInstance().send(...)
    }
}

// SpeedMonitorService.kt
class SpeedMonitorService(
    private val rentalRepo: RentalRepository,
    private val alertService: AlertService
) {
    fun onSpeedUpdate(vehicleId: String, speed: Double) {
        val rental = rentalRepo.getActiveRentalByVehicle(vehicleId)
        if (rental != null && speed > rental.speedLimit) {
            val message = "Speed exceeded: $speed > ${rental.speedLimit}"
            alertService.notifyCompany(rental, message)
            alertService.alertUser(rental, message)
        }
    }
}


// TelemetryService.kt
interface TelemetryService {
    fun subscribeToSpeedUpdates(vehicleId: String, callback: (Double) -> Unit)
}

class SimulatedTelemetryService : TelemetryService {
    private val listeners = mutableMapOf<String, (Double) -> Unit>()

    override fun subscribeToSpeedUpdates(vehicleId: String, callback: (Double) -> Unit) {
        listeners[vehicleId] = callback
    }

    fun simulateSpeed(vehicleId: String, speed: Double) {
        listeners[vehicleId]?.invoke(speed)
    }
}


