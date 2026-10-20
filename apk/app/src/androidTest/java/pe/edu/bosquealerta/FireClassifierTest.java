package pe.edu.bosquealerta;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import static org.junit.Assert.*;

/** Casos reportados por el usuario, excluidos de entrenamiento y validación. */
@RunWith(AndroidJUnit4.class)
public class FireClassifierTest {
    private FireClassifier.Analysis analyze(String name) throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        try (FireClassifier model = new FireClassifier(target);
             InputStream stream = tests.getAssets().open(name)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            assertNotNull(bitmap);
            try { return model.analyze(bitmap); }
            finally { bitmap.recycle(); }
        }
    }
    @Test public void fireOnImageSidesIsRecognized() throws Exception {
        FireClassifier.Analysis result = analyze("fuego_lateral.png");
        assertTrue("No debe ocultar el fuego lateral", result.fire);
        assertTrue("La referencia también muestra humo", result.smoke);
        assertTrue(result.title().toLowerCase().contains("fuego"));
    }
    @Test public void extensiveFireIsRecognized() throws Exception {
        FireClassifier.Analysis result = analyze("fuego_extenso.png");
        assertTrue(result.fire);
        assertTrue(result.smoke);
    }
    @Test public void sunsetIsNeitherFireNorSmoke() throws Exception {
        FireClassifier.Analysis result = analyze("atardecer.png");
        assertFalse("Atardecer clasificado como fuego", result.fire);
        assertFalse("Atardecer clasificado como humo", result.smoke);
    }
    @Test public void smokeImageUsesSmokeLabel() throws Exception {
        FireClassifier.Analysis result = analyze("humo_sin_llamas.jpg");
        assertTrue("Debe identificar humo en la referencia etiquetada", result.smoke);
        assertFalse("La referencia no contiene llamas visibles", result.fire);
    }
    @Test public void lowEvidenceIsNotPresentedAsSafety() {
        FireClassifier.Analysis r = new FireClassifier.Analysis(.49f,.1f,0,.5f,.5f,.12f);
        assertTrue(r.uncertain); assertFalse(r.fire);
        assertEquals("Resultado no concluyente",r.title());
    }
    @Test public void smokeHasIndependentLabel() {
        FireClassifier.Analysis r = new FireClassifier.Analysis(.1f,.9f,0,.5f,.5f,.12f);
        assertTrue(r.smoke); assertFalse(r.fire);
        assertEquals("Posible presencia de humo",r.title());
    }
    @Test public void regionalEvidenceDoesNotClaimSeverity() {
        FireClassifier.Analysis r = new FireClassifier.Analysis(.2f,.1f,2,.5f,.5f,.12f);
        assertTrue(r.fire); assertTrue(r.localized);
        assertTrue(r.description().contains("no mide"));
    }
}
