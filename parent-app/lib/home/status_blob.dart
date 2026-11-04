import 'dart:math' as math;

import 'package:flutter/material.dart';

/// «Дышащая» фигура статуса на главной: два слоя медленно меняют форму.
/// Это единственное постоянное движение на экране — оно говорит «наблюдение идёт».
/// При системном «уменьшении движения» фигура замирает.
class StatusBlob extends StatefulWidget {
  const StatusBlob({
    required this.outer,
    required this.inner,
    required this.child,
    this.size = 248,
    super.key,
  });

  final Color outer;
  final Color inner;
  final Widget child;
  final double size;

  @override
  State<StatusBlob> createState() => _StatusBlobState();
}

class _StatusBlobState extends State<StatusBlob>
    with SingleTickerProviderStateMixin {
  // Полный цикл формы — 12 с: достаточно медленно, чтобы не отвлекать.
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 12),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller.stop();
    } else if (!_controller.isAnimating) {
      _controller.repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Смена статуса перекрашивает фигуру плавно, а не скачком.
    return TweenAnimationBuilder<Color?>(
      tween: ColorTween(end: widget.outer),
      duration: const Duration(milliseconds: 600),
      builder: (context, outer, _) => TweenAnimationBuilder<Color?>(
        tween: ColorTween(end: widget.inner),
        duration: const Duration(milliseconds: 600),
        builder: (context, inner, _) => SizedBox.square(
          dimension: widget.size,
          child: CustomPaint(
            painter: _BlobPainter(
              _controller,
              outer ?? widget.outer,
              inner ?? widget.inner,
            ),
            child: Center(child: widget.child),
          ),
        ),
      ),
    );
  }
}

class _BlobPainter extends CustomPainter {
  _BlobPainter(this.progress, this.outer, this.inner)
    : super(repaint: progress);

  final Animation<double> progress;
  final Color outer;
  final Color inner;

  @override
  void paint(Canvas canvas, Size size) {
    final t = progress.value * 2 * math.pi;
    final center = size.center(Offset.zero);
    final radius = size.shortestSide / 2;
    canvas
      ..drawPath(blobPath(center, radius * 0.94, t), Paint()..color = outer)
      // Внутренний слой — в противофазе, чтобы края двигались не синхронно.
      ..drawPath(
        blobPath(center, radius * 0.78, -t + 1.7),
        Paint()..color = inner,
      );
  }

  @override
  bool shouldRepaint(_BlobPainter old) =>
      old.outer != outer || old.inner != inner;
}

/// Замкнутая мягкая фигура вокруг [center]: радиус колеблется суммой двух гармоник.
/// Амплитуды малы (до 7 %), поэтому текст в центре всегда помещается.
Path blobPath(Offset center, double radius, double phase) {
  const steps = 96;
  final path = Path();
  for (var i = 0; i <= steps; i++) {
    final a = i / steps * 2 * math.pi;
    final r =
        radius *
        (1 +
            0.045 * math.sin(3 * a + phase) +
            0.025 * math.sin(5 * a - 2 * phase));
    final point = center + Offset(math.cos(a) * r, math.sin(a) * r);
    if (i == 0) {
      path.moveTo(point.dx, point.dy);
    } else {
      path.lineTo(point.dx, point.dy);
    }
  }
  return path..close();
}
