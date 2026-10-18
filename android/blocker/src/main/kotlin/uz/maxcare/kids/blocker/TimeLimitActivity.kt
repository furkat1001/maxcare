package uz.maxcare.kids.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Date
import uz.maxcare.kids.core.R as CoreR
import uz.maxcare.kids.core.limits.TimeLimitExtension

/**
 * Экран «время вышло»: сработало ограничение родителя — перерыв или дневной лимит. В отличие от
 * заглушки опасного контента, причину показываем честно: когда снова можно и что лимит задал
 * родитель. Для игры — одна отсрочка «доиграть матч».
 */
class TimeLimitActivity : Activity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun timeLimitExtension(): TimeLimitExtension
    }

    private val extension: TimeLimitExtension by lazy {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .timeLimitExtension()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildContent(): LinearLayout {
        val brand = ContextCompat.getColor(this, CoreR.color.maxcare_brand)
        val onBrand = ContextCompat.getColor(this, CoreR.color.maxcare_on_brand)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val daily = intent.getStringExtra(EXTRA_REASON) == REASON_DAILY
        val until = DateFormat.getTimeFormat(this).format(Date(intent.getLongExtra(EXTRA_UNTIL, 0)))
        val app = labelOf(pkg)

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
            val title = if (daily) R.string.limit_daily_title else R.string.limit_break_title
            addView(label(getString(title), onBrand, TITLE_SP))
            addView(spacer(dp(GAP_DP)))
            val message = if (daily) {
                getString(R.string.limit_daily_message, app)
            } else {
                getString(R.string.limit_break_message, app, until)
            }
            addView(label(message, onBrand, BODY_SP))
            addView(spacer(dp(GAP_DP)))
            addView(label(getString(R.string.limit_set_by_parent), onBrand, NOTE_SP))
            addView(spacer(dp(PADDING_DP)))
            if (pkg.isNotEmpty() && extension.canExtend(pkg)) {
                addView(button(getString(R.string.limit_extend)) { extendAndReturn(pkg) })
            }
            addView(button(getString(R.string.limit_ok)) { finish() })
        }
    }

    private fun extendAndReturn(pkg: String) {
        if (extension.extend(pkg)) {
            packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity)
        }
        finish()
    }

    private fun labelOf(pkg: String): CharSequence = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
    } catch (ignored: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
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

    companion object {
        const val REASON_BREAK = "break"
        const val REASON_DAILY = "daily"

        private const val EXTRA_PACKAGE = "uz.maxcare.kids.blocker.PACKAGE"
        private const val EXTRA_REASON = "uz.maxcare.kids.blocker.REASON"
        private const val EXTRA_UNTIL = "uz.maxcare.kids.blocker.UNTIL"

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val PADDING_DP = 24
        private const val LOGO_DP = 96
        private const val GAP_DP = 8
        private const val TITLE_SP = 22f
        private const val BODY_SP = 16f
        private const val NOTE_SP = 13f

        /** @param untilMs когда приложение снова откроется (для дневного лимита не показывается). */
        fun intent(context: Context, packageName: String, reason: String, untilMs: Long): Intent =
            Intent(context, TimeLimitActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_UNTIL, untilMs)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
