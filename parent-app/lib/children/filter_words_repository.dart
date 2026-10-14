import '../core/api_client.dart';

/// Вид слова: стоп-слово (block) или разрешённое (allow, отключает корень).
enum WordKind {
  block('block'),
  allow('allow');

  const WordKind(this.wire);
  final String wire;
}

/// Настройка фильтра семьи: стоп-слова, разрешённые слова и уровень строгости.
class FilterWordsRepository {
  FilterWordsRepository(this._api);

  final ApiClient _api;

  Future<List<String>> list(WordKind kind) async {
    final data = await _api.get('/v1/filter-words?kind=${kind.wire}');
    final words = data['words'];
    return words is List ? words.whereType<String>().toList() : const [];
  }

  Future<void> add(String word, WordKind kind) => _api.post(
        '/v1/filter-words?kind=${kind.wire}',
        body: {'word': word},
        authorized: true,
      );

  Future<void> remove(String word, WordKind kind) => _api.delete(
        '/v1/filter-words?word=${Uri.encodeQueryComponent(word)}&kind=${kind.wire}',
      );

  Future<String> strictness() async {
    final data = await _api.get('/v1/settings');
    return data['strictness'] as String? ?? 'standard';
  }

  Future<void> setStrictness(String value) =>
      _api.put('/v1/settings', body: {'strictness': value});
}
