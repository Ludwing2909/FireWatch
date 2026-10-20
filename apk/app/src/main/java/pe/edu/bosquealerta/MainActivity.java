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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** Inferencia local de fuego y humo con exploración de regiones. */
public class MainActivity extends Activity {
    private static final int REQ_GALLERY = 10;
    private static final int REQ_CAMERA = 11;
    private static final int REQ_LIVE_PERMISSION = 20;
    private static final int REQ_PERM_CAMERA_SHOT = 21;

    private static final int COLOR_IDLE = 0xFFEAF1EC;
    private static final int COLOR_BUSY = 0xFFFFF1E4;
    private static final int COLOR_FIRE = 0xFFD9480F;
    private static final int COLOR_OK = 0xFF157347;

    private TextView resultTitle;
    private TextView resultHint;
    private TextView resultScores;
    private ImageView preview;
    private View placeholder;
    private ProgressBar progress;
    private MaterialCardView resultCard;
    private MaterialButton cameraBtn;
    private MaterialButton galleryBtn;
    private MaterialButton liveBtn;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private FireClassifier model;
    private boolean busy;
    private boolean closing;
    private File pendingPhoto;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        WindowInsetsHelper.apply(findViewById(R.id.root));
        if (state != null && state.getString("pendingPhoto") != null)
            pendingPhoto = new File(state.getString("pendingPhoto"));
        findViewById(R.id.aboutBtn).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle("Análisis local, alcance limitado")
            .setMessage("La CNN analiza por separado características visuales de fuego y humo. Explora la imagen completa y recortes para encontrar señales pequeñas. Un resultado localizado describe zonas de la foto, no la intensidad ni la gravedad de un incendio. Puede confundir niebla, nubes y luces. Las cajas en vivo son recortes experimentales, no contornos de objetos validados.\n\nLa cámara se solicita únicamente cuando la utilizas. La galería permite elegir un archivo sin dar acceso a todas tus fotos. No enviamos imágenes ni solicitamos ubicación, micrófono o contactos.")
            .setPositiveButton("Entendido", null).show());

        preview = findViewById(R.id.preview);
        placeholder = findViewById(R.id.placeholder);
        progress = findViewById(R.id.progress);
        resultCard = findViewById(R.id.resultCard);
        resultTitle = findViewById(R.id.resultTitle);
        resultHint = findViewById(R.id.resultHint);
        resultScores = findViewById(R.id.resultScores);
        cameraBtn = findViewById(R.id.cameraBtn);
        galleryBtn = findViewById(R.id.galleryBtn);
        liveBtn = findViewById(R.id.liveBtn);

        try {
            model = new FireClassifier(this);
        } catch (Exception e) {
            showError("No se pudo cargar el modelo: " + e.getMessage());
            setButtonsEnabled(false);
        }

        cameraBtn.setOnClickListener(v -> requestCameraThen(this::openCamera, REQ_PERM_CAMERA_SHOT, "la cámara para tomar una fotografía"));
        galleryBtn.setOnClickListener(v -> openGallery());
        liveBtn.setOnClickListener(v -> requestCameraThen(this::openLive, REQ_LIVE_PERMISSION, "la cámara para el modo en tiempo real"));
    }

    private void openLive() {
        startActivity(new Intent(this, LiveActivity.class));
    }

    private boolean granted(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }


    private void requestCameraThen(Runnable action, int requestCode, String feature) {
        if (granted(Manifest.permission.CAMERA)) {
            action.run();
            return;
        }
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("askedCamera", false);
        if (asked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new MaterialAlertDialogBuilder(this).setTitle("Acceso a la cámara desactivado")
                .setMessage("Puedes habilitar Cámara en los permisos de Bosque Alerta. La galería sigue disponible.")
                .setPositiveButton("Abrir ajustes", (d, w) -> startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))))
                .setNegativeButton("Ahora no", null).show();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("askedCamera", true).apply();
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showPermissionDialog("Permiso de cámara",
                    "Para usar " + feature + ", la app necesita acceso a la cámara. Concede el permiso para continuar.",
                    () -> requestPermissions(new String[]{Manifest.permission.CAMERA}, requestCode));
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, requestCode);
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
                showError("Cámara no autorizada. Puedes usar la galería o volver a intentar abrir la cámara.");
            }
        } else if (request == REQ_PERM_CAMERA_SHOT) {
            if (ok) {
                openCamera();
            } else {
                showError("Se necesita el permiso de cámara para tomar una fotografía.");
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
        resultScores.setVisibility(View.GONE);
        resultHint.setTextColor(Color.rgb(145, 100, 55));
        resultHint.setText("Procesando la imagen con el modelo CNN en el dispositivo");

        worker.execute(() -> {
            try {
                Bitmap image = decodeCapped(source);
                FireClassifier.Analysis analysis = model.analyze(image);
                runOnUiThread(() -> { if (!closing) showResult(image, analysis); });
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

    private void showResult(Bitmap image, FireClassifier.Analysis analysis) {
        busy = false;
        progress.setVisibility(View.GONE);
        preview.setImageBitmap(image);
        preview.setVisibility(View.VISIBLE);
        placeholder.setVisibility(View.GONE);
        boolean warning = analysis.fire || analysis.smoke || analysis.uncertain;
        resultCard.setCardBackgroundColor(analysis.fire ? COLOR_FIRE : warning ? 0xFF856015 : COLOR_OK);
        resultTitle.setTextColor(Color.WHITE);
        resultTitle.setText(analysis.title());
        resultScores.setText(getString(R.string.model_scores, analysis.fireScore * 100, analysis.smokeScore * 100));
        resultScores.setVisibility(View.VISIBLE);
        resultHint.setTextColor(0xE6FFFFFF);
        resultHint.setText(getString(R.string.model_result_explanation, analysis.description(), getString(R.string.model_scores_caption)));
        setButtonsEnabled(true);
    }

    private void showError(String msg) {
        busy = false;
        progress.setVisibility(View.GONE);
        if (preview.getDrawable() == null) placeholder.setVisibility(View.VISIBLE);
        resultCard.setCardBackgroundColor(COLOR_IDLE);
        resultTitle.setTextColor(getResources().getColor(R.color.primary_dark, getTheme()));
        resultTitle.setText("AVISO");
        resultScores.setVisibility(View.GONE);
        resultHint.setTextColor(getResources().getColor(R.color.muted, getTheme()));
        resultHint.setText(msg);
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        cameraBtn.setEnabled(enabled && model != null);
        galleryBtn.setEnabled(enabled && model != null);
        liveBtn.setEnabled(enabled && model != null);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pendingPhoto != null) out.putString("pendingPhoto", pendingPhoto.getAbsolutePath());
    }

    @Override protected void onDestroy() {
        closing = true;
        worker.execute(() -> { if (model != null) model.close(); });
        worker.shutdown();
        super.onDestroy();
    }
}
