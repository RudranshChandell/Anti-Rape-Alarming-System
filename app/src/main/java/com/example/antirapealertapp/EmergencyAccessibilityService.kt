package com.example.antirapealertapp

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase

class EmergencyAccessibilityService : AccessibilityService() {

    private var volumePressCount = 0
    private var firstPressTime: Long = 0

    private fun sendEmergencyAlert() {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        val name = prefs.getString("name", "Unknown") ?: "Unknown"
        val email = prefs.getString("email", "unknown@example.com") ?: "unknown@example.com"
        val aadhaar = prefs.getString("aadhaar", "000000000000") ?: "000000000000"

        val latitude = prefs.getFloat("latitude", 0.0f).toDouble()
        val longitude = prefs.getFloat("longitude", 0.0f).toDouble()

        val alert = mapOf(
            "name" to name,
            "email" to email,
            "aadhaar" to aadhaar,
            "latitude" to latitude,
            "longitude" to longitude,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance().reference
            .child("alerts").push().setValue(alert)
            .addOnSuccessListener {
                Toast.makeText(this, "🚨 Emergency alert sent!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "❌ Failed to send alert", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Accessibility Service Enabled ✅", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional: UI tracking if needed in future
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()

            if (volumePressCount == 0) {
                firstPressTime = currentTime
            }

            volumePressCount++

            if (volumePressCount == 5) {
                val timeDiff = currentTime - firstPressTime
                if (timeDiff <= 5000) {
                    Toast.makeText(this, "🚨 Emergency Triggered from Background!", Toast.LENGTH_LONG).show()
                    sendEmergencyAlert()
                }
                volumePressCount = 0
            }

            if (currentTime - firstPressTime > 5000) {
                volumePressCount = 1
                firstPressTime = currentTime
            }

            return true
        }

        return false
    }

    override fun onInterrupt() {
        // Not used, but required
    }
}
