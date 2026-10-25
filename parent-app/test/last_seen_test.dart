import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/last_seen.dart';
import 'package:parent/l10n/app_localizations.dart';

void main() {
  final t = lookupAppLocalizations(const Locale('ru'));
  final now = DateTime(2026, 10, 25, 9, 0);

  test('a phone asleep at night is not reported as lost', () {
    final text = lastSeenText(t, now.subtract(const Duration(hours: 2)), now: now);

    expect(text, t.deviceLastSeen('07:00'));
  });

  test('six hours of silence is reported as no connection', () {
    final text = lastSeenText(t, now.subtract(const Duration(hours: 7)), now: now);

    expect(text, startsWith(t.deviceOffline('').split(' ').first));
  });
}
