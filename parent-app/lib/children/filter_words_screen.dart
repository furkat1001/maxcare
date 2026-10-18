import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/api_exception.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'filter_words_repository.dart';

/// Экран настройки фильтра: строгость, стоп-слова и разрешённые слова.
class FilterWordsScreen extends ConsumerStatefulWidget {
  const FilterWordsScreen({super.key});

  @override
  ConsumerState<FilterWordsScreen> createState() => _FilterWordsScreenState();
}

class _FilterWordsScreenState extends ConsumerState<FilterWordsScreen> {
  final _word = TextEditingController();
  WordKind _kind = WordKind.block;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _word.dispose();
    super.dispose();
  }

  Future<void> _add() async {
    final word = _word.text.trim();
    if (word.isEmpty) return;
    final t = AppLocalizations.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(filterWordsRepositoryProvider).add(word, _kind);
      _word.clear();
      ref.invalidate(filterWordsProvider(_kind));
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _error = e.code == 'limit_reached' ? t.filterWordsLimit : errorText(t, e));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _remove(String word) async {
    try {
      await ref.read(filterWordsRepositoryProvider).remove(word, _kind);
      ref.invalidate(filterWordsProvider(_kind));
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = errorText(AppLocalizations.of(context), e));
    }
  }

  Future<void> _setStrict(bool strict) async {
    final value = strict ? 'strict' : 'standard';
    await ref.read(filterWordsRepositoryProvider).setStrictness(value);
    ref.invalidate(strictnessProvider);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final words = ref.watch(filterWordsProvider(_kind));
    final strictness = ref.watch(strictnessProvider);
    return Scaffold(
      appBar: AppBar(title: Text(t.filterWordsTitle)),
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SwitchListTile(
              title: Text(t.filterStrictTitle),
              subtitle: Text(t.filterStrictBody),
              value: strictness.valueOrNull == 'strict',
              onChanged: strictness.hasValue ? (v) => _setStrict(v) : null,
            ),
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: SegmentedButton<WordKind>(
                segments: [
                  ButtonSegment(value: WordKind.block, label: Text(t.filterWordsBlockTab)),
                  ButtonSegment(value: WordKind.allow, label: Text(t.filterWordsAllowTab)),
                ],
                selected: {_kind},
                onSelectionChanged: (s) => setState(() {
                  _kind = s.first;
                  _error = null;
                }),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(_kind == WordKind.block ? t.filterWordsBody : t.filterWordsAllowBody),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _word,
                          decoration: InputDecoration(labelText: t.filterWordsHint),
                          textInputAction: TextInputAction.done,
                          onSubmitted: (_) => _busy ? null : _add(),
                        ),
                      ),
                      const SizedBox(width: 8),
                      FilledButton(
                        onPressed: _busy ? null : _add,
                        child: _busy
                            ? const SizedBox(
                                height: 20,
                                width: 20,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : Text(t.filterWordsAdd),
                      ),
                    ],
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 8),
                    Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ],
                ],
              ),
            ),
            const SizedBox(height: 8),
            const Divider(height: 1),
            Expanded(
              child: words.when(
                data: (list) => _list(t, list),
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (error, _) => Center(child: Text(errorText(t, error))),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _list(AppLocalizations t, List<String> list) {
    if (list.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(t.filterWordsEmpty, textAlign: TextAlign.center),
        ),
      );
    }
    return ListView.separated(
      itemCount: list.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (_, i) => ListTile(
        title: Text(list[i]),
        trailing: IconButton(
          icon: const Icon(Icons.delete_outline),
          tooltip: t.filterWordsRemove,
          onPressed: () => _remove(list[i]),
        ),
      ),
    );
  }
}
