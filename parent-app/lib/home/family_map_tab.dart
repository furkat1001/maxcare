import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:latlong2/latlong.dart';

import '../children/device_repository.dart';
import '../children/location_repository.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'child_look.dart';
import 'child_screen.dart';

/// «Карта»: последняя точка каждого ребёнка на одной карте, у каждого свой цвет.
/// Нажатие на ребёнка внизу ведёт к нему камеру; на метку — открывает его маршрут.
class FamilyMapTab extends ConsumerStatefulWidget {
  const FamilyMapTab({super.key});

  @override
  ConsumerState<FamilyMapTab> createState() => _FamilyMapTabState();
}

class _FamilyMapTabState extends ConsumerState<FamilyMapTab> {
  final _map = MapController();

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final devices = ref.watch(devicesProvider).valueOrNull ?? const <Device>[];
    final points = ref.watch(familyLocationsProvider);
    return points.when(
      data: (byId) {
        final placed = [
          for (final d in devices)
            if (byId[d.id] case final DeviceLocation p) (device: d, point: p),
        ];
        if (placed.isEmpty) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(t.mapEmpty, textAlign: TextAlign.center),
            ),
          );
        }
        return _content(context, t, devices, placed);
      },
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(errorText(t, error), textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton(
              onPressed: () => ref.invalidate(familyLocationsProvider),
              child: Text(t.commonRetry),
            ),
          ],
        ),
      ),
    );
  }

  Widget _content(
    BuildContext context,
    AppLocalizations t,
    List<Device> devices,
    List<({Device device, DeviceLocation point})> placed,
  ) {
    LatLng at(DeviceLocation p) => LatLng(p.latitude, p.longitude);
    final coordinates = placed.map((e) => at(e.point)).toList();
    return Stack(
      children: [
        FlutterMap(
          mapController: _map,
          options: MapOptions(
            // Камера сразу охватывает всех детей; одного — показывает крупно.
            initialCameraFit: CameraFit.coordinates(
              coordinates: coordinates,
              padding: const EdgeInsets.fromLTRB(56, 56, 56, 160),
              maxZoom: 16,
            ),
          ),
          children: [
            TileLayer(
              urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              // Политика тайлов OSM требует указывать приложение.
              userAgentPackageName: 'uz.maxcare.parent',
            ),
            MarkerLayer(
              markers: [
                for (final e in placed)
                  Marker(
                    point: at(e.point),
                    width: 48,
                    height: 48,
                    child: _Pin(
                      look: lookOf(devices, e.device.id),
                      initial: initialOf(e.device),
                      label: e.device.title,
                      onTap: () => _openChild(e.device),
                    ),
                  ),
              ],
            ),
          ],
        ),
        Positioned(
          left: 8,
          top: 8,
          child: Container(
            color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.8),
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            child: Text(t.locationAttribution, style: Theme.of(context).textTheme.labelSmall),
          ),
        ),
        Positioned(
          left: 0,
          right: 0,
          bottom: 12,
          child: SizedBox(
            height: 76,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: [
                for (final d in devices)
                  _ChildChip(
                    device: d,
                    look: lookOf(devices, d.id),
                    point: placed.where((e) => e.device.id == d.id).firstOrNull?.point,
                    onTap: (p) => _map.move(at(p), 16),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  void _openChild(Device device) => Navigator.of(context).push(
        MaterialPageRoute<void>(builder: (_) => ChildScreen(deviceId: device.id, initialTab: 1)),
      );
}

/// Метка ребёнка: кружок его цвета с инициалом и белой обводкой, видна на любой карте.
class _Pin extends StatelessWidget {
  const _Pin({required this.look, required this.initial, required this.label, required this.onTap});

  final ChildLook look;
  final String initial;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Semantics(
        button: true,
        label: label,
        child: GestureDetector(
          onTap: onTap,
          child: Container(
            decoration: BoxDecoration(
              color: look.dot,
              shape: BoxShape.circle,
              border: Border.all(color: Colors.white, width: 3),
              boxShadow: const [
                BoxShadow(color: Color(0x40000000), blurRadius: 8, offset: Offset(0, 3)),
              ],
            ),
            alignment: Alignment.center,
            child: Text(
              initial,
              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 18),
            ),
          ),
        ),
      );
}

/// Карточка ребёнка под картой: цвет его метки, имя и время последней точки.
class _ChildChip extends StatelessWidget {
  const _ChildChip({
    required this.device,
    required this.look,
    required this.point,
    required this.onTap,
  });

  final Device device;
  final ChildLook look;
  final DeviceLocation? point;
  final void Function(DeviceLocation) onTap;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final p = point;
    final when = p == null
        ? t.mapNoLocation
        : t.mapPointAt(DateFormat.Hm().format(p.recordedAt.toLocal()));
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: Material(
        color: theme.colorScheme.surface,
        elevation: 3,
        borderRadius: BorderRadius.circular(20),
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: p == null ? null : () => onTap(p),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(10, 10, 16, 10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 18,
                  height: 18,
                  decoration: BoxDecoration(color: look.dot, shape: BoxShape.circle),
                ),
                const SizedBox(width: 10),
                Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(device.title, style: const TextStyle(fontWeight: FontWeight.w700)),
                    Text(when, style: theme.textTheme.bodySmall),
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
