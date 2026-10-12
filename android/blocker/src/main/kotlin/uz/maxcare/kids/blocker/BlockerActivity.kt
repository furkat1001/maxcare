package uz.maxcare.kids.blocker

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import uz.maxcare.kids.core.R as CoreR

/**
 * Экран-заглушка: показывается, когда защита перекрыла действие (опасный контент или попытка
 * удаления). Объясняет ребёнку, что произошло, и предлагает позвать родителя — скрытых режимов нет.
 *
 * Простая [Activity] с программной разметкой: модулю не нужны ни AppCompat (его тема роняла экран),
 * ни Compose. Причина блокировки в UI не показывается — она только для журнала родителя.
 */
class BlockerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildContent(): LinearLayout {
        val brand = ContextCompat.getColor(this, CoreR.color.maxcare_brand)
        val onBrand = ContextCompat.getColor(this, CoreR.color.maxcare_on_brand)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(brand)
            setPadding(dp(PADDING_DP))
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)

            addView(
                ImageView(context).apply {
                    setImageResource(CoreR.drawable.ic_maxcare_logo)
                    layoutParams = LinearLayout.LayoutParams(dp(LOGO_DP), dp(LOGO_DP))
                }
            )
            addView(spacer(dp(PADDING_DP)))
            addView(label(getString(R.string.blocker_title), onBrand, TITLE_SP))
            addView(spacer(dp(GAP_DP)))
            addView(label(getString(R.string.blocker_message), onBrand, BODY_SP))
            addView(spacer(dp(PADDING_DP)))
            addView(
                Button(context).apply {
                    text = getString(R.string.blocker_open_app)
                    setOnClickListener { openParentApp() }
                }
            )
        }
    }

    private fun openParentApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
        finish()
    }

    private fun label(text: String, color: Int, sizeSp: Float) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = sizeSp
        gravity = Gravity.CENTER
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, height)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val PADDING_DP = 24
        const val LOGO_DP = 96
        const val GAP_DP = 8
        const val TITLE_SP = 22f
        const val BODY_SP = 16f
    }
}
