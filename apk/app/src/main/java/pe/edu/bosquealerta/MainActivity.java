package pe.edu.bosquealerta;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.tensorflow.lite.Interpreter;

/** Todo el procesamiento ocurre localmente. Entrada RGB float32 [1,96,96,3] / 255. */
public class MainActivity extends Activity {
    private static final int REQ_GALLERY = 10;
    private static final int REQ_CAMERA = 11;
    private static final int REQ_LIVE_PERMISSION = 20;
    private static final int REQ_PERM_CAMERA_SHOT = 21;
    private static final int REQ_PERM_GALLERY = 22;

    private static final int COLOR_IDLE = 0xFFEAF1EC;
    private static final int COLOR_BUSY = 0xFFFFF1E4;
    private static final int COLOR_FIRE = 0xFFD9480F;
    private static final int COLOR_OK = 0xFF157347;

    private TextView resultTitle;
    private TextView resultHint;
    private ImageView preview;
    private View placeholder;
    private ProgressBar progress;
    private MaterialCardView resultCard;
    private MaterialButton cameraBtn;
    private MaterialButton galleryBtn;
    private MaterialButton liveBtn;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Interpreter model;
    private boolean busy;
    private boolean closing;
    private File pendingPhoto;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.preview);
        placeholder = findViewById(R.id.placeholder);
        progress = findViewById(R.id.progress);
        resultCard = findViewById(R.id.resultCard);
        resultTitle = findViewById(R.id.resultTitle);
        resultHint = findViewById(R.id.resultHint);
        cameraBtn = findViewById(R.id.cameraBtn);
        galleryBtn = findViewById(R.id.galleryBtn);
        liveBtn = findViewById(R.id.liveBtn);

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getAssets().open("bosque_alerta.tflite")) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) != -1) bytes.write(b, 0, n);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.size()).order(ByteOrder.nativeOrder());
            buffer.put(bytes.toByteArray()).rewind();
            model = new Interpreter(buffer, new Interpreter.Options().setNumThreads(2));
        } catch (Exception e) {
            showError("No se pudo cargar el modelo: " + e.getMessage());
            setButtonsEnabled(false);
        }

        cameraBtn.setOnClickListener(v -> requestCameraThen(this::openCamera, REQ_PERM_CAMERA_SHOT, "la cámara para tomar una fotografía"));
        galleryBtn.setOnClickListener(v -> requestGalleryThen(this::openGallery));
        liveBtn.setOnClickListener(v -> requestCameraThen(this::openLive, REQ_LIVE_PERMISSION, "la cámara para el modo en tiempo real"));
    }

    private void openLive() {
        startActivity(new Intent(this, LiveActivity.class));
    }

    private boolean granted(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String galleryPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_IMAGES"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void requestCameraThen(Runnable action, int requestCode, String feature) {
        if (granted(Manifest.permission.CAMERA)) {
            action.run();
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showPermissionDialog("Permiso de cámara",
                    "Para usar " + feature + ", la app necesita acceso a la cámara. Concede el permiso para continuar.",
                    () -> requestPermissions(new String[]{Manifest.permission.CAMERA}, requestCode));
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, requestCode);
        }
    }

    private void requestGalleryThen(Runnable action) {
        String permission = galleryPermission();
        if (granted(permission)) {
            action.run();
            return;
        }
        if (shouldShowRequestPermissionRationale(permission)) {
            showPermissionDialog("Permiso de galería",
                    "Para elegir una fotografía, la app necesita acceso a tus imágenes. Concede el permiso para continuar.",
                    () -> requestPermissions(new String[]{permission}, REQ_PERM_GALLERY));
        } else {
            requestPermissions(new String[]{permission}, REQ_PERM_GALLERY);
        }
    }

    private void showPermissionDialog(String title, String message, Runnable retry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Conceder", (d, w) -> retry.run())
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Abrir ajustes", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(i);
                })
                .show();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (request == REQ_LIVE_PERMISSION) {
            if (ok) {
                startActivity(new Intent(this, LiveActivity.class));
            } else {
                showPermissionDialog("Permiso necesario",
                        "El modo en tiempo real no puede funcionar sin acceso a la cámara.",
                        () -> requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_LIVE_PERMISSION));
            }
        } else if (request == REQ_PERM_CAMERA_SHOT) {
            if (ok) {
                openCamera();
            } else {
                showError("Se necesita el permiso de cámara para tomar una fotografía.");
            }
        } else if (request == REQ_PERM_GALLERY) {
            if (ok) {
                openGallery();
            } else {
                showError("Se necesita el permiso de galería para elegir una imagen.");
            }
        }
    }

    private void openGallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_GALLERY);
    }

    private void openCamera() {
        if (busy) return;
        try {
            File dir = new File(getCacheDir(), "captures");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("directorio no disponible");
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            File out = new File(dir, "captura_" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);

            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            List<ResolveInfo> apps = getPackageManager().queryIntentActivities(i, 0);
            for (ResolveInfo ri : apps) grantUriPermission(ri.activityInfo.packageName, uri, flags);
            if (apps.isEmpty()) { showError("No hay una aplicación de cámara disponible en este dispositivo."); return; }

            pendingPhoto = out;
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            showError("No se pudo abrir la cámara: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request == REQ_GALLERY) {
            if (code == RESULT_OK && data != null && data.getData() != null) {
                analyze(ImageDecoder.createSource(getContentResolver(), data.getData()));
            }
        } else if (request == REQ_CAMERA) {
            File photo = pendingPhoto;
            pendingPhoto = null;
            if (code == RESULT_OK && photo != null && photo.exists() && photo.length() > 0) {
                analyze(ImageDecoder.createSource(photo));
            }
        }
    }

    private void analyze(ImageDecoder.Source source) {
        if (model == null || busy) return;
        busy = true;
        setButtonsEnabled(false);
        placeholder.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        resultCard.setCardBackgroundColor(COLOR_BUSY);
        resultTitle.setTextColor(Color.rgb(130, 75, 20));
        resultTitle.setText("ANALIZANDO…");
        resultHint.setTextColor(Color.rgb(145, 100, 55));
        resultHint.setText("Procesando la imagen con el modelo CNN en el dispositivo");

        worker.execute(() -> {
            try {
                Bitmap image = decodeCapped(source);
                float p = classify(image);
                runOnUiThread(() -> { if (!closing) showResult(image, p); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!closing) showError("No se pudo analizar la imagen. Prueba con otra fotografía."); });
            }
        });
    }

    private Bitmap decodeCapped(ImageDecoder.Source source) throws IOException {
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float scale = Math.min(1f, 1600f / Math.max(w, h));
            decoder.setTargetSize(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)));
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
    }

    private float classify(Bitmap image) {
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

    private void showResult(Bitmap image, float p) {
        busy = false;
        progress.setVisibility(View.GONE);
        preview.setImageBitmap(image);
        preview.setVisibility(View.VISIBLE);
        placeholder.setVisibility(View.GONE);
        boolean fire = p >= .5f;
        resultCard.setCardBackgroundColor(fire ? COLOR_FIRE : COLOR_OK);
        resultTitle.setTextColor(Color.WHITE);
        resultTitle.setText(fire ? "🔥 FUEGO" : "🌿 SIN FUEGO");
        resultHint.setTextColor(0xE6FFFFFF);
        resultHint.setText(String.format(Locale.getDefault(), "Puntaje de fuego: %.1f %%", p * 100));
        setButtonsEnabled(true);
    }

    private void showError(String msg) {
        busy = false;
        progress.setVisibility(View.GONE);
        if (preview.getDrawable() == null) placeholder.setVisibility(View.VISIBLE);
        resultCard.setCardBackgroundColor(COLOR_IDLE);
        resultTitle.setTextColor(getResources().getColor(R.color.primary_dark, getTheme()));
        resultTitle.setText("AVISO");
        resultHint.setTextColor(getResources().getColor(R.color.muted, getTheme()));
        resultHint.setText(msg);
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        cameraBtn.setEnabled(enabled);
        galleryBtn.setEnabled(enabled);
    }

    @Override protected void onDestroy() {
        closing = true;
        worker.execute(() -> { if (model != null) model.close(); });
        worker.shutdown();
        super.onDestroy();
    }
}
