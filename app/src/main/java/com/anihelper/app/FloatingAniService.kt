package com.anihelper.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import kotlin.random.Random

class FloatingAniService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var yukiView: ImageView
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())

    private var userDragging = false
    private var autoMoving = false

    override fun onCreate() {
        super.onCreate()

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        yukiView = ImageView(this).apply {
            setImageResource(R.drawable.yuki)

            // Почти непрозрачная
            alpha = 0.97f

            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Размер Юки
        val size = dpToPx(180)

        params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 80
        params.y = 350

        windowManager.addView(yukiView, params)

        enableManualDragging()

        // Запускаем самостоятельное поведение Юки
        scheduleNextWalk()
    }

    private fun enableManualDragging() {

        var startX = 0
        var startY = 0

        var touchX = 0f
        var touchY = 0f

        yukiView.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    userDragging = true

                    startX = params.x
                    startY = params.y

                    touchX = event.rawX
                    touchY = event.rawY

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    params.x =
                        startX + (event.rawX - touchX).toInt()

                    params.y =
                        startY + (event.rawY - touchY).toInt()

                    keepInsideScreen()

                    windowManager.updateViewLayout(
                        yukiView,
                        params
                    )

                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    userDragging = false

                    true
                }

                else -> false
            }
        }
    }

    // Через случайное время Юки решает немного пройтись
    private fun scheduleNextWalk() {

        val delay =
            Random.nextLong(10_000L, 30_000L)

        handler.postDelayed({

            if (!userDragging && !autoMoving) {
                startRandomWalk()
            } else {
                scheduleNextWalk()
            }

        }, delay)
    }

    private fun startRandomWalk() {

        autoMoving = true

        // Куда идти
        val direction =
            if (Random.nextBoolean()) 1 else -1

        // Насколько далеко пройти
        val distance =
            Random.nextInt(
                dpToPx(50),
                dpToPx(140)
            )

        var travelled = 0

        val step = dpToPx(2)

        val movement = object : Runnable {

            override fun run() {

                if (userDragging) {

                    autoMoving = false
                    scheduleNextWalk()

                    return
                }

                params.x += step * direction

                keepInsideScreen()

                try {
                    windowManager.updateViewLayout(
                        yukiView,
                        params
                    )
                } catch (_: Exception) {
                    return
                }

                travelled += step

                if (travelled < distance) {

                    // Скорость движения
                    handler.postDelayed(
                        this,
                        35L
                    )

                } else {

                    autoMoving = false

                    // После прогулки снова отдыхает
                    scheduleNextWalk()
                }
            }
        }

        handler.post(movement)
    }

    private fun keepInsideScreen() {

        val screenWidth =
            resources.displayMetrics.widthPixels

        val screenHeight =
            resources.displayMetrics.heightPixels

        val viewWidth =
            if (yukiView.width > 0)
                yukiView.width
            else
                dpToPx(180)

        val viewHeight =
            if (yukiView.height > 0)
                yukiView.height
            else
                dpToPx(180)

        params.x =
            params.x.coerceIn(
                0,
                (screenWidth - viewWidth)
                    .coerceAtLeast(0)
            )

        params.y =
            params.y.coerceIn(
                0,
                (screenHeight - viewHeight)
                    .coerceAtLeast(0)
            )
    }

    private fun dpToPx(dp: Int): Int {
        return (
            dp *
            resources.displayMetrics.density
        ).toInt()
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        if (::yukiView.isInitialized) {

            try {
                windowManager.removeView(yukiView)
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
