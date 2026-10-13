import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/api_exception.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';

/// Экран своих стоп-слов родителя: список + добавление + удаление.
class FilterWordsScreen extends ConsumerStatefulWidget {
  const FilterWordsScreen({super.key});

  @override
  ConsumerState<FilterWordsScreen> createState() => _FilterWordsScreenState();
}

class _FilterWordsScreenState extends ConsumerState<FilterWordsScreen> {
  final _word = TextEditingController();
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
      await ref.read(filterWordsRepositoryProvider).add(word);
      _word.clear();
      ref.invalidate(filterWordsProvider);
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
      await ref.read(filterWordsRepositoryProvider).remove(word);
      ref.invalidate(filterWordsProvider);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = errorText(AppLocalizations.of(context), e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final words = ref.watch(filterWordsProvider);
    return Scaffold(
      appBar: AppBar(title: Text(t.filterWordsTitle)),
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(t.filterWordsBody),
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
