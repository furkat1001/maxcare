import '../l10n/app_localizations.dart';
import 'device_repository.dart';

/// Локализованный текст события по его проводному типу (EventType.wireName).
/// Для заблокированных сайтов дописывает адрес, если он пришёл.
String eventLabel(AppLocalizations t, DeviceEvent event) {
  final base = _base(t, event.type);
  final site = event.detail;
  // Для сайта detail — адрес, для ограничения времени — пакет приложения.
  final withDetail = event.type == 'site_blocked_filtered' ||
      event.type == 'site_blocked_gambling' ||
      event.type == 'app_limit_reached';
  if (withDetail && site != null && site.isNotEmpty) {
    return t.eventSiteWithAddress(base, site);
  }
  return base;
}

String _base(AppLocalizations t, String type) {
  switch (type) {
    case 'admin_disabled':
      return t.eventAdminDisabled;
    case 'protection_disabled':
      return t.eventProtectionDisabled;
    case 'password_lockout':
      return t.eventPasswordLockout;
    case 'password_recovered':
      return t.eventPasswordRecovered;
    case 'notifications_off':
      return t.eventNotificationsOff;
    case 'device_booted':
      return t.eventDeviceBooted;
    case 'device_shutdown':
      return t.eventDeviceShutdown;
    case 'vpn_off':
      return t.eventVpnOff;
    case 'private_dns_on':
      return t.eventPrivateDnsOn;
    case 'site_blocked_filtered':
      return t.eventSiteBlockedFiltered;
    case 'site_blocked_gambling':
      return t.eventSiteBlockedGambling;
    case 'search_blocked':
      return t.eventSearchBlocked;
    case 'text_flagged_messenger':
      return t.eventTextFlaggedMessenger;
    case 'image_blocked_nudity':
      return t.eventImageBlockedNudity;
    case 'image_blocked_violence':
      return t.eventImageBlockedViolence;
    case 'uninstall_attempt_blocked':
      return t.eventUninstallAttemptBlocked;
    case 'accessibility_off':
      return t.eventAccessibilityOff;
    case 'app_limit_reached':
      return t.eventAppLimitReached;
  }
  // Неизвестный серверу/приложению тип не роняет экран: показываем сам код.
  return type;
}
