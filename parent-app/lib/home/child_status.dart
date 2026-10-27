import '../children/daily_report_repository.dart';
import '../children/last_seen.dart';

/// Состояние ребёнка на главной — от худшего к лучшему.
enum ChildState { notPaired, offline, attention, calm }

/// [today] — итоги сегодняшнего дня; null, пока не загрузились или не загрузились вовсе:
/// тогда судим только по связи, а не пугаем родителя «вниманием».
ChildState childState(
  DateTime? lastSeenAt,
  DailyReport? today, {
  DateTime? now,
}) {
  if (lastSeenAt == null) return ChildState.notPaired;
  if ((now ?? DateTime.now()).difference(lastSeenAt) >= offlineAfter) {
    return ChildState.offline;
  }
  if (today != null &&
      (today.bypassAttempts.isNotEmpty || today.protectionDisabled > 0)) {
    return ChildState.attention;
  }
  return ChildState.calm;
}
