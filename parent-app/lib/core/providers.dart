import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../auth/auth_repository.dart';
import '../children/device_repository.dart';
import '../children/filter_words_repository.dart';
import '../children/location_repository.dart';
import '../children/pairing_repository.dart';
import 'api_client.dart';
import 'token_store.dart';

final tokenStoreProvider = Provider<TokenStore>((ref) => TokenStore());

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(tokenStore: ref.watch(tokenStoreProvider)),
);

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(
    api: ref.watch(apiClientProvider),
    tokenStore: ref.watch(tokenStoreProvider),
  ),
);

final pairingRepositoryProvider = Provider<PairingRepository>(
  (ref) => PairingRepository(ref.watch(apiClientProvider)),
);

final deviceRepositoryProvider = Provider<DeviceRepository>(
  (ref) => DeviceRepository(ref.watch(apiClientProvider)),
);

final filterWordsRepositoryProvider = Provider<FilterWordsRepository>(
  (ref) => FilterWordsRepository(ref.watch(apiClientProvider)),
);

/// Слова семьи по виду (block/allow); обновляется после добавления/удаления.
final filterWordsProvider =
    FutureProvider.autoDispose.family<List<String>, WordKind>(
  (ref, kind) => ref.watch(filterWordsRepositoryProvider).list(kind),
);

/// Уровень строгости семьи.
final strictnessProvider = FutureProvider.autoDispose<String>(
  (ref) => ref.watch(filterWordsRepositoryProvider).strictness(),
);

/// Список телефонов детей. Обновляется pull-to-refresh и после привязки.
final devicesProvider = FutureProvider.autoDispose<List<Device>>(
  (ref) => ref.watch(deviceRepositoryProvider).list(),
);

/// Журнал событий одного устройства.
final deviceActivityProvider =
    FutureProvider.autoDispose.family<DeviceActivity, String>(
  (ref, deviceId) => ref.watch(deviceRepositoryProvider).activity(deviceId),
);

final locationRepositoryProvider = Provider<LocationRepository>(
  (ref) => LocationRepository(ref.watch(apiClientProvider)),
);

/// Трек устройства за последние сутки (для карты «где был ребёнок»).
final deviceTrackProvider =
    FutureProvider.autoDispose.family<List<DeviceLocation>, String>(
  (ref, deviceId) => ref.watch(locationRepositoryProvider).history(deviceId),
);

/// Состояние сессии: null — ещё проверяем, true/false — вошли/нет.
class SessionController extends StateNotifier<bool?> {
  SessionController(this._auth) : super(null) {
    _restore();
  }

  final AuthRepository _auth;

  Future<void> _restore() async {
    state = await _auth.hasSession();
  }

  void onSignedIn() => state = true;

  Future<void> signOut() async {
    await _auth.signOut();
    state = false;
  }
}

final sessionProvider = StateNotifierProvider<SessionController, bool?>(
  (ref) => SessionController(ref.watch(authRepositoryProvider)),
);
