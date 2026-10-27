import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'app_usage_screen.dart';
import 'daily_report_repository.dart';
import 'day_timeline.dart';
import 'event_label.dart';

/// Шапка итогов дня: кольцо экранного времени по приложениям и короткий вывод о дне.
class DayOverview extends StatelessWidget {
  const DayOverview({required this.report, required this.attention, super.key});

  final DailyReport report;

  /// Сколько пунктов ленты требуют внимания; null — лента ещё грузится.
  final int? attention;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final slices = ringSlices(report, theme.colorScheme, t.dailyRingOther);
    Widget line(Color color, String text) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(text)),
        ],
      ),
    );
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          _ScreenTimeRing(
            slices: slices,
            label: formatUsage(t, report.screenTotal),
            track: theme.colorScheme.surfaceContainerHighest,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (attention != null)
                  attention == 0
                      ? line(theme.colorScheme.primary, t.dailyAllCalm)
                      : line(attentionColor, t.dailyAttentionCount(attention!)),
                for (final s in slices)
                  line(s.color, '${s.label}: ${formatUsage(t, s.time)}'),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Янтарный для «внимания»: отличается от синего не только оттенком, но и светлотой.
const attentionColor = Color(0xFFA14A00);

class RingSlice {
  RingSlice(this.label, this.time, this.color);

  final String label;
  final Duration time;
  final Color color;
}

/// До трёх самых долгих приложений и «Другие» — остаток общего времени.
List<RingSlice> ringSlices(
  DailyReport r,
  ColorScheme scheme,
  String otherLabel,
) {
  final colors = [scheme.primary, scheme.tertiary, scheme.secondary];
  final top = r.topApps.take(3).toList();
  final slices = [
    for (var i = 0; i < top.length; i++)
      RingSlice(top[i].label, top[i].foreground, colors[i]),
  ];
  final rest =
      r.screenTotal - top.fold(Duration.zero, (sum, a) => sum + a.foreground);
  if (rest > const Duration(minutes: 1)) {
    slices.add(RingSlice(otherLabel, rest, scheme.outline));
  }
  return slices;
}

class _ScreenTimeRing extends StatelessWidget {
  const _ScreenTimeRing({
    required this.slices,
    required this.label,
    required this.track,
  });

  final List<RingSlice> slices;
  final String label;
  final Color track;

  @override
  Widget build(BuildContext context) {
    final reduce = MediaQuery.disableAnimationsOf(context);
    // Кольцо «дорисовывается» один раз при открытии дня — единственный акцент экрана.
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: reduce ? 1 : 0, end: 1),
      duration: const Duration(milliseconds: 1100),
      curve: Curves.easeOutCubic,
      builder: (context, progress, _) => SizedBox.square(
        dimension: 116,
        child: CustomPaint(
          painter: _RingPainter(slices, track, progress),
          child: Center(
            child: Text(
              label,
              textAlign: TextAlign.center,
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
          ),
        ),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  _RingPainter(this.slices, this.track, this.progress);

  final List<RingSlice> slices;
  final Color track;
  final double progress;

  @override
  void paint(Canvas canvas, Size size) {
    const stroke = 14.0;
    final rect = (Offset.zero & size).deflate(stroke / 2);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke;
    canvas.drawArc(rect, 0, 2 * math.pi, false, paint..color = track);
    final total = slices.fold(0, (sum, s) => sum + s.time.inSeconds);
    if (total == 0) return;
    var start = -math.pi / 2;
    final available = 2 * math.pi * progress;
    var used = 0.0;
    for (final s in slices) {
      // Небольшой зазор между сегментами, чтобы соседние цвета не сливались.
      final sweep = 2 * math.pi * s.time.inSeconds / total;
      final visible = math.min(sweep, available - used);
      if (visible <= 0) break;
      canvas.drawArc(
        rect,
        start,
        math.max(visible - 0.04, 0.01),
        false,
        paint..color = s.color,
      );
      start += sweep;
      used += sweep;
    }
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.progress != progress || old.slices != slices;
}

/// Лента дня: события по порядку, фильтр «Весь день / Важное», пункт раскрывается подробностями.
class DayTimeline extends ConsumerStatefulWidget {
  const DayTimeline({required this.request, super.key});

  final DailyReportRequest request;

  @override
  ConsumerState<DayTimeline> createState() => _DayTimelineState();
}

class _DayTimelineState extends ConsumerState<DayTimeline> {
  bool _importantOnly = false;
  int? _open;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final events = ref.watch(dayEventsProvider(widget.request));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 20, bottom: 8),
          child: Text(
            t.dailyTimeline,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        Wrap(
          spacing: 8,
          children: [
            ChoiceChip(
              label: Text(t.dailyFilterAll),
              selected: !_importantOnly,
              onSelected: (_) => setState(() => _importantOnly = false),
            ),
            ChoiceChip(
              label: Text(t.dailyFilterImportant),
              selected: _importantOnly,
              onSelected: (_) => setState(() => _importantOnly = true),
            ),
          ],
        ),
        const SizedBox(height: 8),
        events.when(
          data: (list) => _list(t, buildTimeline(list)),
          loading: () => const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (error, _) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Text(errorText(t, error)),
          ),
        ),
      ],
    );
  }

  Widget _list(AppLocalizations t, List<TimelineEntry> all) {
    final shown = _importantOnly
        ? all.where((e) => e.level == EventLevel.attention).toList()
        : all;
    if (shown.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Text(
          _importantOnly ? t.dailyImportantEmpty : t.dailyTimelineEmpty,
        ),
      );
    }
    // Смена фильтра пересобирает список с короткой сменой кадра, а не скачком.
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 250),
      child: Column(
        key: ValueKey(_importantOnly),
        children: [
          for (var i = 0; i < shown.length; i++)
            _TimelineRow(
              entry: shown[i],
              first: i == 0,
              last: i == shown.length - 1,
              open: _open == shown[i].from.millisecondsSinceEpoch,
              onTap: () => setState(() {
                final id = shown[i].from.millisecondsSinceEpoch;
                _open = _open == id ? null : id;
              }),
            ),
        ],
      ),
    );
  }
}

