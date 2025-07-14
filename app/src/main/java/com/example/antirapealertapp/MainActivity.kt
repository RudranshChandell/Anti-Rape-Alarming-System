package com.example.antirapealertapp

import android.Manifest
import android.view.KeyEvent
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.location.LocationServices
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.InputStream
import com.google.firebase.database.FirebaseDatabase

private lateinit var database: FirebaseDatabase
private var lastLatitude = 0.0
private var lastLongitude = 0.0
private var volumePressCount = 0
private var firstPressTime: Long = 0
private val LOCATION_PERMISSION_REQUEST = 2001
private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etAadhaar: EditText
    private lateinit var btnUploadPic: Button
    private lateinit var ivProfile: ImageView
    private lateinit var btnRegister: Button

    // To store selected image URI
    private var selectedImageUri: Uri? = null

    // Request code for image pick
    private val IMAGE_PICK_CODE = 1000

    private fun sendEmergencyAlert() {
        val name = etName.text.toString()
        val email = etEmail.text.toString()
        val aadhaar = etAadhaar.text.toString()

        if (name.isEmpty() || email.isEmpty() || aadhaar.length != 12) {
            Toast.makeText(this, "Please register properly first", Toast.LENGTH_SHORT).show()
            return
        }

        val alert = mapOf(
            "name" to name,
            "email" to email,
            "aadhaar" to aadhaar,
            "latitude" to lastLatitude,
            "longitude" to lastLongitude,
            "timestamp" to System.currentTimeMillis()
        )

        val alertRef = database.getReference("alerts").push()
        alertRef.setValue(alert)
            .addOnSuccessListener {
                Toast.makeText(this, "🚨 Alert sent to Firebase!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send alert", Toast.LENGTH_SHORT).show()
            }
    }


    private fun getLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            fetchLocation()
        }
    }

    private fun fetchLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    lastLatitude = location.latitude
                    lastLongitude = location.longitude
                } else {
                    Toast.makeText(this, "Couldn't fetch location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Link UI elements
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        etAadhaar = findViewById(R.id.etAadhaar)
        btnUploadPic = findViewById(R.id.btnUploadPic)
        ivProfile = findViewById(R.id.ivProfile)
        btnRegister = findViewById(R.id.btnRegister)

        // Open gallery when upload pic button clicked
        btnUploadPic.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        // Handle Register button click
        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val aadhaar = etAadhaar.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || aadhaar.length != 12 || selectedImageUri == null) {
                Toast.makeText(this, "Please fill all fields correctly", Toast.LENGTH_SHORT).show()
            } else {
                val user = User(name, email, aadhaar, selectedImageUri.toString())
                val database = FirebaseDatabase.getInstance().reference

                val key = database.child("users").push().key ?: return@setOnClickListener
                database.child("users").child(key).setValue(user)
                    .addOnSuccessListener {
                        Toast.makeText(this, "✅ Registered & Saved to Firebase!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "❌ Failed to save: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        database = FirebaseDatabase.getInstance()
        getLocationPermission()
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)

    }

    // Handle image selection result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            val inputStream: InputStream? = contentResolver.openInputStream(selectedImageUri!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            ivProfile.setImageBitmap(bitmap)
        }
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
                if (timeDiff <= 5000) { // within 5 seconds
                    // Trigger emergency
                    Toast.makeText(this, "🚨 Emergency Alert Triggered!", Toast.LENGTH_LONG).show()
                    sendEmergencyAlert();
                }
                volumePressCount = 0 // reset
            }

            // Reset if more than 5 seconds passed
            if (currentTime - firstPressTime > 5000) {
                volumePressCount = 1
                firstPressTime = currentTime
            }

            return true
        }

        return super.onKeyDown(keyCode, event)
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

}
