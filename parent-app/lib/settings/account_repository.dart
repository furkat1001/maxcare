import '../core/api_client.dart';

/// Состояние подписки семьи. Оплаты пока нет: есть только пробный период.
enum PlanStatus { trial, expired }

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
  });

  factory Account.fromJson(Map<String, dynamic> json) {
    final plan = json['plan'] as Map<String, dynamic>;
    return Account(
      email: json['email'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      hasPassword: json['hasPassword'] as bool,
      providers: (json['providers'] as List<dynamic>).cast<String>(),
      // Неизвестный статус от будущего сервера (например, оплаченный) не считаем окончанием.
      planStatus: plan['status'] == 'expired' ? PlanStatus.expired : PlanStatus.trial,
      trialEndsAt: DateTime.parse(plan['trialEndsAt'] as String),
      trialDaysLeft: (plan['trialDaysLeft'] as num).toInt(),
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

  bool get signedInWithGoogle => providers.contains('google');
}

class AccountRepository {
  AccountRepository(this._api);

  final ApiClient _api;

  Future<Account> get() async => Account.fromJson(await _api.get('/v1/account'));
}
