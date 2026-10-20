package uz.maxcare.kids.accessibility.limits

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uz.maxcare.kids.core.limits.TimeLimitExtension
import uz.maxcare.kids.core.limits.TodayUsage
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/**
 * Применяет ограничения времени на телефоне: держит [AppTimeLimiter], раз в секунду сообщает ему,
 * что на экране, сохраняет состояние и подтягивает правила. Блокирует служба доступности по
 * вердикту — здесь только решение.
 *
 * Часы — «доверенные»: время запуска плюс монотонные часы с загрузки. Перевод времени в
 * настройках, пока служба работает, ни лимит, ни перерыв не сбрасывает.
 */
@Singleton
class AppLimitEnforcer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppLimitsRepository,
    private val todayUsage: TodayUsage,
    private val store: SecureStore
) : TimeLimitExtension {

    private val wallAtStart = System.currentTimeMillis()
    private val elapsedAtStart = SystemClock.elapsedRealtime()
    private val groups = ConcurrentHashMap<String, String>()
    private val lock = Any()

    private val limiter = AppTimeLimiter(
        now = { wallAtStart + (SystemClock.elapsedRealtime() - elapsedAtStart) },
        zone = { ZoneId.systemDefault() },
        groupOf = ::groupOf
    )

    /** Окна, которые не меняют «приложение на экране»: шторка, клавиатура. */
    @Volatile
    private var transient: Set<String> = emptySet()

    @Volatile
    private var current: String? = null

    /** Окно сменилось; вердикт — сразу, не дожидаясь следующего тика. */
    fun onWindow(packageName: String): LimitVerdict {
        if (packageName in transient) return LimitVerdict.Free
        current = packageName
        return synchronized(lock) { limiter.tick(packageName) }
    }

    /** Время ещё не засчитано, но сохранить его стоит, например перед блокировкой. */
    // Сохраняем и без правил: иначе после перезапуска вернулось бы старое состояние, где
    // удалённое правило ещё «известно».
    suspend fun save() {
        val state = synchronized(lock) { limiter.snapshot() }
        try {
            store.writeString(
                SecureKeys.APP_LIMIT_STATE,
                json.encodeToString(LimiterState.serializer(), state)
            )
        } catch (ignored: SecureStoreException) {
            // Не сохранилось — после перезапуска потраченное подтянет системная история.
        }
    }

    /**
     * Цикл службы, пока она подключена.
     * @param isInteractive включён ли экран; при выключенном время не идёт.
     * @param activeWindow пакет активного окна — чтобы знать, что на экране, сразу после запуска
     *   службы, а не только после следующей смены окна.
     * @param onVerdict предупреждение или блокировка для приложения на экране.
     */
    suspend fun run(
        isInteractive: () -> Boolean,
        activeWindow: () -> String?,
        onVerdict: (String, LimitVerdict) -> Unit
    ) {
        start()
        if (current == null) current = activeWindow()?.takeIf { it !in transient }
        var second = 0L
        while (currentCoroutineContext().isActive) {
            val pkg = current?.takeIf { isInteractive() }
            val verdict = synchronized(lock) { limiter.tick(pkg) }
            if (pkg != null && verdict != LimitVerdict.Free) onVerdict(pkg, verdict)
            second++
            if (second % SAVE_EVERY_S == 0L) save()
            if (second % BASELINE_EVERY_S == 0L) applyBaseline()
            // Правила родителя — раз в минуту, но только при включённом экране: во сне сеть не трогаем.
            if (second % REFRESH_EVERY_S == 0L && isInteractive()) refresh()
            delay(TICK_MS)
        }
    }

    override fun canExtend(packageName: String): Boolean =
        synchronized(lock) { limiter.canExtend(packageName) }

    override fun extend(packageName: String): Boolean =
        synchronized(lock) { limiter.extend(packageName) }

    private suspend fun start() {
        transient = transientPackages()
        val exempt = exemptPackages()
        try {
            repository.loadCached()
            store.readString(SecureKeys.APP_LIMIT_STATE)?.let { text ->
                val saved = json.decodeFromString(LimiterState.serializer(), text)
                synchronized(lock) { limiter.restore(saved) }
            }
        } catch (ignored: SecureStoreException) {
            // Хранилище недоступно — начинаем с нуля, а системная история подтянет потраченное.
        } catch (ignored: SerializationException) {
            // Состояние от старой версии — так же.
        }
        synchronized(lock) {
            limiter.exempt = exempt
            limiter.config = repository.current()
        }
        refresh()
        applyBaseline()
    }

    private suspend fun refresh() {
        try {
            repository.refresh()
        } catch (ignored: SecureStoreException) {
            return
        }
        val changed = synchronized(lock) {
            val before = limiter.config.rules.map { it.key }.toSet()
            limiter.config = repository.current()
            limiter.config.rules.map { it.key }.toSet() != before
        }
        // Правило добавили или убрали — сверить сразу, а не через BASELINE_EVERY_S: иначе новое
        // правило несколько минут работает со старым счётчиком.
        if (changed) applyBaseline()
    }

    private suspend fun applyBaseline() {
        // Без правил историю не читаем, но ограничитель вызываем: он «забывает» удалённые
        // правила, и добавленное снова считается новым.
        val today = if (repository.current().rules.isEmpty()) {
            emptyMap()
        } else {
            todayUsage.foregroundToday()
        }
        synchronized(lock) { limiter.applyBaseline(today) }
    }

    private fun groupOf(packageName: String): String? = groups.getOrPut(packageName) {
        val category = try {
            context.packageManager.getApplicationInfo(packageName, 0).category
        } catch (ignored: PackageManager.NameNotFoundException) {
            -1
        }
        AppGroups.groupOf(packageName, category).orEmpty()
    }.ifEmpty { null }

    /** Никогда не блокируются: звонки и SMS (в том числе экстренные), лаунчер и сам MaxCare Kids. */
    private fun exemptPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
        val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        val sms = Telephony.Sms.getDefaultSmsPackage(context)
        return (
            launchers + listOfNotNull(
                dialer,
                sms
            ) + ALWAYS_EXEMPT + context.packageName
            ).toSet()
    }

    private fun transientPackages(): Set<String> {
        val keyboards = context.getSystemService(InputMethodManager::class.java)
            ?.inputMethodList.orEmpty()
            .map { it.packageName }
        return (keyboards + SYSTEM_UI + context.packageName).toSet()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val SYSTEM_UI = "com.android.systemui"
        val ALWAYS_EXEMPT =
            setOf(SYSTEM_UI, "android", "com.android.phone", "com.android.server.telecom")
        const val TICK_MS = 1_000L
        const val SAVE_EVERY_S = 30L
        const val BASELINE_EVERY_S = 5 * 60L
        const val REFRESH_EVERY_S = 60L
    }
}
