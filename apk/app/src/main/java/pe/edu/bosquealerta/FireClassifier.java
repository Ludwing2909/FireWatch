package pe.edu.bosquealerta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

/** CNN multietiqueta: puntajes de fuego y humo, sin estimar gravedad. */
final class FireClassifier implements AutoCloseable {
    private static final int SIZE = 160;
    private final Interpreter interpreter;
    private final ByteBuffer modelBuffer;
    private final ByteBuffer input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder());
    private final int[] pixels = new int[SIZE * SIZE];
    private final float[][] output = new float[1][2];
    private final float fireThreshold, smokeThreshold, uncertainMargin;

    static final class Analysis {
        final float fireScore, smokeScore;
        final boolean fire, smoke, localized, uncertain;
        final int fireRegions;
        Analysis(float f, float s, int regions, float ft, float st, float margin) {
            fireScore = f; smokeScore = s; fireRegions = regions;
            fire = f >= ft || regions >= 2;
            smoke = s >= st;
            localized = fire && regions > 0 && regions <= 2;
            uncertain = !fire && !smoke && (f >= ft - margin || s >= st - margin);
        }
        String title() {
            if (localized) return smoke ? "Fuego localizado y posible humo" : "Indicios localizados de fuego";
            if (fire) return smoke ? "Indicios de fuego y humo" : "Indicios de fuego";
            if (smoke) return "Posible presencia de humo";
            return uncertain ? "Resultado no concluyente" : "Sin indicios claros de fuego ni humo";
        }
        String description() {
            if (localized) return "Se encontraron señales en algunas zonas de la imagen. Esto no mide el tamaño, la intensidad ni la gravedad real del incendio.";
            if (fire && smoke) return "El modelo encontró características de llamas y humo. Es una estimación visual y puede equivocarse.";
            if (fire) return "El modelo encontró características de llamas. La imagen no permite determinar la gravedad de un incendio.";
            if (smoke) return "Hay características compatibles con humo, sin evidencia suficiente de llamas. Niebla y nubes pueden confundirse con humo.";
            if (uncertain) return "La evidencia es débil o ambigua. Prueba una foto más cercana y nítida; no interpretes este resultado como ausencia de fuego.";
            return "No se encontró evidencia suficiente en esta foto. El resultado no confirma que el entorno sea seguro.";
        }
    }

    FireClassifier(Context context) throws Exception {
        byte[] bytes = readAsset(context, "bosque_alerta.tflite");
        modelBuffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        modelBuffer.put(bytes).rewind();
        interpreter = new Interpreter(modelBuffer, new Interpreter.Options().setNumThreads(2));
        int[] shape = interpreter.getInputTensor(0).shape();
        if (shape.length != 4 || shape[1] != SIZE || shape[2] != SIZE || interpreter.getOutputTensor(0).shape()[1] != 2) {
            interpreter.close(); throw new IllegalStateException("El modelo no corresponde a la versión de fuego y humo.");
        }
        JSONObject contract = new JSONObject(new String(readAsset(context, "contrato.json"), java.nio.charset.StandardCharsets.UTF_8));
        fireThreshold = (float) contract.getJSONArray("thresholds").getDouble(0);
        smokeThreshold = (float) contract.getJSONArray("thresholds").getDouble(1);
        uncertainMargin = (float) contract.getDouble("uncertain_margin");
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = context.getAssets().open(name)) {
            byte[] chunk = new byte[8192]; int count;
            while ((count = stream.read(chunk)) != -1) bytes.write(chunk, 0, count);
        }
        return bytes.toByteArray();
    }

    float fireThreshold() { return fireThreshold; }
    float smokeThreshold() { return smokeThreshold; }

    float[] scores(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true);
        resized.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        input.rewind();
        for (int pixel : pixels) {
            input.putFloat(Color.red(pixel) / 255f); input.putFloat(Color.green(pixel) / 255f); input.putFloat(Color.blue(pixel) / 255f);
        }
        if (resized != bitmap) resized.recycle();
        input.rewind(); interpreter.run(input, output);
        return output[0].clone();
    }

    /** Cuatro recortes grandes superpuestos ayudan con llamas laterales pequeñas. */
    Analysis analyze(Bitmap bitmap) {
        float[] global = scores(bitmap);
        int width = Math.max(1, Math.round(bitmap.getWidth() * .65f));
        int height = Math.max(1, Math.round(bitmap.getHeight() * .65f));
        int regions = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                Bitmap crop = Bitmap.createBitmap(bitmap, col == 0 ? 0 : bitmap.getWidth() - width,
                        row == 0 ? 0 : bitmap.getHeight() - height, width, height);
                float[] score = scores(crop);
                if (score[0] >= Math.min(.95f, fireThreshold + .10f)) regions++;
                if (crop != bitmap) crop.recycle();
            }
        }
        return new Analysis(global[0], global[1], regions, fireThreshold, smokeThreshold, uncertainMargin);
    }

    @Override public void close() { interpreter.close(); }
}
