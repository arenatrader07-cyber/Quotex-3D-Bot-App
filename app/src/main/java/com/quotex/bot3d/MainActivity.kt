package com.quotex.bot3d

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_REQ_CODE = 101
    private val SCREEN_CAPTURE_REQ_CODE = 102
    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
            setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
        }

        val title = TextView(this).apply {
            text = "QUOTEX 3D AI BOT"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 40)
        }

        val startBtn = Button(this).apply {
            text = "LAUNCH FLOATING 3D BOT"
            setBackgroundColor(android.graphics.Color.parseColor("#10B981"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener { checkPermissionsAndStart() }
        }

        layout.addView(title)
        layout.addView(startBtn)
        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_REQ_CODE)
        } else {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQ_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQ_CODE)
            } else {
                Toast.makeText(this, "Overlay Permission Required!", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == SCREEN_CAPTURE_REQ_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, FloatingBotService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA", data)
            }
            startForegroundService(serviceIntent)
            moveTaskToBack(true)
        }
    }
}
