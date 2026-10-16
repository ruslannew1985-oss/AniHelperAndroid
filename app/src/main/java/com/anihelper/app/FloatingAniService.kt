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
import kotlin.math.abs
import kotlin.random.Random

class FloatingAniService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var yukiView: ImageView
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())

    private var userDragging = false
    private var autoMoving = false
    private var destroyed = false

    private var walkFrame = 0

    private val walkFrames by lazy {
        intArrayOf(
            R.drawable.yuki_walk1,
            R.drawable.yuki_walk2,
            R.drawable.yuki_walk3,
            R.drawable.yuki_walk4
        )
    }

    override fun onCreate() {
        super.onCreate()

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        yukiView = ImageView(this).apply {
            setImageResource(R.drawable.yuki)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

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

        enableTouch()

        // Через небольшую паузу Юки начинает жить сама
        handler.postDelayed({
            scheduleNextAction()
        }, 1500)
    }

    private fun enableTouch() {

        var startX = 0
        var startY = 0

        var touchX = 0f
        var touchY = 0f

        var moved = false

        yukiView.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {

                    userDragging = true
                    moved = false

                    startX = params.x
                    startY = params.y

                    touchX = event.rawX
                    touchY = event.rawY

                    true
                }

                MotionEvent.ACTION_MOVE -> {

                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY

                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                    }

                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()

                    keepInsideScreen()

                    try {
                        windowManager.updateViewLayout(
                            yukiView,
                            params
                        )
                    } catch (_: Exception) {
                    }

                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    userDragging = false

                    // Если палец почти не двигался — это тап
                    if (!moved &&
                        event.action == MotionEvent.ACTION_UP
                    ) {
                        wave()
                    }

                    true
                }

                else -> false
            }
        }
    }

    // Выбор следующего поведения
    private fun scheduleNextAction() {

        if (destroyed) return

        val delay =
            Random.nextLong(2000L, 6000L)

        handler.postDelayed({

            if (destroyed) return@postDelayed

            if (userDragging || autoMoving) {
                scheduleNextAction()
                return@postDelayed
            }

            when (Random.nextInt(100)) {

                in 0..64 -> startRandomWalk()

                in 65..84 -> sit()

                else -> wave()
            }

        }, delay)
    }

    // Начало прогулки
    private fun startRandomWalk() {

        if (destroyed || userDragging) {
            scheduleNextAction()
            return
        }

        autoMoving = true

        val screenWidth =
            resources.displayMetrics.widthPixels

        val viewWidth =
            if (yukiView.width > 0)
                yukiView.width
            else
                dpToPx(180)

        val maxX =
            (screenWidth - viewWidth)
                .coerceAtLeast(0)

        val targetX =
            if (maxX > 0)
                Random.nextInt(0, maxX + 1)
            else
                0

        val direction =
            if (targetX >= params.x) 1 else -1

        // Разворот персонажа
        yukiView.scaleX =
            if (direction > 0) 1f else -1f

        walkFrame = 0

        val movement =
            object : Runnable {

                override fun run() {

                    if (destroyed) return

                    if (userDragging) {
                        autoMoving = false
                        showIdle()
                        scheduleNextAction()
                        return
                    }

                    val distance =
                        targetX - params.x

                    if (abs(distance) <= dpToPx(4)) {

                        params.x = targetX

                        updatePosition()

                        autoMoving = false

                        showIdle()

                        // Иногда после прогулки садится
                        if (Random.nextBoolean()) {
                            handler.postDelayed({
                                sit()
                            }, 500)
                        } else {
                            scheduleNextAction()
                        }

                        return
                    }

                    val step = dpToPx(3)

                    params.x +=
                        if (distance > 0)
                            step
                        else
                            -step

                    keepInsideScreen()

                    // Меняем кадры ног
                    walkFrame =
                        (walkFrame + 1) %
                                walkFrames.size

                    yukiView.setImageResource(
                        walkFrames[walkFrame]
                    )

                    updatePosition()

                    handler.postDelayed(
                        this,
                        110
                    )
                }
            }

        handler.post(movement)
    }

    // Юки сидит
    private fun sit() {

        if (destroyed || userDragging) {
            scheduleNextAction()
            return
        }

        autoMoving = false

        yukiView.scaleX = 1f
        yukiView.setImageResource(
            R.drawable.yuki_sit
        )

        handler.postDelayed({

            if (!destroyed && !userDragging) {
                showIdle()
                scheduleNextAction()
            }

        }, Random.nextLong(2500L, 5500L))
    }

    // Юки машет при тапе
    private fun wave() {

        if (destroyed) return

        autoMoving = false

        yukiView.scaleX = 1f
        yukiView.setImageResource(
            R.drawable.yuki_wave
        )

        handler.postDelayed({

            if (!destroyed && !userDragging) {
                showIdle()
                scheduleNextAction()
            }

        }, 1600)
    }

    private fun showIdle() {

        if (destroyed) return

        yukiView.scaleX = 1f
        yukiView.setImageResource(
            R.drawable.yuki
        )
    }

    private fun updatePosition() {

        if (destroyed) return

        try {
            windowManager.updateViewLayout(
                yukiView,
                params
            )
        } catch (_: Exception) {
        }
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

        destroyed = true

        handler.removeCallbacksAndMessages(null)

        if (::yukiView.isInitialized) {

            try {
                windowManager.removeView(yukiView)
            } catch (_: Exception) {
            }
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
