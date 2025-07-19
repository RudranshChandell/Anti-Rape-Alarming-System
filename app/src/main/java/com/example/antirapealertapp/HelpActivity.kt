package com.example.antirapealertapp

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

class HelpActivity : AppCompatActivity() {

    private lateinit var btnHelp: Button

    private var volumePressCount = 0
    private var firstPressTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        btnHelp = findViewById(R.id.btnHelp)

        btnHelp.setOnClickListener {
            triggerEmergencyAlert()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }


        startListeningForNearbyAlerts() // 👈 Start listening as soon as user enters HelpActivity
    }

    private fun triggerEmergencyAlert() {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val name = prefs.getString("name", "Unknown") ?: "Unknown"
        val email = prefs.getString("email", "unknown@example.com") ?: "unknown@example.com"
        val aadhaar = prefs.getString("aadhaar", "000000000000") ?: "000000000000"

        val lat = prefs.getFloat("latitude", 0f).toDouble()
        val lng = prefs.getFloat("longitude", 0f).toDouble()

        val alertKey = FirebaseDatabase.getInstance().reference.child("alerts").push().key ?: return

        val alert = mapOf(
            "name" to name,
            "email" to email,
            "aadhaar" to aadhaar,
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis()
        )

        val databaseRef = FirebaseDatabase.getInstance().reference
        databaseRef.child("alerts").child(alertKey).setValue(alert)

        // Save location for GeoFire
        val geoFireRef = databaseRef.child("alert_locations")
        val geoFire = GeoFire(geoFireRef)
        geoFire.setLocation(alertKey, GeoLocation(lat, lng)) { _, error ->
            if (error != null) {
                Toast.makeText(this, "❌ Failed to store location", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "🚨 Emergency Alert Sent!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startListeningForNearbyAlerts() {
        val geoFireRef = FirebaseDatabase.getInstance().getReference("alert_locations")
        val geoFire = GeoFire(geoFireRef)

        val sharedPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val currentLat = sharedPrefs.getFloat("latitude", 0f).toDouble()
        val currentLng = sharedPrefs.getFloat("longitude", 0f).toDouble()

        val geoQuery = geoFire.queryAtLocation(GeoLocation(currentLat, currentLng), 0.5) // 500 meters

        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
            override fun onKeyEntered(key: String, location: GeoLocation) {
                showEmergencyNotification(key)
            }

            override fun onKeyExited(key: String) {}
            override fun onKeyMoved(key: String, location: GeoLocation) {}
            override fun onGeoQueryReady() {}
            override fun onGeoQueryError(error: DatabaseError) {
                Toast.makeText(this@HelpActivity, "❌ GeoQuery Error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showEmergencyNotification(alertKey: String) {
        val alertRef = FirebaseDatabase.getInstance().getReference("alerts").child(alertKey)

        alertRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val name = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
                val email = snapshot.child("email").getValue(String::class.java) ?: "Unknown"
                val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0

                val message = "👤 Name: $name\n📧 Email: $email\n📍Location: $lat, $lng"

                showNotification("🚨 Nearby Emergency Alert", message)
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "emergency_alerts"

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

        // For Android 8+ we must create a channel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Emergency Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val currentTime = System.currentTimeMillis()

            if (volumePressCount == 0) {
                firstPressTime = currentTime
            }

            volumePressCount++

            if (volumePressCount == 5) {
                val timeDiff = currentTime - firstPressTime
                if (timeDiff <= 5000) {
                    triggerEmergencyAlert()
                }
                volumePressCount = 0
            }

            if (currentTime - firstPressTime > 5000) {
                volumePressCount = 1
                firstPressTime = currentTime
            }

            return true
        }

        return super.onKeyDown(keyCode, event)
    }
}
