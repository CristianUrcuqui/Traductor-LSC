
package org.tensorflow.lite.examples.gesture;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Main {@code Activity} clase para la aplicación Cámara.*/
public class CameraActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_camera);
    if (null == savedInstanceState) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.container, Camera2BasicFragment.newInstance())
          .commit();
    }
  }
}
