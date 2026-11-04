import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../children/app_usage_repository.dart';
import '../children/app_usage_screen.dart';
import '../children/daily_report_repository.dart';
import '../children/device_repository.dart';
import '../children/last_seen.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'child_look.dart';
import 'child_screen.dart';
import 'child_status.dart';
import 'status_blob.dart';

/// «Семья»: карточки всех детей. Те, у кого что-то случилось, — первыми.
class FamilyTab extends ConsumerWidget {
  const FamilyTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final devices = ref.watch(devicesProvider);
    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(devicesProvider);
        ref.invalidate(dailyReportProvider);
      },
      child: devices.when(
        data: (list) => list.isEmpty ? _Empty(t: t) : _Family(devices: list),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => ListView(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 96, 24, 16),
              child: Text(errorText(t, error), textAlign: TextAlign.center),
            ),
            Center(
              child: OutlinedButton(
                onPressed: () => ref.invalidate(devicesProvider),
                child: Text(t.commonRetry),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Ребёнок с его сегодняшним состоянием — для сортировки и карточки.
typedef _Child = ({Device device, DailyReport? today, ChildState state});

class _Family extends ConsumerWidget {
  const _Family({required this.devices});

  final List<Device> devices;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final date = isoDate(DateTime.now());
    final children = <_Child>[
      for (final d in devices)
        () {
          final today =
              ref.watch(dailyReportProvider((deviceId: d.id, date: date))).valueOrNull;
          return (device: d, today: today, state: childState(d.lastSeenAt, today));
        }(),
    ];
    // Стабильная сортировка: внутри одного состояния — порядок привязки.
    final sorted = [...children]..sort((a, b) => a.state.index.compareTo(b.state.index));
    final needAttention = sorted
        .where((c) => c.state == ChildState.attention || c.state == ChildState.offline)
        .map((c) => c.device.title)
        .toList();
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      children: [
        // Плашка появляется и исчезает плавно, не сдвигая карточки рывком.
        AnimatedSize(
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOutCubic,
          child: needAttention.isEmpty
              ? const SizedBox(width: double.infinity)
              : _AttentionBanner(
                  text: t.familyAttention(needAttention.join(', ')),
                  onTap: () => ref.read(mainTabProvider.notifier).state = 2,
                ),
        ),
        for (final c in sorted)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _ChildCard(child: c, look: lookOf(devices, c.device.id)),
          ),
      ],
    );
  }
}

class _AttentionBanner extends StatelessWidget {
  const _AttentionBanner({required this.text, required this.onTap});

  final String text;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    const look = StatusLook(Color(0xFFE88D14), Color(0xFFA14A00), Color(0xFFFFE3C4), Color(0xFF6B3200));
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: look.bannerBg,
        borderRadius: BorderRadius.circular(20),
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              children: [
                Icon(Icons.warning_amber_rounded, color: look.bannerInk),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    text,
                    style: TextStyle(color: look.bannerInk, fontWeight: FontWeight.w700),
                  ),
                ),
                Icon(Icons.chevron_right, color: look.bannerInk),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ChildCard extends StatefulWidget {
  const _ChildCard({required this.child, required this.look});

  final _Child child;
  final ChildLook look;

  @override
  State<_ChildCard> createState() => _ChildCardState();
}

class _ChildCardState extends State<_ChildCard> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final c = widget.child;
    final status = StatusLook.of(theme.colorScheme, c.state);
    final title = switch (c.state) {
      ChildState.calm => t.homeStatusCalm,
      ChildState.attention => t.homeStatusAttention,
      ChildState.offline => t.homeStatusOffline,
      ChildState.notPaired => t.homeStatusNotPaired,
    };
    final today = c.today;
    final km = today?.location == null
        ? '0'
        : NumberFormat('0.0', Localizations.localeOf(context).languageCode)
            .format(today!.location!.distanceMeters / 1000);
    // Карточка слегка «проседает» под пальцем — отклик на нажатие.
    return AnimatedScale(
      scale: _pressed ? 0.97 : 1,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutBack,
      child: Material(
        color: theme.colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(24),
        child: InkWell(
          borderRadius: BorderRadius.circular(24),
          onHighlightChanged: (v) => setState(() => _pressed = v),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => ChildScreen(deviceId: c.device.id)),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                Row(
                  children: [
                    _Pulse(
                      active: c.state == ChildState.attention,
                      color: status.ring,
                      child: ChildAvatar(
                        look: widget.look,
                        initial: initialOf(c.device),
                        ring: status.ring,
                        size: 52,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            c.device.title,
                            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          Text(
                            title,
                            style: TextStyle(color: status.text, fontWeight: FontWeight.w600),
                          ),
                          Text(
                            lastSeenText(t, c.device.lastSeenAt),
                            style: theme.textTheme.bodySmall,
                          ),
                        ],
                      ),
                    ),
                    const Icon(Icons.chevron_right),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    _Metric(t.homeMetricScreen, today == null ? '—' : formatUsage(t, today.screenTotal)),
                    const SizedBox(width: 8),
                    _Metric(
                      t.homeMetricBlocked,
                      today == null ? '—' : '${today.blockedTotal}',
                      warn: (today?.blockedTotal ?? 0) > 0,
                    ),
                    const SizedBox(width: 8),
                    _Metric(t.homeMetricDistance, today == null ? '—' : t.homeDistanceKm(km)),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric(this.label, this.value, {this.warn = false});

  final String label;
  final String value;
  final bool warn;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: theme.textTheme.labelSmall, maxLines: 1),
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 300),
              child: Text(
                value,
                key: ValueKey(value),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                  color: warn ? const Color(0xFFA14A00) : null,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Мягкая расходящаяся волна вокруг аватара ребёнка, которому нужно внимание.
/// Единственное постоянное движение на экране; при «уменьшении движения» её нет.
class _Pulse extends StatefulWidget {
  const _Pulse({required this.active, required this.color, required this.child});

  final bool active;
  final Color color;
  final Widget child;

  @override
  State<_Pulse> createState() => _PulseState();
}

class _PulseState extends State<_Pulse> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 2200),
  );

  bool get _run => widget.active && !MediaQuery.disableAnimationsOf(context);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(_Pulse old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    if (_run && !_controller.isAnimating) {
      _controller.repeat();
    } else if (!_run) {
      _controller.stop();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.active) return widget.child;
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) => Stack(
        alignment: Alignment.center,
        children: [
          Transform.scale(
            scale: 1 + _controller.value * 0.5,
            child: Opacity(
              opacity: _run ? (1 - _controller.value) * 0.5 : 0,
              child: Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: widget.color,
                  borderRadius: BorderRadius.circular(18),
                ),
              ),
            ),
          ),
          child!,
        ],
      ),
      child: widget.child,
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty({required this.t});

  final AppLocalizations t;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 48, 24, 96),
      children: [
        Center(
          child: StatusBlob(
            outer: scheme.surfaceContainerHigh,
            inner: scheme.primaryContainer,
            size: 200,
            child: Icon(Icons.family_restroom, size: 56, color: scheme.onPrimaryContainer),
          ),
        ),
        const SizedBox(height: 24),
        Text(t.homeEmpty, textAlign: TextAlign.center),
      ],
    );
  }
}
