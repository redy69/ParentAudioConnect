package com.santethp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.ViewGroup

/**
 * Full-screen, Maps-themed parent UI for the two-way live conversation.
 * Audio transport and permission handling remain owned by MainActivity/service.
 */
class LiveConversationPage(
    context: Context,
    private val onBack: () -> Unit,
    private val onStart: () -> Unit,
    private val onToggleParentMute: () -> Unit,
    private val onEnd: () -> Unit
) : LinearLayout(context) {

    private val statusText = TextView(context)
    private val equalizer = ConversationEqualizerView(context)
    private val startButton = makeButton("MULAI PERCAKAPAN", intArrayOf(Color.rgb(36, 132, 88), Color.rgb(22, 99, 67)))
    private val muteButton = makeButton("MUTE ORTU", intArrayOf(Color.rgb(67, 132, 105), Color.rgb(43, 105, 78)))
    private val endButton = makeButton("AKHIRI PERCAKAPAN", intArrayOf(Color.rgb(190, 76, 70), Color.rgb(150, 48, 45)))

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(18))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(244, 249, 246), Color.rgb(250, 252, 250), Color.rgb(238, 246, 241))
        )

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val back = TextView(context).apply {
            text = "‹"
            textSize = 34f
            setTextColor(Color.rgb(24, 86, 64))
            gravity = Gravity.CENTER
            contentDescription = "Kembali ke dashboard"
            setPadding(dp(8), 0, dp(14), 0)
            setOnClickListener { onBack() }
        }
        header.addView(back, LayoutParams(dp(44), dp(48)))
        val heading = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        heading.addView(TextView(context).apply {
            text = "Percakapan Langsung"
            textSize = 21f
            setTextColor(Color.rgb(24, 86, 64))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        heading.addView(TextView(context).apply {
            text = "Suara dua arah • dikendalikan dari HP orang tua"
            textSize = 11.5f
            setTextColor(Color.rgb(103, 126, 112))
        })
        header.addView(heading, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(12))
        }

        val statusCard = makeCard()
        statusCard.addView(TextView(context).apply {
            text = "STATUS PERCAKAPAN"
            textSize = 10.5f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(92, 125, 104))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        statusText.apply {
            text = "Siap memulai percakapan"
            textSize = 15f
            setTextColor(Color.rgb(36, 79, 55))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        }
        statusCard.addView(statusText)
        content.addView(statusCard, cardParams())

        val audioCard = makeCard()
        audioCard.addView(TextView(context).apply {
            text = "AKTIVITAS AUDIO"
            textSize = 10.5f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(92, 125, 104))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        equalizer.contentDescription = "Indikator aktivitas audio percakapan"
        audioCard.addView(equalizer, LayoutParams(LayoutParams.MATCH_PARENT, dp(82)).apply {
            topMargin = dp(12)
        })
        audioCard.addView(TextView(context).apply {
            text = "Hijau bergerak saat sesi percakapan aktif"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(113, 135, 121))
            setPadding(0, dp(7), 0, 0)
        })
        content.addView(audioCard, cardParams())

        startButton.setOnClickListener { onStart() }
        muteButton.setOnClickListener { onToggleParentMute() }
        endButton.setOnClickListener { onEnd() }
        content.addView(startButton, buttonParams())
        content.addView(muteButton, buttonParams())
        content.addView(endButton, buttonParams())

        content.addView(TextView(context).apply {
            text = "MUTE ORTU hanya mematikan mikrofon HP orang tua. Mikrofon anak tetap aktif sampai percakapan diakhiri."
            textSize = 11.5f
            setTextColor(Color.rgb(94, 119, 103))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(8))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        scroll.addView(content)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        render(false, false, false, "Siap memulai percakapan")
    }

    fun render(sessionActive: Boolean, audioActive: Boolean, parentMuted: Boolean, status: String) {
        statusText.text = status
        equalizer.setActive(audioActive)
        startButton.visibility = if (sessionActive) GONE else VISIBLE
        muteButton.visibility = if (sessionActive) VISIBLE else GONE
        endButton.visibility = if (sessionActive) VISIBLE else GONE
        muteButton.text = if (parentMuted) "NYALAKAN MIKROFON ORTU" else "MUTE ORTU"
    }

    private fun makeCard(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(207, 226, 215))
        }
        elevation = dp(2).toFloat()
    }

    private fun makeButton(label: String, colors: IntArray): Button = Button(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.WHITE)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setAllCaps(false)
        minHeight = dp(54)
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.argb(45, 255, 255, 255))
        }
        elevation = dp(2).toFloat()
    }

    private fun cardParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(12)
    }

    private fun buttonParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(5)
        bottomMargin = dp(7)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private class ConversationEqualizerView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var active = false
        private val tick = object : Runnable {
            override fun run() {
                if (!active || !isAttachedToWindow || visibility != VISIBLE) return
                invalidate()
                postDelayed(this, 55L)
            }
        }

        fun setActive(value: Boolean) {
            if (active == value) return
            active = value
            removeCallbacks(tick)
            invalidate()
            if (active && isAttachedToWindow) post(tick)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (active) post(tick)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(tick)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val radius = dp(14).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(246, 251, 247)
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(221, 236, 225)
            canvas.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius, radius, paint)
            paint.style = Paint.Style.FILL
            val count = 25
            val gap = dp(3).toFloat()
            val barWidth = dp(4).toFloat()
            val total = count * barWidth + (count - 1) * gap
            val start = (w - total) / 2f
            val center = h / 2f
            val now = android.os.SystemClock.uptimeMillis()
            for (i in 0 until count) {
                val wave = if (active) {
                    (kotlin.math.sin(now / 170.0 + i * 0.61) * 0.5 + 0.5).toFloat()
                } else {
                    (0.18f + (i % 5) * 0.07f)
                }
                val barHeight = dp(7).toFloat() + wave * dp(if (active) 43 else 12)
                paint.color = if (active) {
                    Color.rgb(28, 145 + (wave * 70).toInt(), 66 + (wave * 20).toInt())
                } else {
                    Color.rgb(183, 211, 192)
                }
                val x = start + i * (barWidth + gap)
                canvas.drawRoundRect(
                    x, center - barHeight / 2f, x + barWidth, center + barHeight / 2f,
                    barWidth, barWidth, paint
                )
            }
        }

        private fun dp(value: Int): Int =
            (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}
