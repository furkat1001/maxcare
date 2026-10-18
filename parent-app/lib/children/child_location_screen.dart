import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:latlong2/latlong.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'location_repository.dart';

/// Карта «где ребёнок»: последняя позиция и маршрут за сутки.
class ChildLocationScreen extends ConsumerWidget {
  const ChildLocationScreen({required this.deviceId, required this.title, super.key});

  final String deviceId;
  final String title;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final track = ref.watch(deviceTrackProvider(deviceId));
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            tooltip: t.commonRetry,
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(deviceTrackProvider(deviceId)),
          ),
        ],
      ),
      body: track.when(
        data: (points) => points.isEmpty ? _empty(t) : _map(context, t, points),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _error(context, t, ref, error),
      ),
    );
  }

  Widget _map(BuildContext context, AppLocalizations t, List<DeviceLocation> points) {
    final latLngs = points.map((p) => LatLng(p.latitude, p.longitude)).toList();
    final current = points.last;
    final currentLatLng = latLngs.last;
    final scheme = Theme.of(context).colorScheme;
    return Stack(
      children: [
        FlutterMap(
          options: MapOptions(initialCenter: currentLatLng, initialZoom: 15),
          children: [
            TileLayer(
              urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              // Политика тайлов OSM требует указывать приложение.
              userAgentPackageName: 'uz.maxcare.parent',
            ),
            if (latLngs.length > 1)
              PolylineLayer(
                polylines: [
                  Polyline(points: latLngs, strokeWidth: 4, color: scheme.primary),
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
        Positioned(
          left: 8,
          bottom: 4,
          child: _attribution(context, t),
        ),
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
              Text(t.locationTrackToday, style: Theme.of(context).textTheme.bodySmall),
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
                onPressed: () => ref.invalidate(deviceTrackProvider(deviceId)),
                child: Text(t.commonRetry),
              ),
            ],
          ),
        ),
      );
}
