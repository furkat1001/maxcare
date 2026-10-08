package uz.maxcare.kids.protection

internal class FakeDevicePolicyGateway(
    var adminActive: Boolean = false,
    var deviceOwner: Boolean = false
) : DevicePolicyGateway {
    var uninstallBlocked = false
        private set
    val restrictions = mutableSetOf<String>()
    var policyCalls = 0
        private set

    override fun isAdminActive() = adminActive || deviceOwner

    override fun isDeviceOwner() = deviceOwner

    override fun setOwnUninstallBlocked(blocked: Boolean) {
        requireOwner()
        uninstallBlocked = blocked
    }

    override fun addUserRestriction(restriction: String) {
        requireOwner()
        restrictions += restriction
    }

    override fun clearUserRestriction(restriction: String) {
        requireOwner()
        restrictions -= restriction
    }

    override fun clearDeviceOwner() {
        requireOwner()
        deviceOwner = false
        adminActive = false
    }

    // Как и настоящий DevicePolicyManager, без прав владельца бросает SecurityException.
    private fun requireOwner() {
        policyCalls++
        if (!deviceOwner) throw SecurityException("Not a device owner")
    }
}
