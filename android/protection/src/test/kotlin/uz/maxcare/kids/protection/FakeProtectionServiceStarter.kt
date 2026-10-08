package uz.maxcare.kids.protection

import uz.maxcare.kids.protection.service.ProtectionServiceStarter

internal class FakeProtectionServiceStarter : ProtectionServiceStarter {
    var running = false
        private set

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }
}
