package com.lujian.travelplan.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

internal data class LujianMapInfoAction(
    val label: String,
    val onClick: () -> Unit,
)

internal fun createLujianMapInfoWindow(
    context: Context,
    title: String,
    subtitle: String? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: List<LujianMapInfoAction> = emptyList(),
): LinearLayout {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#FAF6EF"))
            setStroke(dp(3), Color.parseColor("#2A2520"))
        }
        elevation = dp(8).toFloat()
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        addView(TextView(context).apply {
            text = title
            setTextColor(Color.parseColor("#2A2520"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxWidth = dp(230)
            onTitleClick?.let { click -> setOnClickListener { click() } }
        })

        subtitle?.takeIf(String::isNotBlank)?.let { value ->
            addView(TextView(context).apply {
                text = value
                setTextColor(Color.parseColor("#6B6354"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                maxWidth = dp(230)
                setPadding(0, dp(4), 0, 0)
            })
        }

        actions.forEach { action ->
            addView(TextView(context).apply {
                text = action.label
                setTextColor(Color.parseColor("#FF6B4A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                maxWidth = dp(230)
                setPadding(dp(4), dp(5), dp(4), dp(2))
                setOnClickListener { action.onClick() }
            })
        }

        alpha = 0f
        scaleX = .92f
        scaleY = .92f
        post {
            pivotX = width / 2f
            pivotY = height.toFloat()
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(170)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
