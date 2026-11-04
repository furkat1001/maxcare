import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/daily_report_repository.dart';
import 'package:parent/children/day_timeline.dart';
import 'package:parent/children/device_repository.dart';
import 'package:parent/core/api_client.dart';
import 'package:parent/core/token_store.dart';
import 'package:parent/home/child_status.dart';

class _FakeTokenStore implements TokenStore {
  @override
  Future<String?> read() async => 'tok';
  @override
  Future<void> write(String token) async {}
  @override
  Future<void> clear() async {}
}

/// Запоминает запрошенный адрес и отвечает пустым журналом.
class _RecordingAdapter implements HttpClientAdapter {
  Uri? uri;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    uri = options.uri;
    return ResponseBody.fromString(
      jsonEncode({'lastSeenAt': null, 'events': <Object>[]}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

DeviceEvent _event(String type, String at, {String? detail}) => DeviceEvent(
      type: type,
      occurredAt: DateTime.parse(at),
      receivedAt: DateTime.parse(at),
      detail: detail,
    );

DailyReport _report({List<EventCount> bypass = const [], int disabled = 0}) => DailyReport(
      screenTotal: Duration.zero,
      topApps: const [],
      newApps: const [],
      blocked: const {},
      topSites: const [],
      limitsReached: const [],
      bypassAttempts: bypass,
      protectionDisabled: disabled,
      location: null,
      boots: 0,
      shutdowns: 0,
    );

void main() {
  group('timeline', () {
    test('events go from morning to evening', () {
      final entries = buildTimeline([
        _event('device_shutdown', '2026-10-27T17:40:00Z'),
        _event('device_booted', '2026-10-27T02:40:00Z'),
      ]);

      expect(entries.map((e) => e.type), ['device_booted', 'device_shutdown']);
    });

    test('the same event in a row is one entry with a count and every site once', () {
      final entries = buildTimeline([
        _event('site_blocked_gambling', '2026-10-27T07:30:00Z', detail: 'bet.example.com'),
        _event('site_blocked_gambling', '2026-10-27T07:31:00Z', detail: 'bet.example.com'),
        _event('site_blocked_gambling', '2026-10-27T07:45:00Z', detail: 'win.example.com'),
      ]);

      final entry = entries.single;
      expect(entry.count, 3);
      expect(entry.from, DateTime.utc(2026, 10, 27, 7, 30));
      expect(entry.to, DateTime.utc(2026, 10, 27, 7, 45));
      expect(entry.details, ['bet.example.com', 'win.example.com']);
      // Сайтов два — в подписи ни один, оба видны в подробностях.
      expect(entry.sample.detail, isNull);
    });

    test('a long pause or another event in between starts a new entry', () {
      final entries = buildTimeline([
        _event('search_blocked', '2026-10-27T07:00:00Z'),
        _event('search_blocked', '2026-10-27T08:00:00Z'),
        _event('vpn_off', '2026-10-27T08:05:00Z'),
        _event('search_blocked', '2026-10-27T08:10:00Z'),
      ]);

      expect(entries.map((e) => e.count), [1, 1, 1, 1]);
    });

    test('bypass and harmful content need attention, everyday events do not', () {
      expect(levelOf('vpn_off'), EventLevel.attention);
      expect(levelOf('site_blocked_gambling'), EventLevel.attention);
      expect(levelOf('device_booted'), EventLevel.info);
      expect(levelOf('app_limit_reached'), EventLevel.info);
      // Новый тип, о котором приложение ещё не знает, лучше показать как важный.
      expect(levelOf('something_new'), EventLevel.attention);
    });
  });

  group('child state on the home tab', () {
    final now = DateTime(2026, 10, 27, 12);

    test('never connected and long silence come first', () {
      expect(childState(null, _report(), now: now), ChildState.notPaired);
      expect(
        childState(now.subtract(const Duration(hours: 7)), _report(disabled: 1), now: now),
        ChildState.offline,
      );
    });

    test('bypass attempts or disabled protection need attention', () {
      final seen = now.subtract(const Duration(minutes: 5));
      final bypass = EventCount(type: 'vpn_off', count: 1, lastAt: seen);

      expect(childState(seen, _report(bypass: [bypass]), now: now), ChildState.attention);
      expect(childState(seen, _report(disabled: 1), now: now), ChildState.attention);
      expect(childState(seen, _report(), now: now), ChildState.calm);
    });

    test('a report that has not loaded does not raise an alarm', () {
      expect(
        childState(now.subtract(const Duration(minutes: 5)), null, now: now),
        ChildState.calm,
      );
    });
  });

  test('day events are asked for one local day in UTC', () async {
    final adapter = _RecordingAdapter();
    final dio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = adapter;
    final repo = DeviceRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio));

    final from = DateTime.utc(2026, 10, 26, 19);
    await repo.events('d1', from, from.add(const Duration(days: 1)));

    expect(adapter.uri!.path, '/v1/devices/d1/events');
    expect(adapter.uri!.queryParameters, {
      'from': '2026-10-26T19:00:00.000Z',
      'to': '2026-10-27T19:00:00.000Z',
    });
  });
}
