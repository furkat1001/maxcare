import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'app_usage_repository.dart';
import 'app_usage_screen.dart';
import 'daily_report_repository.dart';
import 'day_timeline.dart';
import 'day_timeline_view.dart';
import 'device_repository.dart';
import 'event_label.dart';

/// «Итоги дня»: кольцо экранного времени, лента событий дня, ниже — сводка по разделам.
/// Дни — те же последние [trackDays], что и у маршрута и времени в приложениях.
class DailyReportScreen extends ConsumerStatefulWidget {
  const DailyReportScreen({
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
  ConsumerState<DailyReportScreen> createState() => _DailyReportScreenState();
}

class _DailyReportScreenState extends ConsumerState<DailyReportScreen> {
  /// Выбранный день: 0 — сегодня, 1 — вчера, …
  int _day = 0;

  DailyReportRequest get _request {
    final now = DateTime.now();
    return (
      deviceId: widget.deviceId,
      date: isoDate(DateTime(now.year, now.month, now.day - _day)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final report = ref.watch(dailyReportProvider(_request));
    return Scaffold(
      appBar: widget.embedded ? null : AppBar(
        title: Text(widget.title),
        actions: [
          IconButton(
            tooltip: t.commonRetry,
            icon: const Icon(Icons.refresh),
            onPressed: () {
              ref.invalidate(dailyReportProvider(_request));
              ref.invalidate(dayEventsProvider(_request));
            },
          ),
        ],
      ),
      body: Column(
        children: [
          _daySelector(t),
          Expanded(
            child: report.when(
              data: (r) => _content(context, t, r),
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
    String label(int offset) => switch (offset) {
          0 => t.locationDayToday,
          1 => t.locationDayYesterday,
          _ => DateFormat.MMMd().format(startToday.subtract(Duration(days: offset))),
        };
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
      child: SegmentedButton<int>(
        segments: [
          for (var d = 0; d < trackDays; d++) ButtonSegment(value: d, label: Text(label(d))),
        ],
        selected: {_day},
        onSelectionChanged: (s) => setState(() => _day = s.first),
      ),
    );
  }

  Widget _content(BuildContext context, AppLocalizations t, DailyReport r) {
    final attention = ref.watch(dayEventsProvider(_request)).whenData(
          (events) =>
              buildTimeline(events).where((e) => e.level == EventLevel.attention).length,
        );
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
      children: [
        DayOverview(report: r, attention: attention.valueOrNull),
        DayTimeline(request: _request),
        Padding(
          padding: const EdgeInsets.only(top: 24),
          child: Text(t.dailySummary, style: Theme.of(context).textTheme.titleMedium),
        ),
        ..._summary(t, r),
      ],
    );
  }

  List<Widget> _summary(AppLocalizations t, DailyReport r) => [
          _screenTime(t, r),
          _blocked(t, r),
          if (r.limitsReached.isNotEmpty) _limits(t, r),
          _bypass(t, r),
          _route(t, r),
          _phone(t, r),
        ];

  Widget _screenTime(AppLocalizations t, DailyReport r) => _Section(
        icon: Icons.hourglass_bottom_outlined,
        title: t.dailyScreenTime,
        children: r.topApps.isEmpty
            ? [Text(t.dailyNoScreenTime)]
            : [
                Text(t.usageTotal(formatUsage(t, r.screenTotal))),
                for (final app in r.topApps) _row(app.label, formatUsage(t, app.foreground)),
                if (r.newApps.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(t.dailyNewApps, style: const TextStyle(fontWeight: FontWeight.w600)),
                  for (final app in r.newApps) _row(app.label, formatUsage(t, app.foreground)),
                ],
              ],
      );

  Widget _blocked(AppLocalizations t, DailyReport r) {
    String name(BlockedCategory c) => switch (c) {
          BlockedCategory.sites => t.dailyBlockedSites,
          BlockedCategory.gambling => t.dailyBlockedGambling,
          BlockedCategory.search => t.dailyBlockedSearch,
          BlockedCategory.images => t.dailyBlockedImages,
          BlockedCategory.messenger => t.dailyBlockedMessenger,
        };
    return _Section(
      icon: Icons.block,
      title: t.dailyBlocked,
      children: r.blockedTotal == 0
          ? [Text(t.dailyNothingBlocked)]
          : [
              for (final c in BlockedCategory.values)
                if ((r.blocked[c] ?? 0) > 0) _row(name(c), '${r.blocked[c]}'),
              if (r.topSites.isNotEmpty) ...[
                const SizedBox(height: 8),
                for (final site in r.topSites) Text(site, style: const TextStyle(fontSize: 13)),
              ],
            ],
    );
  }

  Widget _limits(AppLocalizations t, DailyReport r) => _Section(
        icon: Icons.timer_outlined,
        title: t.dailyLimits,
        children: [
          for (final hit in r.limitsReached)
            _row(hit.label ?? hit.packageName, t.dailyTimes(hit.count)),
        ],
      );

  Widget _bypass(AppLocalizations t, DailyReport r) => _Section(
        icon: Icons.gpp_maybe_outlined,
        title: t.dailyBypass,
        warn: r.bypassAttempts.isNotEmpty,
        children: r.bypassAttempts.isEmpty
            ? [Text(t.dailyNoBypass)]
            : [
                for (final e in r.bypassAttempts)
                  _row(
                    eventLabel(
                      t,
                      DeviceEvent(type: e.type, occurredAt: e.lastAt, receivedAt: e.lastAt),
                    ),
                    t.dailyTimes(e.count),
                  ),
              ],
      );

  Widget _route(AppLocalizations t, DailyReport r) {
    final route = r.location;
    final hm = DateFormat.Hm();
    return _Section(
      icon: Icons.map_outlined,
      title: t.dailyRoute,
      children: route == null
          ? [Text(t.dailyNoRoute)]
          : [
              Text(
                t.dailyRouteSummary(
                  route.points,
                  (route.distanceMeters / 1000).toStringAsFixed(1),
                ),
              ),
              Text(
                t.dailyRouteTime(
                  hm.format(route.firstAt.toLocal()),
                  hm.format(route.lastAt.toLocal()),
                ),
              ),
            ],
    );
  }

  Widget _phone(AppLocalizations t, DailyReport r) => _Section(
        icon: Icons.smartphone,
        title: t.dailyPhone,
        children: [
          _row(t.dailyPhoneBoots, '${r.boots}'),
          _row(t.dailyPhoneShutdowns, '${r.shutdowns}'),
          if (r.protectionDisabled > 0)
            _row(t.dailyProtectionDisabled, '${r.protectionDisabled}'),
        ],
      );

  Widget _row(String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(
          children: [
            Expanded(child: Text(label)),
            const SizedBox(width: 12),
            Text(value),
          ],
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
                onPressed: () => ref.invalidate(dailyReportProvider(_request)),
                child: Text(t.commonRetry),
              ),
            ],
          ),
        ),
      );
}

/// Карточка раздела отчёта; [warn] — подсветить, если есть на что обратить внимание.
class _Section extends StatelessWidget {
  const _Section({
    required this.icon,
    required this.title,
    required this.children,
    this.warn = false,
  });

  final IconData icon;
  final String title;
  final List<Widget> children;
  final bool warn;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      margin: const EdgeInsets.only(top: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, color: warn ? scheme.error : scheme.primary),
                const SizedBox(width: 8),
                Text(title, style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: 8),
            ...children,
          ],
        ),
      ),
    );
  }
}
