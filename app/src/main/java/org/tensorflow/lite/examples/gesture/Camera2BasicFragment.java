

package org.tensorflow.lite.examples.gesture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Basic fragments for the Camera. */
public class Camera2BasicFragment extends Fragment
    implements ActivityCompat.OnRequestPermissionsResultCallback {

  /** Tag for the {@link Log}. */
  private static final String TAG = "TfLitecamerademo";

  private static final String FRAGMENT_DIALOG = "dialog";

  private static final String HANDLE_THREAD_NAME = "CameraBackground";

  private static final int PERMISSIONS_REQUEST_CODE = 1;

  private final Object lock = new Object();
  private boolean runClassifier = false;
  private boolean checkedPermissions = false;
  private TextView textView;
  private ToggleButton toggle;
  private NumberPicker np;
  private ImageClassifier classifier;
  private LinearLayout aLayout,
      bLayout,
      cLayout,
      dLayout,
      eLayout,
      fLayout,
      gLayout,
      hLayout,
          iLayout,
          kLayout,
          lLayout,
          mLayout,
          nLayout,
          oLayout,
          pLayout,
          qLayout,
          rLayout,
          sLayout,
          tLayout,
          uLayout,
          vLayout,
          wLayout,
          xLayout,
          yLayout;



  private BottomSheetBehavior<LinearLayout> sheetBehavior;
  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private TextView resultTextView;
  /** Ancho máximo de vista previa garantizado por la API de Camera2 */
  private static final int MAX_PREVIEW_WIDTH = 640;

  /** Altura máxima de vista previa garantizada por la API de Camera2 */
  private static final int MAX_PREVIEW_HEIGHT = 480;

  private String lastSelectedGesture;
  /**
   * {@link TextureView.SurfaceTextureListener} maneja varios eventos del ciclo de vida en un{@link
   * TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
      new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
          openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
          configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
          return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
      };

  /** ID de la actual {@link CameraDevice}. */
  private String cameraId;

  /** An {@link AutoFitTextureView} para la vista previa de la cámara. */
  private AutoFitTextureView textureView;

  /** A {@link CameraCaptureSession } para la vista previa de la cámara. */
  private CameraCaptureSession captureSession;

  /** A reference to the opened {@link CameraDevice}. */
  private CameraDevice cameraDevice;

  /** The {@link android.util.Size}de la vista previa de la cámara. */
  private Size previewSize;

  /** {@link CameraDevice.StateCallback} is called when {@link CameraDevice} cambia su estado. */
  private final CameraDevice.StateCallback stateCallback =
      new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice currentCameraDevice) {
          // This method is called when the camera is opened.  We start camera preview here.
          cameraOpenCloseLock.release();
          cameraDevice = currentCameraDevice;
          createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice currentCameraDevice) {
          cameraOpenCloseLock.release();
          currentCameraDevice.close();
          cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice currentCameraDevice, int error) {
          cameraOpenCloseLock.release();
          currentCameraDevice.close();
          cameraDevice = null;
          Activity activity = getActivity();
          if (null != activity) {
            activity.finish();
          }
        }
      };

  /** Un hilo adicional para ejecutar tareas que no deberían bloquear la interfaz de usuario. */
  private HandlerThread backgroundThread;

  /** A {@link Handler} para ejecutar tareas en segundo plano.*/
  private Handler backgroundHandler;

  /** Un {@link ImageReader} que maneja la captura de imágenes. */
  private ImageReader imageReader;

  /** {@link CaptureRequest.Builder} para la vista previa de la cámara */
  private CaptureRequest.Builder previewRequestBuilder;

  /** {@link CaptureRequest} generado por {@link #previewRequestBuilder} */
  private CaptureRequest previewRequest;

  /** A {@link Semaphore} para evitar que la aplicación salga antes de cerrar la cámara.*/
  private Semaphore cameraOpenCloseLock = new Semaphore(1);

  /** A {@link CameraCaptureSession.CaptureCallback} que maneja eventos relacionados con la captura. */
  private CameraCaptureSession.CaptureCallback captureCallback =
      new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(
            @NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request,
            @NonNull CaptureResult partialResult) {}

        @Override
        public void onCaptureCompleted(
            @NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request,
            @NonNull TotalCaptureResult result) {}
      };

  /**
   * Muestra una{@link Toast} en el hilo de la interfaz de usuario para los resultados de la clasificación.
   *
   * @param text El mensaje para mostrar
   */
  private void showToast(String s) {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    SpannableString str1 = new SpannableString(s);
    builder.append(str1);
    showToast(builder);
  }

  private void showToast(SpannableStringBuilder builder) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              textView.setText(builder, TextView.BufferType.SPANNABLE);
              resultTextView.setText(builder, TextView.BufferType.SPANNABLE);
            }
          });
    }
  }

  /**
   * Cambia el tamaño de la imagen.
   *<p> Intentar usar un tamaño de vista previa demasiado grande podría exceder el ancho de banda del bus de la cámara
   *limitación, lo que da como resultado vistas previas magníficas, pero el almacenamiento de datos de captura de basura.
   *
   *<p> Dadas las {@code options} de {@code Size} compatibles con una cámara, elija la más pequeña que
   *es al menos tan grande como el tamaño de la vista de textura respectiva y, como máximo, es tan grande como
   *tamaño máximo respectivo, y cuya relación de aspecto coincide con el valor especificado. Si tal tamaño
   *no existe, elija el más grande que sea como máximo tan grande como el tamaño máximo respectivo, y
   *cuya relación de aspecto coincide con el valor especificado.
   *
   * @param choices La lista de tamaños que admite la cámara para la clase de salida deseada
   * @param textureViewWidth El ancho de la vista de textura en relación con las coordenadas del sensor
   * @param textureViewHeight La altura de la vista de textura en relación con las coordenadas del sensor.
   * @param maxWidth El ancho máximo que se puede elegir
   * @param maxHeight El ancho máximo que se puede elegir
   * @param aspectRatio The aspect ratio
   * @return La óptima {@code Size}, o uno arbitrario si ninguno fuera lo suficientemente grande
   */
  private static Size chooseOptimalSize(
      Size[] choices,
      int textureViewWidth,
      int textureViewHeight,
      int maxWidth,
      int maxHeight,
      Size aspectRatio) {

    // Recopile las resoluciones admitidas que sean al menos tan grandes como la superficie de vista previa
    List<Size> bigEnough = new ArrayList<>();
    // Recopile las resoluciones admitidas que sean más pequeñas que la superficie de vista previa
    List<Size> notBigEnough = new ArrayList<>();
    int w = aspectRatio.getWidth();
    int h = aspectRatio.getHeight();
    for (Size option : choices) {
      if (option.getWidth() <= maxWidth
          && option.getHeight() <= maxHeight
          && option.getHeight() == option.getWidth() * h / w) {
        if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
          bigEnough.add(option);
        } else {
          notBigEnough.add(option);
        }
      }
    }

    // Elige el más pequeño de los suficientemente grandes. Si no hay nadie lo suficientemente grande, elija el
    // el más grande de los que no son lo suficientemente grandes.
    if (bigEnough.size() > 0) {
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else if (notBigEnough.size() > 0) {
      return Collections.max(notBigEnough, new CompareSizesByArea());
    } else {
      Log.e(TAG, "No se pudo encontrar ningún tamaño de vista previa adecuado");
      return choices[0];
    }
  }

  public static Camera2BasicFragment newInstance() {
    return new Camera2BasicFragment();
  }

  /** Diseñe la vista previa y los botones. */
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
  }

  /**Conecte los botones a su controlador de eventos. */
  @Override
  public void onViewCreated(final View view, Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    textView = (TextView) view.findViewById(R.id.text);
    toggle = (ToggleButton) view.findViewById(R.id.button);

    toggle.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            classifier.setUseNNAPI(isChecked);
          }
        });

    np = (NumberPicker) view.findViewById(R.id.np);
    np.setMinValue(1);
    np.setMaxValue(10);
    np.setWrapSelectorWheel(true);
    np.setOnValueChangedListener(
        new NumberPicker.OnValueChangeListener() {
          @Override
          public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            classifier.setNumThreads(newVal);
          }
        });

    resultTextView = view.findViewById(R.id.result_text_view);
    bottomSheetLayout = view.findViewById(R.id.bottom_sheet_layout);
    gestureLayout = view.findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
              gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            } else {
              gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }

            int height = gestureLayout.getMeasuredHeight();

            sheetBehavior.setPeekHeight(height);
          }
        });
    sheetBehavior.setHideable(false);

    aLayout = view.findViewById(R.id.a_layout);
    bLayout = view.findViewById(R.id.b_layout);
    cLayout = view.findViewById(R.id.c_layout);
    dLayout = view.findViewById(R.id.d_layout);
    eLayout = view.findViewById(R.id.e_layout);
    fLayout = view.findViewById(R.id.f_layout);
    gLayout = view.findViewById(R.id.g_layout);
    hLayout = view.findViewById(R.id.h_layout);
    iLayout = view.findViewById(R.id.i_layout);
    kLayout = view.findViewById(R.id.k_layout);
    lLayout = view.findViewById(R.id.l_layout);
    mLayout = view.findViewById(R.id.m_layout);
    nLayout = view.findViewById(R.id.n_layout);
    oLayout = view.findViewById(R.id.o_layout);
    pLayout = view.findViewById(R.id.p_layout);
    qLayout = view.findViewById(R.id.q_layout);
    rLayout = view.findViewById(R.id.r_layout);
    sLayout = view.findViewById(R.id.s_layout);
    tLayout = view.findViewById(R.id.t_layout);
    uLayout = view.findViewById(R.id.u_layout);
    vLayout = view.findViewById(R.id.v_layout);
    wLayout = view.findViewById(R.id.w_layout);
    xLayout = view.findViewById(R.id.x_layout);
    yLayout = view.findViewById(R.id.y_layout);




    sheetBehavior.setBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
              case BottomSheetBehavior.STATE_HIDDEN:
                break;
              case BottomSheetBehavior.STATE_EXPANDED:
                {
                }
                break;
              case BottomSheetBehavior.STATE_COLLAPSED:
                {
                }
                break;
              case BottomSheetBehavior.STATE_DRAGGING:
                break;
              case BottomSheetBehavior.STATE_SETTLING:
                break;
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });

    enableDisableButtons();
  }

  private void enableDisableButtons() {

    String content = null;

    try {
      InputStream inputStream = getActivity().getAssets().open("labels.txt");

      int size = inputStream.available();
      byte[] buffer = new byte[size];
      inputStream.read(buffer);
      inputStream.close();
      content = new String(buffer);
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (content == null) return;

    aLayout.setEnabled(false);
    dLayout.setEnabled(false);
    cLayout.setEnabled(false);
    dLayout.setEnabled(false);
    eLayout.setEnabled(false);
    fLayout.setEnabled(false);
    gLayout.setEnabled(false);
    iLayout.setEnabled(false);
    kLayout.setEnabled(false);
    lLayout.setEnabled(false);
    mLayout.setEnabled(false);
    nLayout.setEnabled(false);
    oLayout.setEnabled(false);
    pLayout.setEnabled(false);
    qLayout.setEnabled(false);
    rLayout.setEnabled(false);
    sLayout.setEnabled(false);
    tLayout.setEnabled(false);
    uLayout.setEnabled(false);
    vLayout.setEnabled(false);
    wLayout.setEnabled(false);
    xLayout.setEnabled(false);
    yLayout.setEnabled(false);



    aLayout.setAlpha(0.4f);
    bLayout.setAlpha(0.4f);
    cLayout.setAlpha(0.4f);
    dLayout.setAlpha(0.4f);
    eLayout.setAlpha(0.4f);
    fLayout.setAlpha(0.4f);
    gLayout.setAlpha(0.4f);
    hLayout.setAlpha(0.4f);
    iLayout.setAlpha(0.4f);
    kLayout.setAlpha(0.4f);
    lLayout.setAlpha(0.4f);
    mLayout.setAlpha(0.4f);
    nLayout.setAlpha(0.4f);
    oLayout.setAlpha(0.4f);
    pLayout.setAlpha(0.4f);
    qLayout.setAlpha(0.4f);
    rLayout.setAlpha(0.4f);
    sLayout.setAlpha(0.4f);
    tLayout.setAlpha(0.4f);
    uLayout.setAlpha(0.4f);
    vLayout.setAlpha(0.4f);
    wLayout.setAlpha(0.4f);
    xLayout.setAlpha(0.4f);
    yLayout.setAlpha(0.4f);



    StringTokenizer tokenizer = new StringTokenizer(content, ",");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (token.equalsIgnoreCase("A")) {
        aLayout.setEnabled(true);
        aLayout.setAlpha(1.0f);
      } else if (token.equalsIgnoreCase("B")) {
        bLayout.setEnabled(true);
        bLayout.setAlpha(1.0f);
      } else if (token.equalsIgnoreCase("C")) {
        cLayout.setEnabled(true);
        cLayout.setAlpha(1.0f);
      } else if (token.equalsIgnoreCase("D")) {
        dLayout.setEnabled(true);
        dLayout.setAlpha(1.0f);
      } else if (token.equalsIgnoreCase("E")) {
        eLayout.setEnabled(true);
        eLayout.setAlpha(1.0f);
      } else if (token.equalsIgnoreCase("F")) {
        fLayout.setEnabled(true);
        fLayout.setAlpha(1.0f);
      } else if (token.equalsIgnoreCase("G")) {
        gLayout.setEnabled(true);
        gLayout.setAlpha(1.0f);
      } else if (token.equalsIgnoreCase("H")) {
        hLayout.setEnabled(true);
        hLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("I")) {
        iLayout.setEnabled(true);
        iLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("K")) {
        kLayout.setEnabled(true);
        kLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("L")) {
        lLayout.setEnabled(true);
        lLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("M")) {
        mLayout.setEnabled(true);
        mLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("N")) {
        nLayout.setEnabled(true);
        nLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("O")) {
        oLayout.setEnabled(true);
        oLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("P")) {
        pLayout.setEnabled(true);
        pLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("Q")) {
        qLayout.setEnabled(true);
        qLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("R")) {
        rLayout.setEnabled(true);
        rLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("S")) {
        sLayout.setEnabled(true);
        sLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("T")) {
        tLayout.setEnabled(true);
        tLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("U")) {
        uLayout.setEnabled(true);
        uLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("V")) {
        vLayout.setEnabled(true);
        vLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("W")) {
        wLayout.setEnabled(true);
        wLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("X")) {
        xLayout.setEnabled(true);
        xLayout.setAlpha(1.0f);
      }else if (token.equalsIgnoreCase("Y")) {
        yLayout.setEnabled(true);
        yLayout.setAlpha(1.0f);
      }
    }
  }

  /** Cargue el modelo y las etiquetas. */
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    try {
      // crea un nuevo ImageClassifierQuantizedMobileNet o un ImageClassifierFloatInception
      // clasificador = nuevo ImageClassifierQuantizedMobileNet (getActivity ());
      classifier = new ImageClassifierFloatInception(getActivity());
    } catch (IOException e) {
      Log.e(TAG, "No se pudo inicializar un clasificador de imágenes.", e);
    }
    startBackgroundThread();
  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    // Cuando la pantalla se apaga y se vuelve a encender, SurfaceTexture ya está
    // disponible, y no se llamará a "onSurfaceTextureAvailable". En ese caso, podemos abrir
    // una cámara y comenzar la vista previa desde aquí (de lo contrario, esperamos hasta que la superficie esté lista en
    // el SurfaceTextureListener).
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    super.onPause();
  }

  @Override
  public void onDestroy() {
    classifier.close();
    super.onDestroy();
  }

  /**
   * Configura variables de miembros relacionadas con la cámara.
   *
   * @param width El ancho del tamaño disponible para la vista previa de la cámara
   * @param height La altura del tamaño disponible para la vista previa de la cámara
   */
  private void setUpCameraOutputs(int width, int height) {
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      String camId = chooseCamera(manager);
      // La cámara frontal y trasera no está presente o no es accesible
      if (camId == null) {
        throw new IllegalStateException("Cámara no encontradad");
      }
      CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);

      StreamConfigurationMap map =
          characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      // // Para las capturas de imágenes fijas, utilizamos el tamaño más grande disponible.
      Size largest =
          Collections.max(
              Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
      imageReader =
          ImageReader.newInstance(
              largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/ 2);

      // Averigüe si necesitamos intercambiar dimensión para obtener el tamaño de vista previa en relación con el sensor
      // coordinar.
      int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
      // sin inspeccion ConstantConditions
      /* Orientación del sensor de la cámara */
      int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      boolean swappedDimensions = false;
      switch (displayRotation) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
          if (sensorOrientation == 90 || sensorOrientation == 270) {
            swappedDimensions = true;
          }
          break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
          if (sensorOrientation == 0 || sensorOrientation == 180) {
            swappedDimensions = true;
          }
          break;
        default:
          Log.e(TAG, "La rotación de la pantalla no es válida: " + displayRotation);
      }

      Point displaySize = new Point();
      activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
      int rotatedPreviewWidth = width;
      int rotatedPreviewHeight = height;
      int maxPreviewWidth = displaySize.x;
      int maxPreviewHeight = displaySize.y;

      if (swappedDimensions) {
        rotatedPreviewWidth = height;
        rotatedPreviewHeight = width;
        maxPreviewWidth = displaySize.y;
        maxPreviewHeight = displaySize.x;
      }

      if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
        maxPreviewWidth = MAX_PREVIEW_WIDTH;
      }

      if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
        maxPreviewHeight = MAX_PREVIEW_HEIGHT;
      }

      previewSize =
          chooseOptimalSize(
              map.getOutputSizes(SurfaceTexture.class),
              rotatedPreviewWidth,
              rotatedPreviewHeight,
              maxPreviewWidth,
              maxPreviewHeight,
              largest);

      // Ajustamos la relación de aspecto de TextureView al tamaño de la vista previa que elegimos.
      int orientation = getResources().getConfiguration().orientation;
      if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
      } else {
        textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
      }

      this.cameraId = camId;
    } catch (CameraAccessException e) {
      Log.e(TAG, "No se pudo acceder a la cámaraa", e);
    } catch (NullPointerException e) {
      // Actualmente, se lanza una NPE cuando se usa Camera2API pero no es compatible con
      // dispositivo que ejecuta este código.
      ErrorDialog.newInstance(getString(R.string.camera_error))
          .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }
  }

  /**
   * Elija la cámara de la lista de cámaras disponibles. La prioridad va a la cámara frontal, si es
   * Presente y luego use la cámara frontal, de lo contrario cambie a la cámara trasera.
   *
   * @param manager CameraManager
   * @return ID de la camara
   * @throws CameraAccessException
   */
  private String chooseCamera(CameraManager manager) throws CameraAccessException {
    String frontCameraId = null;
    String backCameraId = null;
    if (manager != null && manager.getCameraIdList().length > 0) {
      for (String camId : manager.getCameraIdList()) {
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
        StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && map != null) {
          if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            frontCameraId = camId;
            break;
          } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            backCameraId = camId;
          }
        }
      }

      return frontCameraId != null ? frontCameraId : backCameraId;
    }
    return null;
  }

  private String[] getRequiredPermissions() {
    return new String[] {Manifest.permission.CAMERA};
  }

  /** Abre la cámara especificada por{@link Camera2BasicFragment#cameraId}. */
  private void openCamera(int width, int height) {
    if (!checkedPermissions && !allPermissionsGranted()) {
      requestPermissions(getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
      return;
    } else {
      checkedPermissions = true;
    }
    setUpCameraOutputs(width, height);

    if (cameraId == null) {
      throw new IllegalStateException("No hay cámara frontal disponible.");
    }

    configureTransform(width, height);
    Activity activity = getActivity();
    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Tiempo de espera para bloquear la apertura de la cámara.");
      }

      if (!allPermissionsGranted()) {
        // TODO: Considere llamar
        //    ActivityCompat#requestPermissions
        // aquí para solicitar los permisos que faltan y luego anular
        // public void onRequestPermissionsResult (int requestCode, String [] permisos,
        // int [] grantResults)
        // para manejar el caso donde el usuario otorga el permiso. Ver la documentación
        // para ActivityCompat # requestPermissions para más detalles.
        return;
      }

      manager.openCamera(cameraId, stateCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, "No se pudo abrir la cámara", e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrumpido al intentar bloquear la apertura de la cámara.", e);
    }
  }

  private boolean allPermissionsGranted() {
    for (String permission : getRequiredPermissions()) {
      if (getContext().checkPermission(permission, Process.myPid(), Process.myUid())
          != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  /** Cierra la corriente {@link CameraDevice}. */
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != imageReader) {
        imageReader.close();
        imageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrumpida al intentar bloquear el cierre de la cámara.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  /** Inicia un hilo de fondo y su {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
    synchronized (lock) {
      runClassifier = true;
    }
    backgroundHandler.post(periodicClassify);
  }

  /**Detiene el hilo de fondo y su {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
      synchronized (lock) {
        runClassifier = false;
      }
    } catch (InterruptedException e) {
      Log.e(TAG, "Interrumpido al detener el hilo de fondo\n", e);
    }
  }

  /** Toma fotos y clasifícalas periódicamente. */
  private Runnable periodicClassify =
      new Runnable() {
        @Override
        public void run() {
          synchronized (lock) {
            if (runClassifier) {
              classifyFrame();
            }
          }
          backgroundHandler.post(periodicClassify);
        }
      };

  /** Crea una nueva {@link CameraCaptureSession} para la vista previa de la cámara.*/
  private void createCameraPreviewSession() {
    try {
      SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // Configuramos el tamaño del búfer predeterminado para que sea el tamaño de la vista previa de la cámara que queremos.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // Esta es la superficie de salida que necesitamos para iniciar la vista previa.
      Surface surface = new Surface(texture);

      // Configuramos CaptureRequest.Builder con la salida Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      // Aquí, creamos una CameraCaptureSession para la vista previa de la cámara.
      cameraDevice.createCaptureSession(
          Arrays.asList(surface),
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
              // La camara ya esta cerrada
              if (null == cameraDevice) {
                return;
              }

              // Cuando la sesión está lista, comenzamos a mostrar la vista previa.
              captureSession = cameraCaptureSession;
              try {
                // El enfoque automático debe ser continuo para la vista previa de la cámara.
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                // Finalmente, comenzamos a mostrar la vista previa de la cámara.
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(
                    previewRequest, captureCallback, backgroundHandler);
              } catch (CameraAccessException e) {
                Log.e(TAG, "No se pudo configurar la configuración para capturar la cámara", e);
              }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
              showToast("Failed");
            }
          },
          null);
    } catch (CameraAccessException e) {
      Log.e(TAG, "No se pudo obtener una vista previa de la cámara", e);
    }
  }

  /**
   * Configura lo necesario {@link android.graphics.Matrix} transformación a `textureView`. Esta
   * se debe llamar al método después de determinar el tamaño de vista previa de la cámara en setUpCameraOutputs y
   * también el tamaño de `textureView` es fijo.
   *
   * @param viewWidth El ancho de `textureView`
   * @param viewHeight La altura de `textureView`
   */
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = getActivity();
    if (null == textureView || null == previewSize || null == activity) {
      return;
    }
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale =
          Math.max(
              (float) viewHeight / previewSize.getHeight(),
              (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    textureView.setTransform(matrix);
  }

  /** Classifies a frame from the preview stream. */
  private void classifyFrame() {
    if (classifier == null || getActivity() == null || cameraDevice == null) {
      showToast("Clasificador no inicializado o contexto no válido.");
      return;
    }
    SpannableStringBuilder textToShow = new SpannableStringBuilder();

    Bitmap origionalBitmal = textureView.getBitmap();

    Bitmap bitmap = ThumbnailUtils.extractThumbnail(origionalBitmal, 224, 224);

    //        Bitmap bitmap = textureView.getBitmap(classifier.getImageSizeX(),
    // classifier.getImageSizeY());
    classifier.classifyFrame(bitmap, textToShow);
    bitmap.recycle();

    Log.e("amlan", textToShow.toString());

    if (textToShow.toString().indexOf(":") != -1) {
      String token = textToShow.toString().substring(0, textToShow.toString().indexOf(":"));
      Activity activity = getActivity();
      activity.runOnUiThread(
          new Runnable() {
            @Override
            public void run() {
              highLightDirectionButton(token);
            }
          });
    }

    showToast(textToShow);
  }

  private void highLightDirectionButton(String token) {

    if (lastSelectedGesture != null && !token.equalsIgnoreCase(lastSelectedGesture)) {
      if (lastSelectedGesture.equalsIgnoreCase("A")) {
        aLayout.setBackgroundResource(R.drawable.base);
      } else if (lastSelectedGesture.equalsIgnoreCase("B")) {
        bLayout.setBackgroundResource(R.drawable.base);
      } else if (lastSelectedGesture.equalsIgnoreCase("C")) {
        cLayout.setBackgroundResource(R.drawable.base);
      } else if (lastSelectedGesture.equalsIgnoreCase("D")) {
        dLayout.setBackgroundResource(R.drawable.base);
      } else if (lastSelectedGesture.equalsIgnoreCase("E")) {
        eLayout.setBackgroundResource(R.drawable.base);
      } else if (lastSelectedGesture.equalsIgnoreCase("F")) {
        fLayout.setBackgroundResource(R.drawable.base);
      } else if (lastSelectedGesture.equalsIgnoreCase("G")) {
        gLayout.setBackgroundResource(R.drawable.base);
      } else if (lastSelectedGesture.equalsIgnoreCase("H")) {
        hLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("I")) {
        iLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("K")) {
        kLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("L")) {
        lLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("M")) {
        mLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("N")) {
        nLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("O")) {
        oLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("P")) {
        pLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("Q")) {
        qLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("R")) {
        rLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("S")) {
        sLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("T")) {
        tLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("U")) {
        uLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("V")) {
        vLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("W")) {
        wLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("X")) {
        xLayout.setBackgroundResource(R.drawable.base);
      }else if (lastSelectedGesture.equalsIgnoreCase("Y")) {
        yLayout.setBackgroundResource(R.drawable.base);
      }
    }

    if (lastSelectedGesture == null || !lastSelectedGesture.equalsIgnoreCase(token)) {

      if (token.equalsIgnoreCase("A")) {
        if (aLayout.isEnabled()) aLayout.setBackgroundResource(R.drawable.selection_base);
      } else if (token.equalsIgnoreCase("B")) {
        if (bLayout.isEnabled()) bLayout.setBackgroundResource(R.drawable.selection_base);
      } else if (token.equalsIgnoreCase("C")) {
        if (cLayout.isEnabled()) cLayout.setBackgroundResource(R.drawable.selection_base);
      } else if (token.equalsIgnoreCase("D")) {
        if (dLayout.isEnabled()) dLayout.setBackgroundResource(R.drawable.selection_base);
      } else if (token.equalsIgnoreCase("E")) {
        if (eLayout.isEnabled())
          eLayout.setBackgroundResource(R.drawable.selection_base);
      } else if (token.equalsIgnoreCase("F")) {
        if (fLayout.isEnabled())
          fLayout.setBackgroundResource(R.drawable.selection_base);
      } else if (token.equalsIgnoreCase("G")) {
        if (gLayout.isEnabled())
          gLayout.setBackgroundResource(R.drawable.selection_base);
      } else if (token.equalsIgnoreCase("H")) {
        if (hLayout.isEnabled())
          hLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("I")) {
        if (iLayout.isEnabled())
          iLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("K")) {
        if (kLayout.isEnabled())
          kLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("L")) {
        if (lLayout.isEnabled())
          lLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("M")) {
        if (mLayout.isEnabled())
          mLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("N")) {
        if (nLayout.isEnabled())
          nLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("O")) {
        if (oLayout.isEnabled())
          oLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("P")) {
        if (pLayout.isEnabled())
          pLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("Q")) {
        if (qLayout.isEnabled())
          qLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("R")) {
        if (rLayout.isEnabled())
          rLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("S")) {
        if (sLayout.isEnabled())
          sLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("T")) {
        if (tLayout.isEnabled())
          tLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("U")) {
        if (uLayout.isEnabled())
          uLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("V")) {
        if (vLayout.isEnabled())
          vLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("W")) {
        if (wLayout.isEnabled())
          wLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("X")) {
        if (xLayout.isEnabled())
          xLayout.setBackgroundResource(R.drawable.selection_base);
      }else if (token.equalsIgnoreCase("Y")) {
        if (yLayout.isEnabled())
          yLayout.setBackgroundResource(R.drawable.selection_base);
      }

      lastSelectedGesture = token;
    }
  }

  /** Compara dos{@code Size}s en función de sus áreas.*/
  private static class CompareSizesByArea implements Comparator<Size> {

    @Override
    public int compare(Size lhs, Size rhs) {
      // Lanzamos aquí para asegurarnos de que las multiplicaciones no se desborden
      return Long.signum(
          (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  /** Muestra un cuadro de diálogo de mensaje de error. */
  public static class ErrorDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                  activity.finish();
                }
              })
          .create();
    }
  }
}
