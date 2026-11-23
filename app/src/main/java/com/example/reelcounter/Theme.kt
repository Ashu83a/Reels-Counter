package com.example.reelcounter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

// All interface colours and small style helpers in one place.
// Change a colour here and every screen follows.
object Theme {
    // Classic British light theme
    val BG = 0xFFF4EEDD.toInt()
    val CARD = 0xFFFBF7EC.toInt()
    val GREEN = 0xFF1F4D3A.toInt()
    val GREEN_DARK = 0xFF143628.toInt()
    val BRASS = 0xFFB08D4C.toInt()
    val BRASS_SOFT = 0xFFD9C79A.toInt()
    val TEXT = 0xFF2B2B2B.toInt()
    val TEXT_SOFT = 0xFF6B645A.toInt()
    val DIVIDER = 0xFFE2D9C2.toInt()
    val CREAM = 0xFFF4EEDD.toInt()
    val BURGUNDY = 0xFF5A1F2B.toInt()

    val SERIF: Typeface = Typeface.SERIF
    val SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    // Chart colours, one per app
    val INSTAGRAM = 0xFFC97B8B.toInt()
    val FACEBOOK = 0xFF2F4A78.toInt()
    val YOUTUBE = 0xFFA63D32.toInt()
    val THREADS = 0xFF7A4B7E.toInt()
    val LINKEDIN = 0xFF2E7C85.toInt()
    val SNAPCHAT = 0xFFD2AE4E.toInt()
    val TIKTOK = 0xFF8C8A84.toInt()
    val OTHER = 0xFFA9A397.toInt()

    // Works with an app name or a package name.
    fun appColor(name: String): Int {
        val n = name.lowercase()
        return when {
            n.contains("barcelona") || n.contains("threads") -> THREADS
            n.contains("instagram") -> INSTAGRAM
            n.contains("facebook") -> FACEBOOK
            n.contains("youtube") -> YOUTUBE
            n.contains("linkedin") -> LINKEDIN
            n.contains("snapchat") -> SNAPCHAT
            n.contains("tiktok") || n.contains("musically") || n.contains("trill") -> TIKTOK
            else -> OTHER
        }
    }

    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radiusPx
        g.setColor(color)
        if (strokePx > 0) g.setStroke(strokePx, strokeColor)
        return g
    }

    // Layout params with a gap above and below.
    fun lp(c: Context, top: Float = 0f, bottom: Float = 0f): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(c, top)
        p.bottomMargin = dp(c, bottom)
        return p
    }

    // An ivory card with rounded corners and a thin border.
    fun card(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(CARD, dp(c, 16f).toFloat(), DIVIDER, dp(c, 1f))
        val pad = dp(c, 16f)
        l.setPadding(pad, pad, pad, pad)
        l.elevation = dp(c, 2f).toFloat()
        return l
    }

    fun heading(c: Context, text: String, size: Float = 20f): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = size
        t.typeface = SERIF_BOLD
        t.setTextColor(GREEN)
        return t
    }

    fun body(c: Context, text: String, size: Float = 15f): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = size
        t.setTextColor(TEXT)
        return t
    }

    fun soft(c: Context, text: String, size: Float = 13f): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = size
        t.setTextColor(TEXT_SOFT)
        return t
    }

    // Main button: racing green with cream text.
    fun button(c: Context, text: String, bg: Int = GREEN, fg: Int = CREAM): Button {
        val b = Button(c)
        b.text = text
        b.setAllCaps(false)
        b.setTextColor(fg)
        b.textSize = 15f
        b.background = rounded(bg, dp(c, 12f).toFloat())
        b.stateListAnimator = null
        return b
    }

    // Quiet button: ivory with a brass border.
    fun softButton(c: Context, text: String): Button {
        val b = Button(c)
        b.text = text
        b.setAllCaps(false)
        b.setTextColor(GREEN)
        b.textSize = 15f
        b.background = rounded(CARD, dp(c, 12f).toFloat(), BRASS, dp(c, 1f))
        b.stateListAnimator = null
        return b
    }

    fun field(c: Context, value: String, numbers: Boolean): EditText {
        val e = EditText(c)
        e.inputType = if (numbers) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        e.setText(value)
        e.setTextColor(TEXT)
        e.textSize = 15f
        e.background = rounded(0xFFFFFFFF.toInt(), dp(c, 10f).toFloat(), DIVIDER, dp(c, 1f))
        val h = dp(c, 12f)
        val v = dp(c, 8f)
        e.setPadding(h, v, h, v)
        e.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        return e
    }
}
