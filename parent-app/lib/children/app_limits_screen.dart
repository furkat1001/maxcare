import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'app_limits_repository.dart';
import 'limit_rule_editor.dart';

/// «Ограничения времени»: правила для телефона ребёнка — дневной лимит и/или «сессия и перерыв»
/// на группу приложений или одно приложение. Считает и блокирует сам телефон.
class AppLimitsScreen extends ConsumerStatefulWidget {
  const AppLimitsScreen({
    required this.deviceId,
    required this.title,
    super.key,
  });

  final String deviceId;
  final String title;

  @override
  ConsumerState<AppLimitsScreen> createState() => _AppLimitsScreenState();
}

class _AppLimitsScreenState extends ConsumerState<AppLimitsScreen> {
  bool _saving = false;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final limits = ref.watch(appLimitsProvider(widget.deviceId));
    final apps =
        ref.watch(recentAppsProvider(widget.deviceId)).valueOrNull ?? const {};
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      floatingActionButton: limits.hasValue
          ? FloatingActionButton.extended(
              onPressed: _saving
                  ? null
                  : () => _edit(limits.requireValue, null),
              icon: const Icon(Icons.add),
              label: Text(t.limitsAdd),
            )
          : null,
      body: limits.when(
        data: (value) => _content(context, t, value, apps),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _error(t, error),
      ),
    );
  }

  Widget _content(
    BuildContext context,
    AppLocalizations t,
    AppLimits limits,
    Map<String, String> apps,
  ) {
    final text = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.only(bottom: 96),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(t.reportLimits, style: text.titleMedium),
        ),
        if (limits.rules.isEmpty)
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(t.limitsEmpty),
          ),
        for (final rule in limits.rules)
          ListTile(
            leading: Icon(ruleIcon(rule)),
            title: Text(ruleTitle(t, rule, apps)),
            subtitle: Text(ruleSummary(t, rule)),
            onTap: _saving ? null : () => _edit(limits, rule),
            trailing: IconButton(
              tooltip: t.limitsDelete,
              icon: const Icon(Icons.delete_outline),
              onPressed: _saving ? null : () => _save(limits.remove(rule.key)),
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Text(t.limitsNeverBlocked, style: text.bodySmall),
        ),
      ],
    );
  }

  Future<void> _edit(AppLimits limits, LimitRule? rule) async {
    final taken = limits.rules
        .map((r) => r.key)
        .where((k) => k != rule?.key)
        .toSet();
    final edited = await Navigator.of(context).push<LimitRule>(
      MaterialPageRoute(
        fullscreenDialog: true,
        builder: (_) => LimitRuleEditor(
          deviceId: widget.deviceId,
          initial: rule,
          taken: taken,
        ),
      ),
    );
    if (edited == null) return;
    // Сменили цель правила — старое убираем, иначе осталось бы два.
    final base = rule != null && rule.key != edited.key
        ? limits.remove(rule.key)
        : limits;
    await _save(base.put(edited));
  }

  Future<void> _save(AppLimits limits) async {
    final t = AppLocalizations.of(context);
    setState(() => _saving = true);
    try {
      await ref.read(appLimitsRepositoryProvider).save(widget.deviceId, limits);
      ref.invalidate(appLimitsProvider(widget.deviceId));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(errorText(t, error))));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Widget _error(AppLocalizations t, Object error) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(errorText(t, error), textAlign: TextAlign.center),
          const SizedBox(height: 16),
          OutlinedButton(
            onPressed: () => ref.invalidate(appLimitsProvider(widget.deviceId)),
            child: Text(t.commonRetry),
          ),
        ],
      ),
    ),
  );
}

String groupLabel(AppLocalizations t, LimitGroup group) => switch (group) {
  LimitGroup.video => t.limitGroupVideo,
  LimitGroup.social => t.limitGroupSocial,
  LimitGroup.game => t.limitGroupGame,
  LimitGroup.audio => t.limitGroupAudio,
};

IconData groupIcon(LimitGroup group) => switch (group) {
  LimitGroup.video => Icons.smart_display_outlined,
  LimitGroup.social => Icons.forum_outlined,
  LimitGroup.game => Icons.sports_esports_outlined,
  LimitGroup.audio => Icons.music_note_outlined,
};

IconData ruleIcon(LimitRule rule) =>
    rule.group != null ? groupIcon(rule.group!) : Icons.apps;

/// Группа или название приложения; если приложения нет в недавних — его пакет.
String ruleTitle(
  AppLocalizations t,
  LimitRule rule,
  Map<String, String> apps,
) => rule.group != null
    ? groupLabel(t, rule.group!)
    : apps[rule.packageName] ?? rule.packageName!;

/// «60 мин в день · 15 мин, затем перерыв 15 мин».
String ruleSummary(AppLocalizations t, LimitRule rule) => [
  if (rule.dailyLimitMin != null) t.limitDaily(rule.dailyLimitMin!),
  if (rule.sessionMin != null && rule.breakMin != null)
    t.limitCycle(rule.sessionMin!, rule.breakMin!),
].join(' · ');
