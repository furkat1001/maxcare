import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../children/day_timeline.dart';
import '../children/device_repository.dart';
import '../children/event_label.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'child_look.dart';
import 'child_screen.dart';

/// Событие ленты вместе с ребёнком, с чьего телефона оно пришло.
typedef FeedItem = ({Device device, DeviceEvent event});

/// Общая лента всех детей, новые сверху; [childId] — только этот ребёнок,
/// [importantOnly] — только то, что требует внимания.
List<FeedItem> buildFeed(
  Map<Device, List<DeviceEvent>> byChild, {
  String? childId,
  bool importantOnly = false,
}) {
  final items = <FeedItem>[
    for (final entry in byChild.entries)
      if (childId == null || entry.key.id == childId)
        for (final e in entry.value)
          if (!importantOnly || levelOf(e.type) == EventLevel.attention)
            (device: entry.key, event: e),
  ];
  return items..sort((a, b) => b.event.occurredAt.compareTo(a.event.occurredAt));
}

/// «События»: что произошло у всех детей, с фильтром по ребёнку и «только важное».
class EventsTab extends ConsumerStatefulWidget {
  const EventsTab({super.key});

  @override
  ConsumerState<EventsTab> createState() => _EventsTabState();
}

class _EventsTabState extends ConsumerState<EventsTab> {
  String? _childId;
  bool _importantOnly = false;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final devices = ref.watch(devicesProvider).valueOrNull ?? const <Device>[];
    final byChild = {
      for (final d in devices) d: ref.watch(deviceActivityProvider(d.id)).valueOrNull?.events ?? const [],
    };
    final loading = devices.any((d) => ref.watch(deviceActivityProvider(d.id)).isLoading);
    final feed = buildFeed(byChild, childId: _childId, importantOnly: _importantOnly);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(deviceActivityProvider),
      child: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(child: _filters(t, devices)),
          if (feed.isEmpty)
            SliverFillRemaining(
              hasScrollBody: false,
              child: Center(
                child: loading
                    ? const CircularProgressIndicator()
                    : Padding(
                        padding: const EdgeInsets.all(24),
                        child: Text(
                          _importantOnly ? t.dailyImportantEmpty : t.eventsEmpty,
                          textAlign: TextAlign.center,
                        ),
                      ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
              sliver: SliverList.builder(
                itemCount: feed.length,
                itemBuilder: (context, i) => _FeedRow(item: feed[i], devices: devices),
              ),
            ),
        ],
      ),
    );
  }

  Widget _filters(AppLocalizations t, List<Device> devices) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        child: Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            ChoiceChip(
              label: Text(t.filterAll),
              selected: _childId == null,
              onSelected: (_) => setState(() => _childId = null),
            ),
            for (final d in devices)
              ChoiceChip(
                avatar: CircleAvatar(backgroundColor: lookOf(devices, d.id).dot, radius: 6),
                label: Text(d.title),
                selected: _childId == d.id,
                onSelected: (_) => setState(() => _childId = d.id),
              ),
            FilterChip(
              label: Text(t.eventsImportantOnly),
              selected: _importantOnly,
              onSelected: (v) => setState(() => _importantOnly = v),
            ),
          ],
        ),
      );
}

class _FeedRow extends StatelessWidget {
  const _FeedRow({required this.item, required this.devices});

  final FeedItem item;
  final List<Device> devices;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final at = item.event.occurredAt.toLocal();
    final now = DateTime.now();
    final sameDay = at.year == now.year && at.month == now.month && at.day == now.day;
    final time = sameDay ? DateFormat.Hm().format(at) : DateFormat.MMMd().add_Hm().format(at);
    final attention = levelOf(item.event.type) == EventLevel.attention;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Material(
        color: attention ? const Color(0x1FE88D14) : theme.colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(18),
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => ChildScreen(deviceId: item.device.id)),
          ),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                ChildAvatar(
                  look: lookOf(devices, item.device.id),
                  initial: initialOf(item.device),
                  size: 36,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        eventLabel(t, item.event),
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                      Text('${item.device.title}, $time', style: theme.textTheme.bodySmall),
                    ],
                  ),
                ),
                if (attention)
                  Container(
                    width: 10,
                    height: 10,
                    decoration: const BoxDecoration(color: Color(0xFFE88D14), shape: BoxShape.circle),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
