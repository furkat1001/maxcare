import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/app_limits_repository.dart';
import '../children/app_usage_repository.dart';
import '../children/daily_report_repository.dart';
import '../auth/auth_repository.dart';
import '../auth/google_id_token_source.dart';
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

final googleIdTokenSourceProvider = Provider<GoogleIdTokenSource>(
  (ref) => PluginGoogleIdTokenSource(),
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

/// Трек устройства за последние 3 календарных дня (для карты «где был ребёнок»).
/// Берём с локальной полуночи позавчера, чтобы попали сегодня, вчера и позавчера.
final deviceTrackProvider =
    FutureProvider.autoDispose.family<List<DeviceLocation>, String>(
  (ref, deviceId) {
    final now = DateTime.now();
    final since = DateTime(now.year, now.month, now.day)
        .subtract(const Duration(days: trackDays - 1));
    return ref.watch(locationRepositoryProvider).history(deviceId, since: since);
  },
);

/// Сколько календарных дней трека показываем на карте.
const int trackDays = 3;

final appUsageRepositoryProvider = Provider<AppUsageRepository>(
  (ref) => AppUsageRepository(ref.watch(apiClientProvider)),
);

/// Запрос времени в приложениях: устройство и день в виде `2026-10-15`.
typedef AppUsageRequest = ({String deviceId, String date});

/// Время в приложениях за один день.
final appUsageProvider =
    FutureProvider.autoDispose.family<DayAppUsage, AppUsageRequest>(
  (ref, request) => ref
      .watch(appUsageRepositoryProvider)
      .day(request.deviceId, DateTime.parse(request.date)),
);

final dailyReportRepositoryProvider = Provider<DailyReportRepository>(
  (ref) => DailyReportRepository(ref.watch(apiClientProvider)),
);

/// Запрос итогов дня: устройство и день в виде `2026-10-23`.
typedef DailyReportRequest = ({String deviceId, String date});

/// Итоги одного дня по телефону ребёнка.
final dailyReportProvider =
    FutureProvider.autoDispose.family<DailyReport, DailyReportRequest>(
  (ref, request) => ref
      .watch(dailyReportRepositoryProvider)
      .day(request.deviceId, DateTime.parse(request.date)),
);

final appLimitsRepositoryProvider = Provider<AppLimitsRepository>(
  (ref) => AppLimitsRepository(
    ref.watch(apiClientProvider),
    ref.watch(appUsageRepositoryProvider),
  ),
);

/// Ограничения времени одного телефона.
final appLimitsProvider = FutureProvider.autoDispose.family<AppLimits, String>(
  (ref, deviceId) => ref.watch(appLimitsRepositoryProvider).get(deviceId),
);

/// Приложения ребёнка за последние [trackDays] дня — для выбора в правиле: пакет → название.
final recentAppsProvider = FutureProvider.autoDispose.family<Map<String, String>, String>(
  (ref, deviceId) =>
      ref.watch(appLimitsRepositoryProvider).recentApps(deviceId, days: trackDays),
);

/// Состояние сессии: null — ещё проверяем, true/false — вошли/нет.
class SessionController extends StateNotifier<bool?> {
  SessionController(this._auth, this._google) : super(null) {
    _restore();
  }

  final AuthRepository _auth;
  final GoogleIdTokenSource _google;

  Future<void> _restore() async {
    state = await _auth.hasSession();
  }

  void onSignedIn() => state = true;

  Future<void> signOut() async {
    await _auth.signOut();
    // Иначе следующий вход через Google молча возьмёт тот же аккаунт.
    try {
      await _google.signOut();
    } catch (_) {
      // Сессия MaxCare уже удалена — этого достаточно для выхода.
    }
    state = false;
  }
}

final sessionProvider = StateNotifierProvider<SessionController, bool?>(
  (ref) => SessionController(
    ref.watch(authRepositoryProvider),
    ref.watch(googleIdTokenSourceProvider),
  ),
);
