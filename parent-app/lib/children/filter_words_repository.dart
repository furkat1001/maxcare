import '../core/api_client.dart';

/// Свои стоп-слова семьи: родитель добавляет их к базовому словарю фильтра.
class FilterWordsRepository {
  FilterWordsRepository(this._api);

  final ApiClient _api;

  Future<List<String>> list() async {
    final data = await _api.get('/v1/filter-words');
    final words = data['words'];
    return words is List ? words.whereType<String>().toList() : const [];
  }

  Future<void> add(String word) =>
      _api.post('/v1/filter-words', body: {'word': word}, authorized: true);

  Future<void> remove(String word) =>
      _api.delete('/v1/filter-words?word=${Uri.encodeQueryComponent(word)}');
}