class _TimelineRow extends StatelessWidget {
  const _TimelineRow({
    required this.entry,
    required this.first,
    required this.last,
    required this.open,
    required this.onTap,
  });

  final TimelineEntry entry;
  final bool first;
  final bool last;
  final bool open;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final hm = DateFormat.Hm();
    final attention = entry.level == EventLevel.attention;
    final dot = attention ? attentionColor : theme.colorScheme.primary;
    final line = theme.colorScheme.outlineVariant;
    final time = hm.format(entry.from.toLocal());
    final title = entry.count > 1
        ? '${eventLabel(t, entry.sample)} · ${t.dailyTimes(entry.count)}'
        : eventLabel(t, entry.sample);
    final detail = [
      if (entry.count > 1) '$time–${hm.format(entry.to.toLocal())}',
      if (entry.details.length > 1) ...entry.details,
    ];
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            width: 48,
            child: Padding(
              padding: const EdgeInsets.only(top: 14),
              child: Text(time, style: theme.textTheme.bodySmall),
            ),
          ),
          // Вертикальная нить ленты с точкой события.
          SizedBox(
            width: 20,
            child: Column(
              children: [
                Container(
                  width: 2,
                  height: 16,
                  color: first ? Colors.transparent : line,
                ),
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(color: dot, shape: BoxShape.circle),
                ),
                Expanded(
                  child: Container(
                    width: 2,
                    color: last ? Colors.transparent : line,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Material(
                color: open
                    ? theme.colorScheme.surfaceContainerHigh
                    : attention
                    ? const Color(0x1FE88D14)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(16),
                child: InkWell(
                  borderRadius: BorderRadius.circular(16),
                  onTap: detail.isEmpty ? null : onTap,
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 44),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            title,
                            style: const TextStyle(fontWeight: FontWeight.w600),
                          ),
                          AnimatedSize(
                            duration: const Duration(milliseconds: 250),
                            curve: Curves.easeOutCubic,
                            alignment: Alignment.topLeft,
                            child: open && detail.isNotEmpty
                                ? Padding(
                                    padding: const EdgeInsets.only(top: 4),
                                    child: Text(
                                      detail.join('\n'),
                                      style: theme.textTheme.bodySmall,
                                    ),
                                  )
                                : const SizedBox(width: double.infinity),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
