import 'device_repository.dart';

/// Насколько событие важно родителю: [attention] — обход защиты или опасный контент,
/// [info] — обычная жизнь телефона (включение, лимит времени, геолокация вернулась).
enum EventLevel { attention, info }

EventLevel levelOf(String type) =>
    _infoTypes.contains(type) ? EventLevel.info : EventLevel.attention;

// Неизвестный новый тип по умолчанию считается важным: лучше показать лишнее, чем пропустить.
const _infoTypes = {
  'device_booted',
  'device_shutdown',
  'location_on',
  'app_limit_reached',
};

/// Пункт ленты дня: одно событие или несколько одинаковых подряд.
class TimelineEntry {
  TimelineEntry({
    required this.type,
    required this.from,
    required this.to,
    required this.count,
    required this.details,
  });

  final String type;
  final DateTime from;
  final DateTime to;
  final int count;

  /// Адреса сайтов или пакеты приложений из склеенных событий, без повторов.
  final List<String> details;

  EventLevel get level => levelOf(type);

  /// Событие для подписи — первое из склеенных, с первым уточнением.
  DeviceEvent get sample => DeviceEvent(
    type: type,
    occurredAt: from,
    receivedAt: from,
    detail: details.length == 1 ? details.single : null,
  );
}

/// Лента дня по времени события на телефоне, от утра к вечеру. Одинаковые события,
/// идущие подряд с паузой не больше [mergeWithin], склеиваются: десять блокировок
/// одного сайта за минуту — один пункт «10 раз», а не десять строк.
List<TimelineEntry> buildTimeline(
  List<DeviceEvent> events, {
  Duration mergeWithin = const Duration(minutes: 30),
}) {
  final sorted = [...events]
    ..sort((a, b) => a.occurredAt.compareTo(b.occurredAt));
  final entries = <TimelineEntry>[];
  for (final e in sorted) {
    final last = entries.isEmpty ? null : entries.last;
    final detail = e.detail;
    if (last != null &&
        last.type == e.type &&
        e.occurredAt.difference(last.to) <= mergeWithin) {
      entries[entries.length - 1] = TimelineEntry(
        type: last.type,
        from: last.from,
        to: e.occurredAt,
        count: last.count + 1,
        details: detail == null || last.details.contains(detail)
            ? last.details
            : [...last.details, detail],
      );
    } else {
      entries.add(
        TimelineEntry(
          type: e.type,
          from: e.occurredAt,
          to: e.occurredAt,
          count: 1,
          details: detail == null ? const [] : [detail],
        ),
      );
    }
  }
  return entries;
}
