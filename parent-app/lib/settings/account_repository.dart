import '../core/api_client.dart';

/// Состояние подписки семьи; считает сервер.
/// [grace] — пробный период или оплата закончились, но ещё несколько дней всё работает;
/// [expired] — MaxCare остановлен до оплаты.
enum PlanStatus { trial, active, grace, expired }

PlanStatus _planStatus(Object? wire) => switch (wire) {
      'active' => PlanStatus.active,
      'grace' => PlanStatus.grace,
      'expired' => PlanStatus.expired,
      // Неизвестный статус от будущего сервера не считаем остановкой.
      _ => PlanStatus.trial,
    };

/// Аккаунт родителя из `GET /v1/account`. Смотри `docs/backend-api.md`.
class Account {
  Account({
    required this.email,
    required this.createdAt,
    required this.hasPassword,
    required this.providers,
    required this.planStatus,
    required this.trialEndsAt,
    required this.trialDaysLeft,
    this.paidUntil,
    this.graceEndsAt,
    this.familyId,
  });

  factory Account.fromJson(Map<String, dynamic> json) {
    final plan = json['plan'] as Map<String, dynamic>;
    return Account(
      email: json['email'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      hasPassword: json['hasPassword'] as bool,
      providers: (json['providers'] as List<dynamic>).cast<String>(),
      planStatus: _planStatus(plan['status']),
      trialEndsAt: DateTime.parse(plan['trialEndsAt'] as String),
      trialDaysLeft: (plan['trialDaysLeft'] as num).toInt(),
      paidUntil: _date(plan['paidUntil']),
      graceEndsAt: _date(plan['graceEndsAt']),
      familyId: json['familyId'] as String?,
    );
  }

  final String email;
  final DateTime createdAt;
  final bool hasPassword;

  /// Связанные входы: `google`, позже `apple`.
  final List<String> providers;
  final PlanStatus planStatus;
  final DateTime trialEndsAt;

  /// Считает сервер: так часы телефона не влияют на остаток.
  final int trialDaysLeft;

  /// До какой даты оплачено; null — не оплачивали.
  final DateTime? paidUntil;

  /// Когда MaxCare остановится, если не оплатить.
  final DateTime? graceEndsAt;

  /// ID пользователя в RevenueCat: подписка — на всю семью.
  final String? familyId;

  bool get signedInWithGoogle => providers.contains('google');
}

class AccountRepository {
  AccountRepository(this._api);

  final ApiClient _api;

  Future<Account> get() async => Account.fromJson(await _api.get('/v1/account'));

  /// После покупки: сервер сам спрашивает RevenueCat, что оплачено.
  Future<void> syncBilling() =>
      _api.post('/v1/account/billing/sync', authorized: true, body: const {});
}

DateTime? _date(Object? value) => value is String ? DateTime.parse(value) : null;
