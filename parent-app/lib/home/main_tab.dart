import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../children/app_limits_screen.dart';
import '../children/app_usage_repository.dart';
import '../children/app_usage_screen.dart';
import '../children/child_location_screen.dart';
import '../children/daily_report_repository.dart';
import '../children/daily_report_screen.dart';
import '../children/device_repository.dart';
import '../children/last_seen.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'child_status.dart';
import 'status_blob.dart';

/// «Главная»: один ребёнок крупно — живой статус, три цифры дня и быстрые переходы.
class MainTab extends ConsumerWidget {
  const MainTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final devices = ref.watch(devicesProvider);
    return devices.when(
      data: (list) => list.isEmpty ? _Empty(t: t) : _ChildHome(devices: list),
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => _Message(
        text: errorText(t, error),
        action: OutlinedButton(
          onPressed: () => ref.invalidate(devicesProvider),
          child: Text(t.commonRetry),
        ),
      ),
    );
  }
}

class _ChildHome extends ConsumerStatefulWidget {
  const _ChildHome({required this.devices});

  final List<Device> devices;

  @override
  ConsumerState<_ChildHome> createState() => _ChildHomeState();
}

class _ChildHomeState extends ConsumerState<_ChildHome> {
  bool _details = false;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final selectedId = ref.watch(selectedChildProvider);
    final device = widget.devices.firstWhere(
      (d) => d.id == selectedId,
      orElse: () => widget.devices.first,
    );
    final request = (deviceId: device.id, date: isoDate(DateTime.now()));
    final today = ref.watch(dailyReportProvider(request)).valueOrNull;
    final state = childState(device.lastSeenAt, today);
    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(devicesProvider);
        ref.invalidate(dailyReportProvider(request));
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
        children: [
          if (widget.devices.length > 1) _childPicker(device),
          _hero(context, t, device, state),
          AnimatedSize(
            duration: const Duration(milliseconds: 350),
            curve: Curves.easeOutCubic,
            alignment: Alignment.topCenter,
            child: _details
                ? _detailsCard(t, device, today)
                : const SizedBox(width: double.infinity),
          ),
          const SizedBox(height: 12),
          _metrics(t, today),
          const SizedBox(height: 12),
          _tiles(context, t, device),
        ],
      ),
    );
  }

  Widget _childPicker(Device selected) => SizedBox(
    height: 48,
    child: ListView(
      scrollDirection: Axis.horizontal,
      children: [
        for (final d in widget.devices)
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(
              label: Text(d.title),
              selected: d.id == selected.id,
              onSelected: (_) {
                ref.read(selectedChildProvider.notifier).state = d.id;
                setState(() => _details = false);
              },
            ),
          ),
      ],
    ),
  );

  Widget _hero(
    BuildContext context,
    AppLocalizations t,
    Device device,
    ChildState state,
  ) {
    final colors = _StateColors.of(Theme.of(context).colorScheme, state);
    final title = switch (state) {
      ChildState.calm => t.homeStatusCalm,
      ChildState.attention => t.homeStatusAttention,
      ChildState.offline => t.homeStatusOffline,
      ChildState.notPaired => t.homeStatusNotPaired,
    };
    final icon = switch (state) {
      ChildState.calm => Icons.verified_user_outlined,
      ChildState.attention => Icons.gpp_maybe_outlined,
      ChildState.offline => Icons.cloud_off_outlined,
      ChildState.notPaired => Icons.phonelink_off,
    };
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: Semantics(
        button: true,
        hint: t.homeShowDetails,
        child: GestureDetector(
          onTap: () => setState(() => _details = !_details),
          child: StatusBlob(
            outer: colors.outer,
            inner: colors.inner,
            child: Padding(
              padding: const EdgeInsets.all(40),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    device.title,
                    style: textTheme.titleMedium?.copyWith(color: colors.text),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 8),
                  Icon(icon, size: 40, color: colors.text),
                  const SizedBox(height: 6),
                  Text(
                    title,
                    textAlign: TextAlign.center,
                    style: textTheme.headlineSmall?.copyWith(
                      color: colors.text,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    lastSeenText(t, device.lastSeenAt),
                    textAlign: TextAlign.center,
                    style: textTheme.bodySmall?.copyWith(color: colors.text),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _detailsCard(AppLocalizations t, Device device, DailyReport? today) {
    Widget row(String label, String value) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(label)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
    return Card(
      margin: const EdgeInsets.only(top: 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            if (today != null) ...[
              row(
                t.dailyBypass,
                '${today.bypassAttempts.fold(0, (s, e) => s + e.count)}',
              ),
              row(t.dailyProtectionDisabled, '${today.protectionDisabled}'),
              row(t.dailyPhoneBoots, '${today.boots}'),
            ],
            row(t.homeMetricBlocked, '${today?.blockedTotal ?? 0}'),
          ],
        ),
      ),
    );
  }

  Widget _metrics(AppLocalizations t, DailyReport? today) {
    final km = today?.location == null
        ? '0'
        : NumberFormat(
            '0.0',
            Localizations.localeOf(context).languageCode,
          ).format(today!.location!.distanceMeters / 1000);
    // Углы чуть разные: три карточки читаются одной группой, а не таблицей.
    return Row(
      children: [
        Expanded(
          child: _Metric(
            label: t.homeMetricScreen,
            value: today == null ? '—' : formatUsage(t, today.screenTotal),
            radius: const BorderRadius.only(
              topLeft: Radius.circular(22),
              topRight: Radius.circular(22),
              bottomRight: Radius.circular(22),
              bottomLeft: Radius.circular(8),
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _Metric(
            label: t.homeMetricBlocked,
            value: today == null ? '—' : '${today.blockedTotal}',
            radius: BorderRadius.circular(22),
            warn: (today?.blockedTotal ?? 0) > 0,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _Metric(
            label: t.homeMetricDistance,
            value: today == null ? '—' : t.homeDistanceKm(km),
            radius: const BorderRadius.only(
              topLeft: Radius.circular(22),
              topRight: Radius.circular(22),
              bottomLeft: Radius.circular(22),
              bottomRight: Radius.circular(8),
            ),
          ),
        ),
      ],
    );
  }

  Widget _tiles(BuildContext context, AppLocalizations t, Device device) {
    final scheme = Theme.of(context).colorScheme;
    void open(Widget screen) => Navigator.of(
      context,
    ).push(MaterialPageRoute<void>(builder: (_) => screen));
    return Column(
      children: [
        _Tile(
          title: t.reportDaily,
          subtitle: t.homeDailyHint,
          icon: Icons.insights_outlined,
          background: scheme.primary,
          foreground: scheme.onPrimary,
          radius: const BorderRadius.only(
            topLeft: Radius.circular(28),
            topRight: Radius.circular(28),
            bottomRight: Radius.circular(28),
            bottomLeft: Radius.circular(10),
          ),
          onTap: () =>
              open(DailyReportScreen(deviceId: device.id, title: device.title)),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: _Tile(
                title: t.reportRoute,
                icon: Icons.map_outlined,
                background: scheme.primaryContainer,
                foreground: scheme.onPrimaryContainer,
                radius: BorderRadius.circular(24),
                onTap: () => open(
                  ChildLocationScreen(deviceId: device.id, title: device.title),
                ),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _Tile(
                title: t.reportLimits,
                icon: Icons.timer_outlined,
                background: scheme.tertiaryContainer,
                foreground: scheme.onTertiaryContainer,
                radius: const BorderRadius.only(
                  topLeft: Radius.circular(24),
                  topRight: Radius.circular(10),
                  bottomLeft: Radius.circular(24),
                  bottomRight: Radius.circular(24),
                ),
                onTap: () => open(
                  AppLimitsScreen(deviceId: device.id, title: device.title),
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// Цвета фигуры статуса. Янтарный «внимания» — тёмный, чтобы белый текст читался (≥ 4,5:1).
class _StateColors {
  const _StateColors(this.outer, this.inner, this.text);

  factory _StateColors.of(ColorScheme scheme, ChildState state) =>
      switch (state) {
        ChildState.calm => _StateColors(
          scheme.primaryContainer,
          scheme.primary,
          scheme.onPrimary,
        ),
        ChildState.attention => const _StateColors(
          Color(0xFFFFDDB8),
          Color(0xFFA14A00),
          Colors.white,
        ),
        ChildState.offline || ChildState.notPaired => _StateColors(
          scheme.surfaceContainerHigh,
          scheme.surfaceContainerHighest,
          scheme.onSurface,
        ),
      };

  final Color outer;
  final Color inner;
  final Color text;
}

class _Metric extends StatelessWidget {
  const _Metric({
    required this.label,
    required this.value,
    required this.radius,
    this.warn = false,
  });

  final String label;
  final String value;
  final BorderRadius radius;
  final bool warn;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 14),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: radius,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: theme.textTheme.labelMedium, maxLines: 1),
          const SizedBox(height: 4),
          // Новое значение после обновления сменяет старое плавно, а не мигает.
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 300),
            child: Text(
              value,
              key: ValueKey(value),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w800,
                color: warn ? const Color(0xFFA14A00) : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Плитка перехода. При нажатии слегка «проседает» с пружинкой — отклик на палец.
class _Tile extends StatefulWidget {
  const _Tile({
    required this.title,
    required this.icon,
    required this.background,
    required this.foreground,
    required this.radius,
    required this.onTap,
    this.subtitle,
  });

  final String title;
  final String? subtitle;
  final IconData icon;
  final Color background;
  final Color foreground;
  final BorderRadius radius;
  final VoidCallback onTap;

  @override
  State<_Tile> createState() => _TileState();
}

class _TileState extends State<_Tile> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return AnimatedScale(
      scale: _pressed ? 0.96 : 1,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutBack,
      child: Material(
        color: widget.background,
        borderRadius: widget.radius,
        child: InkWell(
          borderRadius: widget.radius,
          onTap: widget.onTap,
          onHighlightChanged: (v) => setState(() => _pressed = v),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 72),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
              child: Row(
                children: [
                  Icon(widget.icon, color: widget.foreground),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          widget.title,
                          style: textTheme.titleMedium?.copyWith(
                            color: widget.foreground,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        if (widget.subtitle != null)
                          Text(
                            widget.subtitle!,
                            style: textTheme.bodySmall?.copyWith(
                              color: widget.foreground,
                            ),
                          ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
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
            child: Icon(
              Icons.family_restroom,
              size: 56,
              color: scheme.onPrimaryContainer,
            ),
          ),
        ),
        const SizedBox(height: 24),
        Text(t.homeEmpty, textAlign: TextAlign.center),
      ],
    );
  }
}

class _Message extends StatelessWidget {
  const _Message({required this.text, required this.action});

  final String text;
  final Widget action;

  @override
  Widget build(BuildContext context) => ListView(
    children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(24, 96, 24, 16),
        child: Text(text, textAlign: TextAlign.center),
      ),
      Center(child: action),
    ],
  );
}
