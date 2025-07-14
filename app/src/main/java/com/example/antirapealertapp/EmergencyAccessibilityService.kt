package com.example.antirapealertapp

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class EmergencyAccessibilityService : AccessibilityService() {

    private var pressCount = 0
    private var firstPressTime: Long = 0

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()

            if (pressCount == 0) {
                firstPressTime = currentTime
            }

            pressCount++

            if (pressCount == 3 && currentTime - firstPressTime <= 5000) {
                // 🚨 Trigger Emergency Alert
                Toast.makeText(this, "🚨 Background Emergency Alert!", Toast.LENGTH_LONG).show()
                pressCount = 0
            } else if (currentTime - firstPressTime > 5000) {
                pressCount = 1
                firstPressTime = currentTime
            }
        }

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        TODO("Not yet implemented")
    }

    override fun onInterrupt() {}
}
