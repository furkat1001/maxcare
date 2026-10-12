"""Оценка модели по критериям спринта 4: пропуски и ложные срабатывания.

Считает на валидации:
  - пропуски (nsfw принят за safe) — доля и количество;
  - ложные срабатывания на 100 безопасных картинок (FP/100);
  - точность.
И перебирает пороги, чтобы выбрать рабочий (в проде — тот же порог в Android).

Запуск:
    python evaluate.py --data-dir data --model models/nsfw.keras
"""

import argparse
import os

import numpy as np
import tensorflow as tf

from config import CLASS_NAMES, DEFAULT_THRESHOLD, INPUT_SIZE, NSFW_INDEX


def load_val(data_dir: str, batch_size: int):
    ds = tf.keras.utils.image_dataset_from_directory(
        os.path.join(data_dir, "val"),
        labels="inferred",
        label_mode="int",
        class_names=CLASS_NAMES,
        image_size=(INPUT_SIZE, INPUT_SIZE),
        batch_size=None,  # поштучно, чтобы ignore_errors отбрасывал битые файлы по одному
        shuffle=False,
    )
    return ds.ignore_errors().map(lambda x, y: (x / 255.0, y)).batch(batch_size)


def collect(model: tf.keras.Model, ds) -> tuple[np.ndarray, np.ndarray]:
    labels: list[np.ndarray] = []
    scores: list[np.ndarray] = []
    for images, batch_labels in ds:
        probs = model.predict(images, verbose=0)
        scores.append(probs[:, NSFW_INDEX])
        labels.append(batch_labels.numpy())
    return np.concatenate(labels), np.concatenate(scores)


def report(labels: np.ndarray, nsfw_score: np.ndarray, threshold: float) -> None:
    predicted_nsfw = nsfw_score >= threshold
    actual_nsfw = labels == NSFW_INDEX
    actual_safe = ~actual_nsfw

    misses = int(np.sum(actual_nsfw & ~predicted_nsfw))  # nsfw принят за safe
    false_pos = int(np.sum(actual_safe & predicted_nsfw))  # safe принят за nsfw
    nsfw_total = int(np.sum(actual_nsfw))
    safe_total = int(np.sum(actual_safe))

    miss_rate = misses / nsfw_total if nsfw_total else 0.0
    fp_per_100 = 100.0 * false_pos / safe_total if safe_total else 0.0
    accuracy = float(np.mean(predicted_nsfw == actual_nsfw))

    print(f"порог={threshold:.2f}  "
          f"пропуски={misses}/{nsfw_total} ({miss_rate:.1%})  "
          f"FP/100={fp_per_100:.2f}  "
          f"точность={accuracy:.1%}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True)
    parser.add_argument("--model", default="models/nsfw.keras")
    parser.add_argument("--batch-size", type=int, default=32)
    args = parser.parse_args()

    model = tf.keras.models.load_model(args.model)
    labels, nsfw_score = collect(model, load_val(args.data_dir, args.batch_size))

    print("Перебор порогов:")
    for threshold in (0.3, 0.4, 0.5, 0.6, 0.7, 0.75, 0.8, 0.9):
        report(labels, nsfw_score, threshold)
    print("\nРабочий порог (как в Android):")
    report(labels, nsfw_score, DEFAULT_THRESHOLD)


if __name__ == "__main__":
    main()
