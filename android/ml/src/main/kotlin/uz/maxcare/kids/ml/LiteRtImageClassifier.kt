package uz.maxcare.kids.ml

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.google.ai.edge.litert.support.common.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import uz.maxcare.kids.core.di.DefaultDispatcher
import uz.maxcare.kids.core.ml.ImageCategory
import uz.maxcare.kids.core.ml.ImageClassifier
import uz.maxcare.kids.core.ml.ImageVerdict

/**
 * Классификатор кадра на LiteRT. Модель — файл assets [MODEL_ASSET]; пока его нет в сборке
 * (PR 4a), [interpreter] = null и любой кадр считается безопасным — пайплайн работает вхолостую.
 *
 * Кадр анализируется только в памяти: ничего не пишется на диск, в логи, в аналитику (CLAUDE.md).
 */
@Singleton
class LiteRtImageClassifier @Inject constructor(
    @ApplicationContext context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) : ImageClassifier {

    private val interpreter: Interpreter? = loadInterpreter(context)

    override suspend fun classify(bitmap: Bitmap): ImageVerdict {
        val tflite = interpreter ?: return ImageVerdict.SAFE
        return withContext(dispatcher) { runInference(tflite, bitmap) }
    }

    private fun runInference(tflite: Interpreter, bitmap: Bitmap): ImageVerdict {
        val output = Array(1) { FloatArray(OUTPUT_CLASSES) }
        tflite.run(toInput(bitmap), output)
        // Отдаём сырой скор наготы; порог (по строгости) применяет вызывающая сторона.
        return ImageVerdict(ImageCategory.NUDITY, output[0][NSFW_INDEX])
    }

    private fun toInput(bitmap: Bitmap): ByteBuffer {
        val scaled = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
        val buffer = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        buffer.rewind()
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr RED_SHIFT) and BYTE_MASK) / MAX_PIXEL)
            buffer.putFloat(((pixel shr GREEN_SHIFT) and BYTE_MASK) / MAX_PIXEL)
            buffer.putFloat((pixel and BYTE_MASK) / MAX_PIXEL)
        }
        if (scaled !== bitmap) scaled.recycle()
        return buffer
    }

    private fun loadInterpreter(context: Context): Interpreter? = try {
        val model = FileUtil.loadMappedFile(context, MODEL_ASSET)
        Interpreter(model, Interpreter.Options().apply { numThreads = INFERENCE_THREADS })
    } catch (ignored: IOException) {
        // Модели нет в сборке — классификатор выключен, кадры считаются безопасными.
        null
    }

    private companion object {
        const val MODEL_ASSET = "nsfw_model.tflite"
        const val INPUT_SIZE = 224
        const val CHANNELS = 3
        const val BYTES_PER_FLOAT = 4
        const val OUTPUT_CLASSES = 2
        const val NSFW_INDEX = 1
        const val INFERENCE_THREADS = 2
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BYTE_MASK = 0xFF
        const val MAX_PIXEL = 255.0f
    }
}
