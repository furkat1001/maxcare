"""Поиск битых изображений в датасете (то, на чём спотыкается обучение).

Проверяет каждый файл тем же декодером, что и обучение (tf.image.decode_image),
поэтому ловит ровно те файлы, из-за которых падает train.py.

Запуск:
    python clean_data.py --data-dir data            # только показать битые
    python clean_data.py --data-dir data --delete   # показать и удалить
"""

import argparse
import glob
import os

import tensorflow as tf

from config import CLASS_NAMES

EXTENSIONS = ("*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif", "*.webp")


def iter_images(data_dir: str):
    for split in ("train", "val"):
        for cls in CLASS_NAMES:
            folder = os.path.join(data_dir, split, cls)
            for ext in EXTENSIONS:
                yield from glob.glob(os.path.join(folder, ext))


def is_broken(path: str) -> bool:
    try:
        data = tf.io.read_file(path)
        tf.image.decode_image(data, channels=3, expand_animations=False)
        return False
    except Exception:  # noqa: BLE001 — любой сбой декодера = файл негоден
        return True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True)
    parser.add_argument("--delete", action="store_true", help="Удалить битые файлы")
    args = parser.parse_args()

    broken = [path for path in iter_images(args.data_dir) if is_broken(path)]
    for path in broken:
        print("BAD", path)
    print(f"Найдено битых: {len(broken)}")

    if args.delete:
        for path in broken:
            os.remove(path)
        print(f"Удалено: {len(broken)}")
    elif broken:
        print("Запусти с --delete, чтобы удалить их.")


if __name__ == "__main__":
    main()
