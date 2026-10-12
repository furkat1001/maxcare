"""Общие константы пайплайна.

ОБЯЗАНЫ совпадать с Android-инференсом
(`android/ml/src/main/kotlin/uz/maxcare/kids/ml/LiteRtImageClassifier.kt`):
вход 224x224x3 в диапазоне [0, 1] в порядке RGB, выход — 2 класса softmax,
индекс nsfw = 1, порог 0.75. Менять здесь и там одновременно.
"""

INPUT_SIZE = 224
CHANNELS = 3

# Порядок классов фиксируем явно, чтобы nsfw всегда был под индексом 1 —
# именно этот индекс читает Android. Папки датасета: <data>/<split>/safe и .../nsfw.
CLASS_NAMES = ["safe", "nsfw"]
NSFW_INDEX = 1

# Тот же порог, что в LiteRtImageClassifier.THRESHOLD.
DEFAULT_THRESHOLD = 0.75

# Имя файла модели — его ждёт LiteRtImageClassifier.MODEL_ASSET.
MODEL_FILENAME = "nsfw_model.tflite"
