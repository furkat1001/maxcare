import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/api_exception.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'account_repository.dart';

/// Удаление аккаунта родителя со всеми детьми и данными. Подтверждение — пароль, а для
/// аккаунта без пароля (вход только через Google) — повторный выбор того же аккаунта Google.
class DeleteAccountScreen extends ConsumerStatefulWidget {
  const DeleteAccountScreen({super.key});

  @override
  ConsumerState<DeleteAccountScreen> createState() => _DeleteAccountScreenState();
}

class _DeleteAccountScreenState extends ConsumerState<DeleteAccountScreen> {
  // Без сохранения состояния: пароль не должен попасть на диск.
  final _password = TextEditingController();
  bool _hidden = true;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final account = ref.watch(accountProvider);
    return Scaffold(
      appBar: AppBar(title: Text(t.deleteAccountTitle)),
      body: account.when(
        data: (a) => _body(t, a),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(errorText(t, error))),
      ),
    );
  }

  Widget _body(AppLocalizations t, Account a) {
    final colors = Theme.of(context).colorScheme;
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Icon(Icons.warning_amber_rounded, size: 48, color: colors.error),
        const SizedBox(height: 16),
        Text(t.deleteAccountBody),
        const SizedBox(height: 12),
        // Google Play не отменяет подписку сам: иначе родитель платил бы за удалённый аккаунт.
        Text(t.deleteAccountSubscription, style: TextStyle(color: colors.error)),
        const SizedBox(height: 24),
        if (a.hasPassword) ...[
          TextField(
            controller: _password,
            obscureText: _hidden,
            autocorrect: false,
            enableSuggestions: false,
            decoration: InputDecoration(
              labelText: t.commonPassword,
              suffixIcon: IconButton(
                onPressed: () => setState(() => _hidden = !_hidden),
                icon: Icon(_hidden ? Icons.visibility : Icons.visibility_off),
              ),
            ),
            onChanged: (_) => setState(() => _error = null),
          ),
          const SizedBox(height: 16),
        ] else ...[
          Text(t.deleteAccountGoogleHint),
          const SizedBox(height: 16),
        ],
        if (_error != null) ...[
          Text(_error!, style: TextStyle(color: colors.error)),
          const SizedBox(height: 12),
        ],
        FilledButton(
          style: FilledButton.styleFrom(backgroundColor: colors.error, foregroundColor: colors.onError),
          onPressed: _busy || (a.hasPassword && _password.text.isEmpty) ? null : () => _delete(t, a),
          child: _busy
              ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(a.hasPassword ? t.deleteAccountConfirm : t.deleteAccountConfirmGoogle),
        ),
      ],
    );
  }

  Future<void> _delete(AppLocalizations t, Account a) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      String? idToken;
      if (!a.hasPassword) {
        idToken = await ref.read(googleIdTokenSourceProvider).obtainIdToken();
        if (idToken == null) return; // Родитель закрыл выбор аккаунта.
      }
      await ref.read(accountRepositoryProvider).delete(
            password: a.hasPassword ? _password.text : null,
            idToken: idToken,
          );
      if (!mounted) return;
      Navigator.of(context).popUntil((route) => route.isFirst);
      await ref.read(sessionProvider.notifier).signOut();
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _error = e.kind == ApiErrorKind.unauthorized
            ? (a.hasPassword ? t.deleteAccountWrongPassword : t.deleteAccountWrongGoogle)
            : errorText(t, e));
      }
    } catch (e) {
      if (mounted) setState(() => _error = errorText(t, e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}
