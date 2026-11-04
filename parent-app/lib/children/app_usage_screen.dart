import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'app_usage_repository.dart';

/// «Время в приложениях»: сколько минут каждое приложение было на экране ребёнка за день.
/// Дни — те же последние [trackDays], что и у маршрута.
class AppUsageScreen extends ConsumerStatefulWidget {
  const AppUsageScreen({
    required this.deviceId,
    required this.title,
    this.embedded = false,
    super.key,
  });

  final String deviceId;
  final String title;

  /// Внутри вкладки экрана ребёнка: без своей шапки — её даёт экран ребёнка.
  final bool embedded;

  @override
  ConsumerState<AppUsageScreen> createState() => _AppUsageScreenState();
}

class _AppUsageScreenState extends ConsumerState<AppUsageScreen> {
  /// Выбранный день: 0 — сегодня, 1 — вчера, …
  int _day = 0;

  AppUsageRequest get _request {
    final now = DateTime.now();
    return (deviceId: widget.deviceId, date: isoDate(DateTime(now.year, now.month, now.day - _day)));
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final usage = ref.watch(appUsageProvider(_request));
    return Scaffold(
      appBar: widget.embedded ? null : AppBar(
        title: Text(widget.title),
        actions: [
          IconButton(
            tooltip: t.commonRetry,
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(appUsageProvider(_request)),
          ),
        ],
      ),
      body: Column(
        children: [
          _daySelector(t),
          Expanded(
            child: usage.when(
              data: (day) => _content(context, t, day),
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) => _error(t, error),
            ),
          ),
        ],
      ),
    );
  }

  Widget _daySelector(AppLocalizations t) {
    final now = DateTime.now();
    final startToday = DateTime(now.year, now.month, now.day);
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
      child: SegmentedButton<int>(
        segments: [
          for (var d = 0; d < trackDays; d++)
            ButtonSegment(value: d, label: Text(_dayLabel(t, startToday, d))),
        ],
        selected: {_day},
        onSelectionChanged: (s) => setState(() => _day = s.first),
      ),
    );
  }

  String _dayLabel(AppLocalizations t, DateTime startToday, int offset) => switch (offset) {
        0 => t.locationDayToday,
        1 => t.locationDayYesterday,
        _ => DateFormat.MMMd().format(startToday.subtract(Duration(days: offset))),
      };

  Widget _content(BuildContext context, AppLocalizations t, DayAppUsage day) {
    if (day.updatedAt == null) return _message(t.usageDayEmpty);
    if (day.apps.isEmpty) return _message(t.usageDayUnused);
    final text = Theme.of(context).textTheme;
    final longest = day.apps.first.foreground.inSeconds;
    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: Text(t.usageTotal(formatUsage(t, day.total)), style: text.titleMedium),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
          child: Text(
            t.usageUpdated(DateFormat.Hm().format(day.updatedAt!.toLocal())),
            style: text.bodySmall,
          ),
        ),
        for (final app in day.apps)
          ListTile(
            title: Text(app.label),
            subtitle: Padding(
              padding: const EdgeInsets.only(top: 6),
              // Доля от самого долгого приложения дня — чтобы сразу видеть, где больше всего.
              child: LinearProgressIndicator(
                value: longest == 0 ? 0 : app.foreground.inSeconds / longest,
              ),
            ),
            trailing: Text(formatUsage(t, app.foreground), style: text.bodyMedium),
          ),
      ],
    );
  }

  Widget _message(String text) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(text, textAlign: TextAlign.center),
        ),
      );

  Widget _error(AppLocalizations t, Object error) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(errorText(t, error), textAlign: TextAlign.center),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: () => ref.invalidate(appUsageProvider(_request)),
                child: Text(t.commonRetry),
              ),
            ],
          ),
        ),
      );
}

/// «1 ч 25 мин» или «40 мин». Меньше минуты телефон не присылает.
String formatUsage(AppLocalizations t, Duration duration) {
  final hours = duration.inHours;
  final minutes = duration.inMinutes.remainder(60);
  return hours > 0 ? t.usageHoursMinutes(hours, minutes) : t.usageMinutes(minutes);
}
