package pe.edu.bosquealerta;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.view.Gravity;
import android.widget.*;
import java.io.*;
import java.nio.*;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.tensorflow.lite.Interpreter;

/** Todo el procesamiento ocurre localmente. Entrada RGB float32 [1,96,96,3] / 255. */
public class MainActivity extends Activity {
    private TextView result;
    private ImageView preview;
    private Button select;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Interpreter model;
    private boolean closing;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24),dp(28),dp(24),dp(20));
        page.setBackgroundColor(Color.rgb(244,248,243));
        TextView title = new TextView(this); title.setText("BOSQUE ALERTA"); title.setTextSize(28); title.setTextColor(Color.rgb(28,71,45));
        page.addView(title);
        TextView subtitle = new TextView(this); subtitle.setText("Clasificación de imágenes con una CNN\nFuego / Sin fuego · Procesamiento sin conexión"); subtitle.setTextSize(16); page.addView(subtitle);
        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.FIT_CENTER); preview.setContentDescription("Imagen seleccionada para analizar");
        page.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        result = new TextView(this); result.setText("Selecciona una fotografía para comenzar."); result.setTextSize(21); result.setGravity(Gravity.CENTER); page.addView(result);
        select = new Button(this); select.setText("Seleccionar fotografía"); page.addView(select);
        TextView note = new TextView(this); note.setText("Prototipo académico. Puede equivocarse. El puntaje del modelo no es una probabilidad calibrada ni una alerta oficial. No detecta ubicación ni confirma incendios forestales."); note.setTextSize(12); page.addView(note);
        setContentView(page);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getAssets().open("bosque_alerta.tflite")) { byte[] b = new byte[8192]; int n; while ((n=in.read(b))!=-1) bytes.write(b,0,n); }
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.size()).order(ByteOrder.nativeOrder()); buffer.put(bytes.toByteArray()); buffer.rewind();
            model = new Interpreter(buffer,new Interpreter.Options().setNumThreads(2));
        } catch (Exception e) { result.setText("No se pudo cargar el modelo: " + e.getMessage()); select.setEnabled(false); }
        select.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,10); });
    }
    @Override protected void onActivityResult(int request,int code,Intent data) {
        super.onActivityResult(request,code,data);
        if(request!=10 || code!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri = data.getData(); select.setEnabled(false); result.setText("Analizando imagen…");
        worker.execute(() -> {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(),uri);
                Bitmap image;
                if(android.os.Build.VERSION.SDK_INT >= 28) {
                    image=ImageDecoder.decodeBitmap(source,(decoder,info,s)-> { decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE); int w=info.getSize().getWidth(),h=info.getSize().getHeight(); float scale=Math.min(1f,1600f/Math.max(w,h)); decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale))); decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB)); });
                } else { throw new IOException("Esta versión requiere Android 9 o posterior"); }
                Bitmap resized=Bitmap.createScaledBitmap(image,96,96,true);
                ByteBuffer input=ByteBuffer.allocateDirect(96*96*3*4).order(ByteOrder.nativeOrder());
                int[] pixels=new int[96*96]; resized.getPixels(pixels,0,96,0,0,96,96);
                for(int p:pixels) { input.putFloat(Color.red(p)/255f); input.putFloat(Color.green(p)/255f); input.putFloat(Color.blue(p)/255f); }
                input.rewind(); float[][] output=new float[1][1]; model.run(input,output); float p=output[0][0];
                runOnUiThread(() -> { if(closing) return; preview.setImageBitmap(image); result.setText(String.format(Locale.getDefault(),"%s\nPuntaje de fuego: %.1f %%",p>=.5f?"FUEGO":"SIN FUEGO",p*100)); result.setTextColor(p>=.5f?Color.rgb(180,55,25):Color.rgb(28,100,55)); select.setEnabled(true); });
            } catch(Exception e) { runOnUiThread(() -> { if(closing) return; result.setText("No se pudo analizar la imagen. Selecciona otra fotografía."); select.setEnabled(true); }); }
        });
    }
    @Override protected void onDestroy() { closing=true; worker.execute(() -> { if(model!=null) model.close(); }); worker.shutdown(); super.onDestroy(); }
}
