import 'package:intl/intl.dart';

import '../l10n/app_localizations.dart';

/// Текст «последняя активность» для устройства: «не подключён», либо дата/время,
/// а для давно пропавшего телефона — сколько его не видно.
String lastSeenText(AppLocalizations t, DateTime? lastSeenAt, {DateTime? now}) {
  if (lastSeenAt == null) return t.deviceNeverSeen;
  final at = now ?? DateTime.now();
  final gap = at.difference(lastSeenAt);
  if (gap.inMinutes >= 30) {
    return t.deviceOffline(_humanDuration(t, gap));
  }
  return t.deviceLastSeen(DateFormat.Hm().format(lastSeenAt.toLocal()));
}

String _humanDuration(AppLocalizations t, Duration gap) {
  if (gap.inDays >= 1) return t.durationDays(gap.inDays);
  if (gap.inHours >= 1) return t.durationHours(gap.inHours);
  return t.durationMinutes(gap.inMinutes);
}
