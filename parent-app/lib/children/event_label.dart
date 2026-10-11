import '../l10n/app_localizations.dart';
import 'device_repository.dart';

/// Локализованный текст события по его проводному типу (EventType.wireName).
/// Для заблокированных сайтов дописывает адрес, если он пришёл.
String eventLabel(AppLocalizations t, DeviceEvent event) {
  final base = _base(t, event.type);
  final site = event.detail;
  final isSite = event.type == 'site_blocked_filtered' || event.type == 'site_blocked_gambling';
  if (isSite && site != null && site.isNotEmpty) {
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
  }
  // Неизвестный серверу/приложению тип не роняет экран: показываем сам код.
  return type;
}
