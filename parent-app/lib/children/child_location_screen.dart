import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:latlong2/latlong.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'location_repository.dart';

/// Карта «где был ребёнок»: маршрут по дням за последние [trackDays] суток.
class ChildLocationScreen extends ConsumerStatefulWidget {
  const ChildLocationScreen({required this.deviceId, required this.title, super.key});

  final String deviceId;
  final String title;

  @override
  ConsumerState<ChildLocationScreen> createState() => _ChildLocationScreenState();
}

class _ChildLocationScreenState extends ConsumerState<ChildLocationScreen> {
  final _map = MapController();

  /// Выбранный день: 0 — сегодня, 1 — вчера, …
  int _day = 0;

  /// День, на котором уже отцентрирована камера (чтобы не сбрасывать панораму).
  int? _centeredDay;

  @override
  void dispose() {
    _map.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final track = ref.watch(deviceTrackProvider(widget.deviceId));
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: [
          IconButton(
            tooltip: t.commonRetry,
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(deviceTrackProvider(widget.deviceId)),
          ),
        ],
      ),
      body: track.when(
        data: (points) => points.isEmpty ? _empty(t) : _content(context, t, points),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _error(context, t, ref, error),
      ),
    );
  }

  Widget _content(BuildContext context, AppLocalizations t, List<DeviceLocation> points) {
    // Раскладываем точки по локальным суткам: ключ — смещение в днях от сегодня.
    final now = DateTime.now();
    final startToday = DateTime(now.year, now.month, now.day);
    final byDay = <int, List<DeviceLocation>>{};
    for (final p in points) {
      final local = p.recordedAt.toLocal();
      final offset = startToday.difference(DateTime(local.year, local.month, local.day)).inDays;
      if (offset >= 0 && offset < trackDays) {
        byDay.putIfAbsent(offset, () => []).add(p);
      }
    }
    // На первом показе выбираем ближайший день, где есть точки.
    if (!byDay.containsKey(_day) && byDay.isNotEmpty) {
      _day = byDay.keys.reduce((a, b) => a < b ? a : b);
    }
    final dayPoints = byDay[_day] ?? const [];
    return Column(
      children: [
        _daySelector(context, t, startToday, byDay),
        Expanded(
          child: dayPoints.isEmpty
              ? _dayEmpty(t)
              : _map2(context, t, dayPoints),
        ),
      ],
    );
  }

  Widget _daySelector(
    BuildContext context,
    AppLocalizations t,
    DateTime startToday,
    Map<int, List<DeviceLocation>> byDay,
  ) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
      child: SegmentedButton<int>(
        segments: [
          for (var d = 0; d < trackDays; d++)
            ButtonSegment(
              value: d,
              label: Text(_dayLabel(t, startToday, d)),
              enabled: (byDay[d]?.isNotEmpty ?? false) || d == _day,
            ),
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

  Widget _map2(BuildContext context, AppLocalizations t, List<DeviceLocation> points) {
    LatLng at(DeviceLocation p) => LatLng(p.latitude, p.longitude);
    // Линия — только через точные точки: грубые (Wi‑Fi, вышки) давали ложные скачки на км.
    final route = points.where(isPrecise).map(at).toList();
    final rough = points.where((p) => !isPrecise(p)).map(at).toList();
    final current = points.last;
    final currentLatLng = at(current);
    final scheme = Theme.of(context).colorScheme;
    // При смене дня переводим камеру на последнюю точку; иначе не трогаем панораму.
    if (_centeredDay != _day) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _centeredDay = _day;
        _map.move(currentLatLng, _map.camera.zoom);
      });
    }
    return Stack(
      children: [
        FlutterMap(
          mapController: _map,
          options: MapOptions(initialCenter: currentLatLng, initialZoom: 15),
          children: [
            TileLayer(
              urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              // Политика тайлов OSM требует указывать приложение.
              userAgentPackageName: 'uz.maxcare.parent',
            ),
            // Насколько можно верить текущему положению: круг радиусом в заявленную точность.
            if (current.accuracyMeters != null)
              CircleLayer(
                circles: [
                  CircleMarker(
                    point: currentLatLng,
                    radius: current.accuracyMeters!,
                    useRadiusInMeter: true,
                    color: scheme.primary.withValues(alpha: 0.12),
                    borderColor: scheme.primary.withValues(alpha: 0.4),
                    borderStrokeWidth: 1,
                  ),
                ],
              ),
            if (route.length > 1)
              PolylineLayer(
                polylines: [
                  Polyline(points: route, strokeWidth: 4, color: scheme.primary),
                ],
              ),
            // Кружок на каждой точке: точные — яркие, грубые — бледные и без линии.
            CircleLayer(
              circles: [
                for (final p in rough)
                  CircleMarker(
                    point: p,
                    radius: 4,
                    color: scheme.outline.withValues(alpha: 0.5),
                  ),
                for (final p in route)
                  CircleMarker(
                    point: p,
                    radius: 5,
                    color: scheme.primary,
                    borderColor: scheme.onPrimary,
                    borderStrokeWidth: 1.5,
                  ),
              ],
            ),
            MarkerLayer(
              markers: [
                Marker(
                  point: currentLatLng,
                  width: 40,
                  height: 40,
                  child: Icon(Icons.location_on, size: 40, color: scheme.primary),
                ),
              ],
            ),
          ],
        ),
        Positioned(left: 8, bottom: 4, child: _attribution(context, t)),
        Positioned(
          left: 12,
          right: 12,
          bottom: 16,
          child: _infoCard(context, t, current, points.length),
        ),
      ],
    );
  }

  Widget _infoCard(BuildContext context, AppLocalizations t, DeviceLocation current, int count) {
    final time = DateFormat.yMMMd().add_Hm().format(current.recordedAt.toLocal());
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(t.locationUpdated(time), style: Theme.of(context).textTheme.bodyMedium),
            if (current.accuracyMeters != null)
              Text(
                t.locationAccuracy(current.accuracyMeters!.round()),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            if (count > 1)
              Text(t.locationTrackPoints(count), style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }

  Widget _attribution(BuildContext context, AppLocalizations t) => Container(
        color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.8),
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        child: Text(t.locationAttribution, style: Theme.of(context).textTheme.labelSmall),
      );

  Widget _dayEmpty(AppLocalizations t) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(t.locationDayEmpty, textAlign: TextAlign.center),
        ),
      );

  Widget _empty(AppLocalizations t) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(t.locationNone, textAlign: TextAlign.center),
        ),
      );

  Widget _error(BuildContext context, AppLocalizations t, WidgetRef ref, Object error) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(errorText(t, error), textAlign: TextAlign.center),
              const SizedBox(height: 16),
              OutlinedButton(
                onPressed: () => ref.invalidate(deviceTrackProvider(widget.deviceId)),
                child: Text(t.commonRetry),
              ),
            ],
          ),
        ),
      );
}
