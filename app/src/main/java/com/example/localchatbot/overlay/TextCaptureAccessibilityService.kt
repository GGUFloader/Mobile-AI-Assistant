package com.example.localchatbot.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class TextCaptureAccessibilityService : AccessibilityService() {
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
    }

    override fun onInterrupt() {
        // Handle interruption
    }
}
