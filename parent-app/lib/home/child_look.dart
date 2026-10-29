import 'package:flutter/material.dart';

import '../children/device_repository.dart';
import 'child_status.dart';

/// Свой цвет у каждого ребёнка — одинаковый на карточке, в ленте событий и на карте,
/// чтобы с первого взгляда было понятно, о ком речь. Цвета не совпадают с цветами
/// статуса (синий — спокойно, янтарный — внимание), и их различает не только оттенок.
class ChildLook {
  const ChildLook({required this.dot, required this.avatar, required this.ink});

  /// Точка на карте и рамка выбранного ребёнка.
  final Color dot;

  /// Фон аватара и текст инициала на нём (контраст ≥ 4,5:1).
  final Color avatar;
  final Color ink;
}

const _palette = [
  ChildLook(dot: Color(0xFF6A4BC4), avatar: Color(0xFFE6DEFA), ink: Color(0xFF3E2A80)),
  ChildLook(dot: Color(0xFFC2306A), avatar: Color(0xFFF9D3E2), ink: Color(0xFF6B1E42)),
  ChildLook(dot: Color(0xFF16805C), avatar: Color(0xFFC9EEDF), ink: Color(0xFF0E4D35)),
];

/// Цвет по порядку привязки: детей не больше трёх, у каждого свой.
ChildLook lookOf(List<Device> devices, String deviceId) {
  final index = devices.indexWhere((d) => d.id == deviceId);
  return _palette[(index < 0 ? 0 : index) % _palette.length];
}

/// Первая буква имени для аватара.
String initialOf(Device device) {
  final title = device.title.trim();
  return title.isEmpty ? '?' : title.characters.first.toUpperCase();
}

/// Цвета статуса. Янтарный «внимания» тёмный, чтобы на светлом фоне текст читался.
class StatusLook {
  const StatusLook(this.ring, this.text, this.bannerBg, this.bannerInk);

  final Color ring;
  final Color text;
  final Color bannerBg;
  final Color bannerInk;

  static StatusLook of(ColorScheme scheme, ChildState state) => switch (state) {
        ChildState.attention => const StatusLook(
            Color(0xFFE88D14),
            Color(0xFFA14A00),
            Color(0xFFFFE3C4),
            Color(0xFF6B3200),
          ),
        ChildState.calm => StatusLook(
            scheme.primary,
            scheme.primary,
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
          ),
        ChildState.offline || ChildState.notPaired => StatusLook(
            scheme.outline,
            scheme.onSurfaceVariant,
            scheme.surfaceContainerHighest,
            scheme.onSurface,
          ),
      };
}

/// Квадратный аватар ребёнка с рамкой цвета статуса (или цвета ребёнка, если статуса нет).
class ChildAvatar extends StatelessWidget {
  const ChildAvatar({
    required this.look,
    required this.initial,
    this.ring,
    this.size = 48,
    super.key,
  });

  final ChildLook look;
  final String initial;
  final Color? ring;
  final double size;

  @override
  Widget build(BuildContext context) => AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeOutBack,
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: look.avatar,
          borderRadius: BorderRadius.circular(size * 0.34),
          border: Border.all(color: ring ?? look.dot, width: 3),
        ),
        alignment: Alignment.center,
        child: Text(
          initial,
          style: TextStyle(
            color: look.ink,
            fontWeight: FontWeight.w800,
            fontSize: size * 0.4,
          ),
        ),
      );
}
