import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'app_limits_repository.dart';
import 'app_limits_screen.dart';
import 'app_usage_screen.dart';

/// Готовый вариант по возрасту (решение от 2026-10-18, `docs/roadmap.md`).
class LimitPreset {
  const LimitPreset(
    this.ages, {
    this.dailyLimitMin,
    this.sessionMin,
    this.breakMin,
  });

  final String ages;
  final int? dailyLimitMin;
  final int? sessionMin;
  final int? breakMin;
}

const limitPresets = [
  LimitPreset('6–9', sessionMin: 15, breakMin: 15),
  LimitPreset('10–12', sessionMin: 30, breakMin: 15),
  // Подросткам — только дневной лимит: жёсткий цикл у них чаще даёт обратный эффект.
  LimitPreset('13–16', dailyLimitMin: 120),
];

const dailyOptions = [15, 30, 45, 60, 90, 120, 180, 240];
const sessionOptions = [10, 15, 20, 30, 45, 60];
const breakOptions = [5, 10, 15, 20, 30, 60];

/// Редактор одного правила. Возвращает [LimitRule] через `Navigator.pop`.
/// [taken] — ключи целей, на которые правило уже есть (их выбрать нельзя).
class LimitRuleEditor extends ConsumerStatefulWidget {
  const LimitRuleEditor({
    required this.deviceId,
    required this.taken,
    this.initial,
    super.key,
  });

  final String deviceId;
  final LimitRule? initial;
  final Set<String> taken;

  @override
  ConsumerState<LimitRuleEditor> createState() => _LimitRuleEditorState();
}

class _LimitRuleEditorState extends ConsumerState<LimitRuleEditor> {
  LimitGroup? _group;
  String? _package;
  bool _daily = true;
  int _dailyMin = 60;
  bool _cycle = false;
  int _sessionMin = 15;
  int _breakMin = 15;

  @override
  void initState() {
    super.initState();
    final rule = widget.initial;
    if (rule == null) return;
    _group = rule.group;
    _package = rule.packageName;
    _daily = rule.dailyLimitMin != null;
    _dailyMin = rule.dailyLimitMin ?? _dailyMin;
    _cycle = rule.sessionMin != null && rule.breakMin != null;
    _sessionMin = rule.sessionMin ?? _sessionMin;
    _breakMin = rule.breakMin ?? _breakMin;
  }

  LimitRule? get _result {
    if (_group == null && _package == null) return null;
    if (!_daily && !_cycle) return null;
    return LimitRule(
      group: _group,
      packageName: _group == null ? _package : null,
      dailyLimitMin: _daily ? _dailyMin : null,
      sessionMin: _cycle ? _sessionMin : null,
      breakMin: _cycle ? _breakMin : null,
    );
  }

  void _apply(LimitPreset preset) => setState(() {
    _daily = preset.dailyLimitMin != null;
    _dailyMin = preset.dailyLimitMin ?? _dailyMin;
    _cycle = preset.sessionMin != null;
    _sessionMin = preset.sessionMin ?? _sessionMin;
    _breakMin = preset.breakMin ?? _breakMin;
  });

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final text = Theme.of(context).textTheme;
    final result = _result;
    return Scaffold(
      appBar: AppBar(
        title: Text(t.limitEditTitle),
        actions: [
          TextButton(
            onPressed: result == null
                ? null
                : () => Navigator.of(context).pop(result),
            child: Text(t.limitsSave),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          _header(t.limitWhat, text),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final group in LimitGroup.values)
                  ChoiceChip(
                    avatar: Icon(groupIcon(group), size: 18),
                    label: Text(groupLabel(t, group)),
                    selected: _group == group,
                    onSelected: widget.taken.contains('group:${group.wire}')
                        ? null
                        : (_) => setState(() {
                            _group = group;
                            _package = null;
                          }),
                  ),
              ],
            ),
          ),
          _header(t.limitOneApp, text),
          _apps(t, text),
          _header(t.limitPresets, text),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Wrap(
              spacing: 8,
              children: [
                for (final preset in limitPresets)
                  ActionChip(
                    label: Text(t.limitPresetAge(preset.ages)),
                    onPressed: () => _apply(preset),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          SwitchListTile(
            title: Text(t.limitDailyToggle),
            subtitle: Text(t.limitDailyHint),
            value: _daily,
            onChanged: (v) => setState(() => _daily = v),
          ),
          if (_daily)
            _minutes(
              t,
              t.limitDailyToggle,
              _dailyMin,
              dailyOptions,
              (v) => _dailyMin = v,
            ),
          SwitchListTile(
            title: Text(t.limitCycleToggle),
            subtitle: Text(t.limitCycleHint),
            value: _cycle,
            onChanged: (v) => setState(() => _cycle = v),
          ),
          if (_cycle) ...[
            _minutes(
              t,
              t.limitSessionLabel,
              _sessionMin,
              sessionOptions,
              (v) => _sessionMin = v,
            ),
            _minutes(
              t,
              t.limitBreakLabel,
              _breakMin,
              breakOptions,
              (v) => _breakMin = v,
            ),
          ],
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
            child: Text(
              '${t.limitWarningHint}\n${t.limitGameHint}',
              style: text.bodySmall,
            ),
          ),
        ],
      ),
    );
  }

  Widget _header(String title, TextTheme text) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
    child: Text(title, style: text.titleSmall),
  );

  Widget _apps(AppLocalizations t, TextTheme text) {
    final apps = ref.watch(recentAppsProvider(widget.deviceId));
    return apps.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(16),
        child: LinearProgressIndicator(),
      ),
      // Без списка приложений правило на группу всё равно можно задать.
      error: (_, _) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Text(t.limitNoApps, style: text.bodySmall),
      ),
      data: (list) {
        // Редактируемое приложение могло выпасть из последних дней — оставляем его в списке.
        final entries = {
          if (_package != null && !list.containsKey(_package))
            _package!: _package!,
          ...list,
        };
        if (entries.isEmpty) {
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(t.limitNoApps, style: text.bodySmall),
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(t.limitAppsHint, style: text.bodySmall),
            ),
            for (final entry in entries.entries)
              ListTile(
                title: Text(entry.value),
                selected: _group == null && _package == entry.key,
                trailing: _group == null && _package == entry.key
                    ? const Icon(Icons.check)
                    : null,
                enabled: !widget.taken.contains('app:${entry.key}'),
                onTap: () => setState(() {
                  _package = entry.key;
                  _group = null;
                }),
              ),
          ],
        );
      },
    );
  }

  Widget _minutes(
    AppLocalizations t,
    String label,
    int value,
    List<int> options,
    void Function(int) onChanged,
  ) => ListTile(
    contentPadding: const EdgeInsets.fromLTRB(32, 0, 16, 0),
    title: Text(label),
    trailing: DropdownButton<int>(
      value: options.contains(value) ? value : null,
      hint: Text(formatUsage(t, Duration(minutes: value))),
      items: [
        for (final m in options)
          DropdownMenuItem(
            value: m,
            child: Text(formatUsage(t, Duration(minutes: m))),
          ),
      ],
      onChanged: (v) {
        if (v != null) setState(() => onChanged(v));
      },
    ),
  );
}
