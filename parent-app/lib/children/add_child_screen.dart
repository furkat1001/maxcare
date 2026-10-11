import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'pairing_repository.dart';

class AddChildScreen extends ConsumerStatefulWidget {
  const AddChildScreen({super.key});

  @override
  ConsumerState<AddChildScreen> createState() => _AddChildScreenState();
}

class _AddChildScreenState extends ConsumerState<AddChildScreen> {
  final _name = TextEditingController();
  bool _busy = false;
  String? _error;
  PairingCode? _code;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _getCode() async {
    final t = AppLocalizations.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final code = await ref.read(pairingRepositoryProvider).createCode(childName: _name.text.trim());
      if (mounted) setState(() => _code = code);
    } catch (e) {
      if (mounted) setState(() => _error = errorText(t, e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final code = _code;
    return Scaffold(
      appBar: AppBar(title: Text(t.addChildTitle)),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: code == null ? _form(t) : _codeView(t, code),
        ),
      ),
    );
  }

  Widget _form(AppLocalizations t) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(t.addChildBody),
          const SizedBox(height: 16),
          TextField(
            controller: _name,
            decoration: InputDecoration(labelText: t.addChildNameLabel),
            textInputAction: TextInputAction.done,
          ),
          const SizedBox(height: 24),
          if (_error != null) ...[
            Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            const SizedBox(height: 12),
          ],
          FilledButton(
            onPressed: _busy ? null : _getCode,
            child: _busy
                ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : Text(t.addChildGetCode),
          ),
        ],
      );

  Widget _codeView(AppLocalizations t, PairingCode code) {
    final spaced = '${code.code.substring(0, 4)} ${code.code.substring(4)}';
    final time = DateFormat.Hm().format(code.expiresAt.toLocal());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Text(t.addChildCodeTitle, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 16),
        SelectableText(
          spaced,
          style: Theme.of(context).textTheme.displaySmall?.copyWith(
                letterSpacing: 4,
                fontWeight: FontWeight.bold,
              ),
        ),
        const SizedBox(height: 8),
        Text(t.addChildCodeExpires(time)),
        const SizedBox(height: 24),
        Text(t.addChildCodeHint, textAlign: TextAlign.center),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: () => Clipboard.setData(ClipboardData(text: code.code)),
          icon: const Icon(Icons.copy),
          label: Text(t.addChildCodeTitle),
        ),
      ],
    );
  }
}
