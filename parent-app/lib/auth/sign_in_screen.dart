import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/api_exception.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';

/// Вход и регистрация на одном экране с переключателем.
class SignInScreen extends ConsumerStatefulWidget {
  const SignInScreen({super.key});

  @override
  ConsumerState<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends ConsumerState<SignInScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _register = false;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final t = AppLocalizations.of(context);
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final auth = ref.read(authRepositoryProvider);
      if (_register) {
        await auth.register(_email.text, _password.text);
      }
      await auth.signIn(_email.text, _password.text);
      ref.read(sessionProvider.notifier).onSignedIn();
    } catch (e) {
      if (mounted) setState(() => _error = errorText(t, e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final google = ref.watch(googleIdTokenSourceProvider);
    return Scaffold(
      appBar: AppBar(
        title: Text(_register ? t.authSignUpTitle : t.authSignInTitle),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Google — основной способ; e-mail с паролем — запасной (телефоны без сервисов Google).
                if (google.isAvailable) ...[
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _signInWithGoogle,
                    icon: const Icon(Icons.account_circle_outlined),
                    label: Text(t.authWithGoogle),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      const Expanded(child: Divider()),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        child: Text(t.authOr),
                      ),
                      const Expanded(child: Divider()),
                    ],
                  ),
                  const SizedBox(height: 16),
                ],
                TextFormField(
                  controller: _email,
                  decoration: InputDecoration(labelText: t.commonEmail),
                  keyboardType: TextInputType.emailAddress,
                  autofillHints: const [AutofillHints.email],
                  validator: (v) => _validEmail(v) ? null : t.errorInvalidEmail,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _password,
                  decoration: InputDecoration(
                    labelText: t.commonPassword,
                    helperText: _register ? t.authPasswordHint : null,
                  ),
                  obscureText: true,
                  validator: (v) =>
                      (v != null && v.length >= 8) ? null : t.errorPasswordShort,
                ),
                const SizedBox(height: 24),
                if (_error != null) ...[
                  Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  const SizedBox(height: 12),
                ],
                FilledButton(
                  onPressed: _busy ? null : _submit,
                  child: _busy
                      ? const SizedBox(
                          height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : Text(_register ? t.authSignUpAction : t.authSignInAction),
                ),
                TextButton(
                  onPressed: _busy ? null : () => setState(() => _register = !_register),
                  child: Text(_register ? t.authToSignIn : t.authToSignUp),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _signInWithGoogle() async {
    final t = AppLocalizations.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final idToken = await ref.read(googleIdTokenSourceProvider).obtainIdToken();
      if (idToken == null) return; // Отменил выбор аккаунта — не ошибка.
      await ref.read(authRepositoryProvider).signInWithGoogle(idToken);
      ref.read(sessionProvider.notifier).onSignedIn();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = errorText(t, e));
    } catch (_) {
      // Ошибки самого Google Sign-In (настройка, сервисы Google на телефоне).
      if (mounted) setState(() => _error = t.authGoogleFailed);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  bool _validEmail(String? v) {
    if (v == null) return false;
    return RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v.trim());
  }
}
