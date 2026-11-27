package com.quotex.bot3d

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class FloatingBotService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var containerLayout: LinearLayout
    private lateinit var roundBubbleBtn: TextView
    private lateinit var card3D: LinearLayout
    private lateinit var tvSignal: TextView
    private lateinit var tvAccuracy: TextView
    private lateinit var tvMtg: TextView

    private var isBotOn = false
    private val serverUrl = "https://onrender.com"
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = Notification.Builder(this, "BOT_CHANNEL")
            .setContentTitle("Quotex 3D AI Bot Active")
            .setContentText("Tap floating bubble to start scanning")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        val resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val data: Intent? = intent?.getParcelableExtra("DATA")
        if (resultCode != -1 && data != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            setupScreenCapture()
        }

        setupFloatingUI()
        return START_STICKY
    }

    private fun setupScreenCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels / 2
        val height = metrics.heightPixels / 2
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "QuotexScreen", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun setupFloatingUI() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val bubbleBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EF4444"))
            setStroke(6, Color.WHITE)
        }

        roundBubbleBtn = TextView(this).apply {
            text = "OFF"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = bubbleBg
            elevation = 20f
            layoutParams = LinearLayout.LayoutParams(150, 150)
        }

        val card3DBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            colors = intArrayOf(Color.parseColor("#1E293B"), Color.parseColor("#0F172A"))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(5, Color.parseColor("#38BDF8"))
        }

        card3D = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = card3DBg
            elevation = 35f
            setPadding(35, 25, 35, 25)
            visibility = View.GONE
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(20, 0, 0, 0)
            layoutParams = params
        }

        tvSignal = TextView(this).apply {
            text = "SCANNING..."
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
        }

        tvAccuracy = TextView(this).apply {
            text = "Win Chance: --%"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        tvMtg = TextView(this).apply {
            text = "1-STEP MTG"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FACC15"))
            gravity = Gravity.CENTER
        }

        card3D.addView(tvSignal)
        card3D.addView(tvAccuracy)
        card3D.addView(tvMtg)

        containerLayout.addView(roundBubbleBtn)
        containerLayout.addView(card3D)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        windowManager.addView(containerLayout, params)

        roundBubbleBtn.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(containerLayout, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX < 15 && diffY < 15) {
                            toggleBotState()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleBotState() {
        isBotOn = !isBotOn
        val bg = roundBubbleBtn.background as GradientDrawable
        if (isBotOn) {
            bg.setColor(Color.parseColor("#10B981"))
            roundBubbleBtn.text = "ON"
            card3D.visibility = View.VISIBLE
            sendToggleToServer()
            startScreenScanningLoop()
        } else {
            bg.setColor(Color.parseColor("#EF4444"))
            roundBubbleBtn.text = "OFF"
            card3D.visibility = View.GONE
            sendToggleToServer()
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun sendToggleToServer() {
        val req = Request.Builder().url("$serverUrl/toggle").post("".toRequestBody()).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun startScreenScanningLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isBotOn) {
                    captureAndSendScreen()
                    handler.postDelayed(this, 2000)
                }
            }
        }, 1000)
    }

    private fun captureAndSendScreen() {
        val image = imageReader?.acquireLatestImage() ?: return
        val plane = image.planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteArray = stream.toByteArray()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
