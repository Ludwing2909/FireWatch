package pe.edu.bosquealerta;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.tensorflow.lite.Interpreter;

/** Modo en vivo: analiza la cámara por zonas y sigue las zonas con fuego. */
public class LiveActivity extends ComponentActivity {
    private static final String TAG = "FireLive";
    private static final float FIRE_THRESHOLD = 0.5f;
    private static final float ZONE_THRESHOLD = 0.6f;
    private static final int GRID = 4;
    private static final long MIN_INTERVAL_MS = 120;

    private PreviewView previewView;
    private FireOverlayView overlay;
    private TextView statusChip;
    private TextView stats;

    private final ExecutorService analyzer = Executors.newSingleThreadExecutor();
    private final List<Track> tracks = new ArrayList<>();
    private Interpreter model;
    private ProcessCameraProvider cameraProvider;

    private long lastAnalyzeAt;
    private int framesSinceFps;
    private long lastFpsAt;
    private float fps;

    private static class Track {
        float left, top, right, bottom, score;
        int age, missed;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_live);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = findViewById(R.id.previewView);
        overlay = findViewById(R.id.overlay);
        statusChip = findViewById(R.id.statusChip);
        stats = findViewById(R.id.stats);
        ((MaterialButton) findViewById(R.id.stopBtn)).setOnClickListener(v -> finish());

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Se requiere el permiso de cámara.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            loadModel();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo cargar el modelo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        startCamera();
    }

    private void loadModel() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = getAssets().open("bosque_alerta.tflite")) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.size()).order(ByteOrder.nativeOrder());
        buffer.put(bytes.toByteArray()).rewind();
        model = new Interpreter(buffer, new Interpreter.Options().setNumThreads(2));
    }

    private ResolutionSelector resolution() {
        return new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(
                        new Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build();
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder()
                        .setResolutionSelector(resolution())
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(resolution())
                        .build();
                analysis.setAnalyzer(analyzer, this::onFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Log.e(TAG, "No se pudo iniciar la cámara", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "No se pudo abrir la cámara: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onFrame(ImageProxy proxy) {
        try {
            long now = SystemClock.elapsedRealtime();
            if (now - lastAnalyzeAt < MIN_INTERVAL_MS) return;
            lastAnalyzeAt = now;

            Bitmap bmp = proxy.toBitmap();
            int rot = proxy.getImageInfo().getRotationDegrees();
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }

            float global = score(bmp);
            float[] cells = new float[GRID * GRID];
            int tw = bmp.getWidth() / GRID;
            int th = bmp.getHeight() / GRID;
            for (int r = 0; r < GRID; r++) {
                for (int c = 0; c < GRID; c++) {
                    cells[r * GRID + c] = score(Bitmap.createBitmap(bmp, c * tw, r * th, tw, th));
                }
            }

            List<FireOverlayView.Box> follow = updateTracks(cells, tw, th);
            int zones = follow.size();

            framesSinceFps++;
            if (lastFpsAt == 0) lastFpsAt = now;
            if (now - lastFpsAt >= 1000) {
                fps = framesSinceFps * 1000f / (now - lastFpsAt);
                framesSinceFps = 0;
                lastFpsAt = now;
            }

            final float g = global;
            final float f = fps;
            final int w = bmp.getWidth();
            final int h = bmp.getHeight();
            final int zoneCount = zones;
            Log.d(TAG, String.format(Locale.US, "fps=%.1f global=%.2f zonas=%d size=%dx%d", f, g, zoneCount, w, h));

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                overlay.setBoxes(follow, w, h);
                boolean fire = g >= FIRE_THRESHOLD || zoneCount > 0;
                if (fire) {
                    statusChip.setText(String.format(Locale.getDefault(), "🔥 FUEGO · %.0f%% · %d zona%s", g * 100, zoneCount, zoneCount == 1 ? "" : "s"));
                    statusChip.setBackgroundColor(0xE0D9480F);
                } else {
                    statusChip.setText(String.format(Locale.getDefault(), "Sin fuego · %.0f%%", g * 100));
                    statusChip.setBackgroundColor(0xCC157347);
                }
                stats.setText(String.format(Locale.getDefault(),
                        "Seguimiento: %.1f fps · %d zona%s · Umbral 60 %%", f, zoneCount, zoneCount == 1 ? "" : "s"));
            });
        } catch (Exception e) {
            Log.w(TAG, "Cuadro omitido: " + e);
        } finally {
            proxy.close();
        }
    }

    /** Detecta zonas contiguas de fuego y las suaviza para simular seguimiento. */
    private List<FireOverlayView.Box> updateTracks(float[] cells, int tw, int th) {
        List<float[]> detections = connectedComponents(cells);

        boolean[] trackUpdated = new boolean[tracks.size()];
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            int best = -1;
            float bestIou = 0.02f;
            for (int j = 0; j < detections.size(); j++) {
                float iou = iou(track, detections.get(j));
                if (iou > bestIou) {
                    bestIou = iou;
                    best = j;
                }
            }
            if (best >= 0) {
                float[] d = detections.get(best);
                float a = 0.55f;
                track.left = track.left * (1 - a) + d[0] * a;
                track.top = track.top * (1 - a) + d[1] * a;
                track.right = track.right * (1 - a) + d[2] * a;
                track.bottom = track.bottom * (1 - a) + d[3] * a;
                track.score = track.score * 0.5f + d[4] * 0.5f;
                track.age++;
                track.missed = 0;
                trackUpdated[i] = true;
                detections.remove(best);
            } else {
                track.missed++;
                track.score *= 0.8f;
            }
        }

        for (int i = tracks.size() - 1; i >= 0; i--) {
            if (tracks.get(i).missed > 4) tracks.remove(i);
        }

        for (float[] d : detections) {
            Track track = new Track();
            track.left = d[0];
            track.top = d[1];
            track.right = d[2];
            track.bottom = d[3];
            track.score = d[4] * 0.7f;
            track.age = 1;
            tracks.add(track);
        }

        List<FireOverlayView.Box> out = new ArrayList<>();
        for (Track track : tracks) {
            if (track.age < 2 || track.score < 0.5f) continue;
            FireOverlayView.Box box = new FireOverlayView.Box();
            box.left = track.left;
            box.top = track.top;
            box.right = track.right;
            box.bottom = track.bottom;
            box.score = Math.min(1f, track.score);
            out.add(box);
        }
        return out;
    }

    /** Agrupa las celdas con fuego contiguas en un rectángulo por grupo. */
    private List<float[]> connectedComponents(float[] cells) {
        boolean[] visited = new boolean[GRID * GRID];
        List<float[]> out = new ArrayList<>();
        for (int idx = 0; idx < cells.length; idx++) {
            if (visited[idx] || cells[idx] < ZONE_THRESHOLD) continue;
            int r0 = idx / GRID;
            int c0 = idx % GRID;
            List<Integer> stack = new ArrayList<>();
            stack.add(idx);
            visited[idx] = true;
            int minR = r0, maxR = r0, minC = c0, maxC = c0;
            float best = cells[idx];
            while (!stack.isEmpty()) {
                int cur = stack.remove(stack.size() - 1);
                int r = cur / GRID;
                int c = cur % GRID;
                minR = Math.min(minR, r);
                maxR = Math.max(maxR, r);
                minC = Math.min(minC, c);
                maxC = Math.max(maxC, c);
                best = Math.max(best, cells[cur]);
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (nr < 0 || nr >= GRID || nc < 0 || nc >= GRID) continue;
                        int ni = nr * GRID + nc;
                        if (visited[ni] || cells[ni] < ZONE_THRESHOLD) continue;
                        visited[ni] = true;
                        stack.add(ni);
                    }
                }
            }
            out.add(new float[]{minC, minR, maxC + 1, maxR + 1, best});
        }
        return out;
    }

    private float iou(Track track, float[] d) {
        float l = Math.max(track.left, d[0]);
        float t = Math.max(track.top, d[1]);
        float r = Math.min(track.right, d[2]);
        float b = Math.min(track.bottom, d[3]);
        float inter = Math.max(0, r - l) * Math.max(0, b - t);
        if (inter <= 0) return 0;
        float areaA = (track.right - track.left) * (track.bottom - track.top);
        float areaB = (d[2] - d[0]) * (d[3] - d[1]);
        return inter / (areaA + areaB - inter);
    }

    private float score(Bitmap image) {
        Bitmap resized = Bitmap.createScaledBitmap(image, 96, 96, true);
        ByteBuffer input = ByteBuffer.allocateDirect(96 * 96 * 3 * 4).order(ByteOrder.nativeOrder());
        int[] pixels = new int[96 * 96];
        resized.getPixels(pixels, 0, 96, 0, 0, 96, 96);
        for (int px : pixels) {
            input.putFloat(Color.red(px) / 255f);
            input.putFloat(Color.green(px) / 255f);
            input.putFloat(Color.blue(px) / 255f);
        }
        input.rewind();
        float[][] output = new float[1][1];
        model.run(input, output);
        return output[0][0];
    }

    @Override protected void onDestroy() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        analyzer.shutdownNow();
        if (model != null) model.close();
        super.onDestroy();
    }
}
