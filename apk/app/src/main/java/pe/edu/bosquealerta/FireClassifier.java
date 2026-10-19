package pe.edu.bosquealerta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.tensorflow.lite.Interpreter;

/** Shared model contract. Each activity owns an instance confined to its worker. */
final class FireClassifier implements AutoCloseable {
    private static final int SIZE = 96;
    private final Interpreter interpreter;
    private final ByteBuffer input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder());
    private final int[] pixels = new int[SIZE * SIZE];
    private final float[][] output = new float[1][1];

    FireClassifier(Context context) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = context.getAssets().open("bosque_alerta.tflite")) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = stream.read(chunk)) != -1) bytes.write(chunk, 0, count);
        }
        ByteBuffer model = ByteBuffer.allocateDirect(bytes.size()).order(ByteOrder.nativeOrder());
        model.put(bytes.toByteArray()).rewind();
        interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(2));
    }

    float score(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true);
        resized.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        input.rewind();
        for (int pixel : pixels) {
            input.putFloat(Color.red(pixel) / 255f);
            input.putFloat(Color.green(pixel) / 255f);
            input.putFloat(Color.blue(pixel) / 255f);
        }
        if (resized != bitmap) resized.recycle();
        input.rewind();
        interpreter.run(input, output);
        return output[0][0];
    }

    @Override public void close() { interpreter.close(); }
}
