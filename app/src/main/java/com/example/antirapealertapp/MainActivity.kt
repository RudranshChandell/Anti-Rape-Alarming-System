package com.example.antirapealertapp

import android.Manifest
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
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

private lateinit var database: FirebaseDatabase
var lastLatitude = 0.0
var lastLongitude = 0.0
var volumePressCount = 0
var firstPressTime: Long = 0
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


    private fun checkAndPromptAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "⚠️ Enable Accessibility Service for emergency trigger.", Toast.LENGTH_LONG).show()
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${com.example.antirapealertapp.EmergencyAccessibilityService::class.java.name}"
        val enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            it.equals(expectedService, ignoreCase = true)
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
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->
            if (location != null) {
                lastLatitude = location.latitude
                lastLongitude = location.longitude

                val sharedPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
                sharedPrefs.edit().apply {
                    putFloat("latitude", lastLatitude.toFloat())
                    putFloat("longitude", lastLongitude.toFloat())
                    apply()
                }

                Toast.makeText(this, "📍 Location fetched: $lastLatitude, $lastLongitude", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ Enable GPS and go outdoors!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "❌ Failed to get location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val isRegistered = sharedPrefs.getBoolean("isRegistered", false)

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
                uploadImageAndSaveUser(name, email, aadhaar)
            }
        }

        database = FirebaseDatabase.getInstance()

        getLocationPermission()

        if (isRegistered) {
            checkAndPromptAccessibilityPermission() // 👈 NEW LINE
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
            finish()
        }

    }

    private fun uploadImageAndSaveUser(name: String, email: String, aadhaar: String) {
        val imageUri = selectedImageUri ?: return
        val user = User(name, email, aadhaar, imageUri.toString(), lastLatitude, lastLongitude)

        val databaseRef = FirebaseDatabase.getInstance().reference
        val key = databaseRef.child("users").push().key ?: return
        databaseRef.child("users").child(key).setValue(user)
            .addOnSuccessListener {
                val sharedPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
                sharedPrefs.edit().apply {
                    putString("name", name)
                    putString("email", email)
                    putString("aadhaar", aadhaar)
                    putString("imageUri", imageUri.toString()) // URI stored directly
                    putBoolean("isRegistered", true)
                    apply()
                }
                Toast.makeText(this, "✅ Registered Successfully!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, HelpActivity::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "❌ Failed to save user data", Toast.LENGTH_SHORT).show()
            }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            val inputStream: InputStream? = contentResolver.openInputStream(selectedImageUri!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            ivProfile.setImageBitmap(bitmap)
        }
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
