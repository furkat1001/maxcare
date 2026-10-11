import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/add_child_screen.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(t.homeTitle),
        actions: [
          IconButton(
            tooltip: t.homeSignOut,
            icon: const Icon(Icons.logout),
            onPressed: () => ref.read(sessionProvider.notifier).signOut(),
          ),
        ],
      ),
      // Список детей и журнал событий появятся на следующем шаге (нужен эндпоинт
      // GET /v1/devices на сервере). Пока — пустое состояние и добавление ребёнка.
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(t.homeEmpty, textAlign: TextAlign.center),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.of(context).push(
          MaterialPageRoute<void>(builder: (_) => const AddChildScreen()),
        ),
        icon: const Icon(Icons.add),
        label: Text(t.homeAddChild),
      ),
    );
  }
}
