import 'package:flutter/material.dart';

/// Фирменный синий MaxCare (как в приложении ребёнка).
const _brand = Color(0xFF1E6FD9);

ThemeData maxCareTheme(Brightness brightness) => ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: _brand, brightness: brightness),
      useMaterial3: true,
    );
