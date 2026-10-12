"""Экспорт обученной модели Keras в .tflite для Android.

Проверяет, что форма входа/выхода совпадает с ожиданиями
LiteRtImageClassifier: вход (1, 224, 224, 3), выход (1, 2).

Запуск:
    python export.py --model models/nsfw.keras --out models/nsfw_model.tflite
Затем положить файл в android/ml/src/main/assets/ или раздавать с сервера.
"""

import argparse
import os

import numpy as np
import tensorflow as tf

from config import CHANNELS, INPUT_SIZE, MODEL_FILENAME


def convert(model_path: str, out_path: str, float16: bool) -> None:
    model = tf.keras.models.load_model(model_path)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if float16:
        # float16 ~вдвое меньше без заметной потери качества; инференс остаётся float.
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()

    out_dir = os.path.dirname(out_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(tflite_model)
    print(f"Wrote {out_path} ({len(tflite_model) / 1_000_000:.1f} MB)")
    verify(tflite_model)


def verify(tflite_model: bytes) -> None:
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    in_shape = list(interpreter.get_input_details()[0]["shape"])
    out_shape = list(interpreter.get_output_details()[0]["shape"])
    print(f"input shape={in_shape}  output shape={out_shape}")

    expected_in = [1, INPUT_SIZE, INPUT_SIZE, CHANNELS]
    if in_shape != expected_in:
        raise SystemExit(f"Форма входа {in_shape} != ожидаемой {expected_in} (Android не примет)")
    if out_shape[-1] != 2:
        raise SystemExit(f"Выход {out_shape} должен иметь 2 класса")

    # Прогон нулевого кадра — модель должна отвечать без ошибок.
    interpreter.set_tensor(
        interpreter.get_input_details()[0]["index"],
        np.zeros(expected_in, dtype=np.float32),
    )
    interpreter.invoke()
    print("OK: модель совместима с LiteRtImageClassifier.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default="models/nsfw.keras")
    parser.add_argument("--out", default=os.path.join("models", MODEL_FILENAME))
    parser.add_argument("--float16", action="store_true", help="Квантовать веса в float16")
    args = parser.parse_args()
    convert(args.model, args.out, args.float16)


if __name__ == "__main__":
    main()
