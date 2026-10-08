package uz.maxcare.kids.protection

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import uz.maxcare.kids.core.protection.DeviceAdminActivation

internal class AndroidDeviceAdminActivation @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceAdminActivation {
    override fun activationIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(
            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
            ComponentName(context, MaxCareAdminReceiver::class.java)
        )
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.device_admin_activation_explanation)
        )
}
