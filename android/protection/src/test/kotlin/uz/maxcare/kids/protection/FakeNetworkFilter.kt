package uz.maxcare.kids.protection

import android.content.Intent
import uz.maxcare.kids.core.filter.NetworkFilter

internal class FakeNetworkFilter(var permitted: Boolean = true) : NetworkFilter {
    var running = false
        private set
    var bypassChecks = 0
        private set

    override fun isPermitted() = permitted

    override fun permissionIntent(): Intent? = null

    override fun start() {
        if (permitted) running = true
    }

    override fun stop() {
        running = false
    }

    override suspend fun checkBypasses() {
        bypassChecks++
    }
}
