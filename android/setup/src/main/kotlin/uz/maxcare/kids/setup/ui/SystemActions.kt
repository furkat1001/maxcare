package uz.maxcare.kids.setup.ui

/** Колбэки, которые требуют Activity: системные экраны и разрешения. */
internal class SystemActions(
    val activateDeviceAdmin: () -> Unit,
    val enableWebFilter: () -> Unit,
    val requestNotifications: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val requestLocation: () -> Unit,
    /** Особый доступ: системного диалога нет, только экран настроек со списком приложений. */
    val openUsageAccessSettings: () -> Unit,
    val openAppSettings: () -> Unit
)
