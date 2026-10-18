import '../core/api_client.dart';

/// Код привязки нового телефона ребёнка.
class PairingCode {
  PairingCode({required this.code, required this.expiresAt});

  final String code;
  final DateTime expiresAt;
}

class PairingRepository {
  PairingRepository(this._api);

  final ApiClient _api;

  Future<PairingCode> createCode({String? childName}) async {
    final response = await _api.post(
      '/v1/pairing-codes',
      authorized: true,
      body: {if (childName != null && childName.isNotEmpty) 'childName': childName},
    );
    return PairingCode(
      code: response['code'] as String,
      expiresAt: DateTime.parse(response['expiresAt'] as String),
    );
  }
}
