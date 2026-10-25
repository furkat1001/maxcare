package uz.maxcare.kids.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.location.LocationFix
import uz.maxcare.kids.core.location.LocationProvider

/**
 * Один фикс через системный [LocationManager] — без зависимости от Google Play Services,
 * поэтому работает и на телефонах без GMS. [LocationManagerCompat.getCurrentLocation] сам
 * выбирает путь по версии Android (новый getCurrentLocation или разовое обновление на старых).
 *
 * Сначала дешёвый источник (fused / сеть). Если он дал позицию хуже [PRECISE_METERS] — по Wi‑Fi
 * и вышкам она «прыгала» на сотни метров, — дополнительно спрашиваем GPS и берём лучшую.
 * Дома Wi‑Fi обычно точнее 100 м, и GPS не включается — батарея не тратится зря.
 */
internal class LocationManagerLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LocationProvider {

    override suspend fun current(): LocationFix? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val provider = manager?.let(::enabledProvider)
        return if (manager != null && provider != null) {
            withContext(ioDispatcher) {
                // getCurrentLocation может «висеть» в помещении, пока система не остановит задачу —
                // ограничиваем ожидание, затем берём недавнюю последнюю позицию.
                val quick = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { requestFix(manager, provider) }
                    ?: recentLastKnown(manager)
                if (needsGps(quick) && gpsAvailable(manager, provider)) {
                    val gps = withTimeoutOrNull(GPS_TIMEOUT_MILLIS) {
                        requestFix(manager, LocationManager.GPS_PROVIDER)
                    }
                    moreAccurate(quick, gps)
                } else {
                    quick
                }
            }
        } else {
            null
        }
    }

    // GPS даёт точность только с разрешением на точное местоположение и если он ещё не опрошен.
    private fun gpsAvailable(manager: LocationManager, used: String) =
        used != LocationManager.GPS_PROVIDER &&
            granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            manager.allProviders.contains(LocationManager.GPS_PROVIDER) &&
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    // Разрешение проверено в current() перед вызовом; SecurityException всё равно ловим —
    // отдельные прошивки отзывают доступ на лету, и это не должно ронять сбор.
    @SuppressLint("MissingPermission")
    private suspend fun requestFix(manager: LocationManager, provider: String): LocationFix? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            val executor = Executor { it.run() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancellation,
                    executor
                ) {
                    continuation.resumeWith(Result.success(it?.toFix()))
                }
            } catch (ignored: SecurityException) {
                continuation.resumeWith(Result.success(null))
            }
        }

    // Самая свежая последняя позиция среди провайдеров, если она не старше MAX_FIX_AGE_NANOS.
    @SuppressLint("MissingPermission")
    private fun recentLastKnown(manager: LocationManager): LocationFix? = try {
        val now = SystemClock.elapsedRealtimeNanos()
        PROVIDER_ORDER
            .filter { manager.allProviders.contains(it) }
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { now - it.elapsedRealtimeNanos <= MAX_FIX_AGE_NANOS }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.toFix()
    } catch (ignored: SecurityException) {
        null
    }

    private fun Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null
    )

    private fun enabledProvider(manager: LocationManager): String? = PROVIDER_ORDER.firstOrNull {
        manager.allProviders.contains(it) && manager.isProviderEnabled(it)
    }

    private fun hasPermission(): Boolean {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        return granted(fine) || granted(coarse)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        // Fused лучше по батарее, но константа LocationManager.FUSED_PROVIDER появилась в API 31,
        // а minSdk 26 — используем её строковое значение; на старых версиях провайдера просто нет
        // в allProviders и он пропускается. Дальше — сеть (дешевле GPS), затем GPS как запасной.
        const val FIX_TIMEOUT_MILLIS = 10_000L

        // Холодный старт GPS на улице — 10–30 с; в помещении он часто не находит спутники вовсе.
        const val GPS_TIMEOUT_MILLIS = 30_000L
        const val FUSED_PROVIDER = "fused"

        // Последняя позиция старше 10 минут уже не «где сейчас» — лучше дождаться нового фикса.
        const val MAX_FIX_AGE_NANOS = 10L * 60 * 1_000_000_000
        val PROVIDER_ORDER = listOf(
            FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )
    }
}

/** Позиции хуже этого (в метрах) уточняем по GPS; так же их отсекает карта родителя. */
internal const val PRECISE_METERS = 100.0

/** Нет позиции или она грубая — стоит спросить GPS. */
internal fun needsGps(fix: LocationFix?): Boolean =
    fix == null || (fix.accuracyMeters ?: 0.0) > PRECISE_METERS

/** Точнее та, у которой меньше радиус погрешности; без точности — хуже любой с точностью. */
internal fun moreAccurate(a: LocationFix?, b: LocationFix?): LocationFix? = when {
    a == null -> b
    b == null -> a
    (b.accuracyMeters ?: Double.MAX_VALUE) < (a.accuracyMeters ?: Double.MAX_VALUE) -> b
    else -> a
}
