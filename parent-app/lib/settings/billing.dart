import 'package:flutter/services.dart';
import 'package:purchases_flutter/purchases_flutter.dart';

/// Публичный ключ SDK RevenueCat для Android (`goog_…`). Не секрет: он есть в любом скачанном
/// приложении. Задаётся при сборке: `--dart-define=MAXCARE_REVENUECAT_KEY=goog_…`.
const revenueCatKey = String.fromEnvironment('MAXCARE_REVENUECAT_KEY');

enum PlanPeriod { monthly, yearly }

/// Тариф из Google Play. Цена — строкой магазина, в валюте покупателя.
class StorePlan {
  const StorePlan({required this.period, required this.priceString, required this.handle});

  final PlanPeriod period;
  final String priceString;

  /// Пакет RevenueCat; снаружи не используется.
  final Object handle;
}

enum PurchaseOutcome { purchased, cancelled }

/// Оплата в этой сборке не настроена: нет ключа RevenueCat.
class BillingUnavailable implements Exception {}

/// Оплата подписки. Подписка — на семью: ID пользователя в RevenueCat — ID семьи с сервера.
/// Что оплачено, решает сервер; приложение после покупки только просит его сверить.
abstract class Billing {
  bool get available;

  /// Месячный и годовой тарифы — те, что есть в текущем предложении RevenueCat.
  Future<List<StorePlan>> plans(String familyId);

  Future<PurchaseOutcome> buy(StorePlan plan);

  /// @return true — нашлась действующая подписка.
  Future<bool> restore(String familyId);
}

class RevenueCatBilling implements Billing {
  RevenueCatBilling({String apiKey = revenueCatKey}) : _apiKey = apiKey;

  final String _apiKey;
  String? _user;

  @override
  bool get available => _apiKey.isNotEmpty;

  /// Один раз на запуск; если вошли в другой аккаунт — переключаем пользователя RevenueCat.
  Future<void> _use(String familyId) async {
    if (!available) throw BillingUnavailable();
    if (_user == null) {
      await Purchases.configure(PurchasesConfiguration(_apiKey)..appUserID = familyId);
    } else if (_user != familyId) {
      await Purchases.logIn(familyId);
    }
    _user = familyId;
  }

  @override
  Future<List<StorePlan>> plans(String familyId) async {
    await _use(familyId);
    final offering = (await Purchases.getOfferings()).current;
    return [
      if (offering?.monthly case final monthly?) _plan(PlanPeriod.monthly, monthly),
      if (offering?.annual case final annual?) _plan(PlanPeriod.yearly, annual),
    ];
  }

  StorePlan _plan(PlanPeriod period, Package package) =>
      StorePlan(period: period, priceString: package.storeProduct.priceString, handle: package);

  @override
  Future<PurchaseOutcome> buy(StorePlan plan) async {
    try {
      await Purchases.purchase(PurchaseParams.package(plan.handle as Package));
      return PurchaseOutcome.purchased;
    } on PlatformException catch (e) {
      // Родитель закрыл окно Google Play — это не ошибка.
      if (PurchasesErrorHelper.getErrorCode(e) == PurchasesErrorCode.purchaseCancelledError) {
        return PurchaseOutcome.cancelled;
      }
      rethrow;
    }
  }

  @override
  Future<bool> restore(String familyId) async {
    await _use(familyId);
    final info = await Purchases.restorePurchases();
    return info.entitlements.active.isNotEmpty;
  }
}
