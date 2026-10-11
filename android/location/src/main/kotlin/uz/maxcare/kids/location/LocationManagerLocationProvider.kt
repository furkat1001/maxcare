package uz.maxcare.kids.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.location.LocationFix
import uz.maxcare.kids.core.location.LocationProvider

/**
 * Один фикс через системный [LocationManager] — без зависимости от Google Play Services,
 * поэтому работает и на телефонах без GMS. [LocationManagerCompat.getCurrentLocation] сам
 * выбирает путь по версии Android (новый getCurrentLocation или разовое обновление на старых).
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
            withContext(ioDispatcher) { requestFix(manager, provider) }
        } else {
            null
        }
    }

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
        const val FUSED_PROVIDER = "fused"
        val PROVIDER_ORDER = listOf(
            FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER
        )
    }
}
