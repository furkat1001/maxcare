"""Обучение классификатора наготы (safe / nsfw) переносом обучения на MobileNetV2.

Датасет (папки по классам, см. README):
    <data-dir>/train/safe/*  <data-dir>/train/nsfw/*
    <data-dir>/val/safe/*    <data-dir>/val/nsfw/*

ПРИВАТНОСТЬ: только публичные/синтетические изображения взрослых. Никогда не
использовать изображения несовершеннолетних — ни для обучения, ни для оценки.

Запуск:
    python train.py --data-dir data --epochs 10 --out models/nsfw.keras
"""

import argparse
import os

import tensorflow as tf

from config import CLASS_NAMES, INPUT_SIZE


def make_datasets(data_dir: str, batch_size: int):
    def load(split: str, shuffle: bool):
        # batch_size=None — по одной картинке, чтобы ignore_errors отбрасывал битый файл
        # поштучно, а не терял целую пачку. Батчим уже после фильтра.
        return tf.keras.utils.image_dataset_from_directory(
            os.path.join(data_dir, split),
            labels="inferred",
            label_mode="int",
            class_names=CLASS_NAMES,  # safe=0, nsfw=1
            image_size=(INPUT_SIZE, INPUT_SIZE),
            batch_size=None,
            shuffle=shuffle,
        )

    # image_dataset_from_directory отдаёт [0, 255]; модель и Android работают в [0, 1].
    to_unit = lambda x, y: (x / 255.0, y)
    autotune = tf.data.AUTOTUNE

    def pipeline(split: str, shuffle: bool):
        return (
            load(split, shuffle)
            .ignore_errors()  # защитная сетка: битый кадр пропускаем, не роняем обучение
            .map(to_unit)
            .batch(batch_size)
            .prefetch(autotune)
        )

    return pipeline("train", shuffle=True), pipeline("val", shuffle=False)


def build_model() -> tf.keras.Model:
    # Вход в домене [0, 1] RGB — ровно то, что подаёт Android.
    inputs = tf.keras.Input(shape=(INPUT_SIZE, INPUT_SIZE, 3))
    # MobileNetV2 обучался на [-1, 1]; переводим внутри модели, не на стороне Android.
    scaled = tf.keras.layers.Rescaling(scale=2.0, offset=-1.0)(inputs)
    base = tf.keras.applications.MobileNetV2(
        include_top=False,
        weights="imagenet",
        input_tensor=scaled,
        pooling="avg",
    )
    base.trainable = False
    dropped = tf.keras.layers.Dropout(0.2)(base.output)
    outputs = tf.keras.layers.Dense(len(CLASS_NAMES), activation="softmax")(dropped)
    return tf.keras.Model(inputs, outputs)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True, help="Корень датасета с train/ и val/")
    parser.add_argument("--out", default="models/nsfw.keras", help="Куда сохранить модель Keras")
    parser.add_argument("--epochs", type=int, default=10)
    parser.add_argument("--batch-size", type=int, default=32)
    args = parser.parse_args()

    train_ds, val_ds = make_datasets(args.data_dir, args.batch_size)
    model = build_model()
    model.compile(
        optimizer="adam",
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs)

    out_dir = os.path.dirname(args.out)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    model.save(args.out)
    print(f"Saved Keras model to {args.out}")


if __name__ == "__main__":
    main()
