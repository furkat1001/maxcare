import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/device_repository.dart';
import 'package:parent/children/event_label.dart';
import 'package:parent/l10n/app_localizations.dart';

DeviceEvent _event(String type) => DeviceEvent(
  type: type,
  occurredAt: DateTime.utc(2026, 10, 19, 9),
  receivedAt: DateTime.utc(2026, 10, 19, 9),
);

void main() {
  test('location events have their own text in every language', () {
    for (final locale in AppLocalizations.supportedLocales) {
      final t = lookupAppLocalizations(locale);
      for (final type in ['location_off', 'location_denied', 'location_on']) {
        expect(
          eventLabel(t, _event(type)),
          isNot(type),
          reason: '$locale $type',
        );
      }
    }
  });

  test('unknown type falls back to its code', () {
    final t = lookupAppLocalizations(const Locale('ru'));
    expect(eventLabel(t, _event('something_new')), 'something_new');
  });
}
