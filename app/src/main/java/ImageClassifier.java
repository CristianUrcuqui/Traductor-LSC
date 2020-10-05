
package org.tensorflow.lite.examples.gesture;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.StringTokenizer;
import org.tensorflow.lite.Interpreter;

/** Clasifica imágenes con Tensorflow Lite. */
public abstract class ImageClassifier {
  // Display preferences
  private static final float GOOD_PROB_THRESHOLD = 0.3f;
  private static final int SMALL_COLOR = 0xffddaa88;

  /** Etiqueta para el{@link Log}. */
  private static final String TAG = "TfLiteCameraDemo";

  /** Número de resultados para mostrar en la interfaz de usuario.*/
  private static final int RESULTS_TO_SHOW = 3;

  /**Dimensiones de las entradas.*/
  private static final int DIM_BATCH_SIZE = 1;

  private static final int DIM_PIXEL_SIZE = 3;

  /* Búferes preasignados para almacenar datos de imágenes en formato. */
  private int[] intValues = new int[getImageSizeX() * getImageSizeY()];

  /**Una instancia de la clase de controlador para ejecutar la inferencia de modelos con Tensorflow Lite.*/
  protected Interpreter tflite;

  /** Etiquetas correspondientes a la salida del modelo de visión. */
  private List<String> labelList;

  /** Un ByteBuffer para contener datos de imagen, que se alimentarán en Tensorflow Lite como entradas. */
  protected ByteBuffer imgData = null;

  /** filtro de paso bajo de varias etapas* */
  private float[][] filterLabelProbArray = null;

  private static final int FILTER_STAGES = 3;
  private static final float FILTER_FACTOR = 0.4f;

  private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
      new PriorityQueue<>(
          RESULTS_TO_SHOW,
          new Comparator<Map.Entry<String, Float>>() {
            @Override
            public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
              return (o1.getValue()).compareTo(o2.getValue());
            }
          });

  /** Inicializa un {@code ImageClassifier}. */
  ImageClassifier(Activity activity) throws IOException {
    tflite = new Interpreter(loadModelFile(activity));
    labelList = loadLabelList(activity);
    imgData =
        ByteBuffer.allocateDirect(
            DIM_BATCH_SIZE
                * getImageSizeX()
                * getImageSizeY()
                * DIM_PIXEL_SIZE
                * getNumBytesPerChannel());
    imgData.order(ByteOrder.nativeOrder());
    filterLabelProbArray = new float[FILTER_STAGES][getNumLabels()];
    Log.d(TAG, "Creó un clasificador de imágenes de Tensorflow Lite.");
  }

  /** Clasifica un fotograma de la secuencia de vista previa. */
  void classifyFrame(Bitmap bitmap, SpannableStringBuilder builder) {
    printTopKLabels(builder);

    if (tflite == null) {
      Log.e(TAG, "El clasificador de imágenes no se ha inicializado; Omitido.");
      builder.append(new SpannableString("Clasificador no inicializado."));
    }
    convertBitmapToByteBuffer(bitmap);
    // Here's where the magic happens!!!
    long startTime = SystemClock.uptimeMillis();
    runInference();
    long endTime = SystemClock.uptimeMillis();
    Log.d(TAG, "Coste de tiempo para ejecutar la inferencia del modelo: " + Long.toString(endTime - startTime));

    // Smooth the results across frames.
    applyFilter();

    // Print the results.
    long duration = endTime - startTime;
    SpannableString span = new SpannableString(duration + " ms");
    span.setSpan(new ForegroundColorSpan(android.graphics.Color.LTGRAY), 0, span.length(), 0);
    builder.append(span);
  }

  void applyFilter() {
    int numLabels = getNumLabels();

    // Filtro de paso bajo `labelProbArray` en la primera etapa del filtro.
    for (int j = 0; j < numLabels; ++j) {
      filterLabelProbArray[0][j] +=
          FILTER_FACTOR * (getProbability(j) - filterLabelProbArray[0][j]);
    }
    // Filtro de paso bajo de cada etapa a la siguiente.
    for (int i = 1; i < FILTER_STAGES; ++i) {
      for (int j = 0; j < numLabels; ++j) {
        filterLabelProbArray[i][j] +=
            FILTER_FACTOR * (filterLabelProbArray[i - 1][j] - filterLabelProbArray[i][j]);
      }
    }

    // Copia la salida del filtro de la última etapa de nuevo a `labelProbArray`.
    for (int j = 0; j < numLabels; ++j) {
      setProbability(j, filterLabelProbArray[FILTER_STAGES - 1][j]);
    }
  }

  public void setUseNNAPI(Boolean nnapi) {
    if (tflite != null) tflite.setUseNNAPI(nnapi);
  }

  public void setNumThreads(int num_threads) {
    if (tflite != null) tflite.setNumThreads(num_threads);
  }

  /** Cierra tflite para liberar recursos. */
  public void close() {
    tflite.close();
    tflite = null;
  }

  /**Lee la lista de etiquetas de los activos. */
  private List<String> loadLabelList(Activity activity) throws IOException {
    List<String> labelList = new ArrayList<String>();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(activity.getAssets().open(getLabelPath())));
    String line;
    line = reader.readLine();
    reader.close();

    StringTokenizer tokenizer = new StringTokenizer(line, ",");
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      labelList.add(token);
    }
    return labelList;
  }

  /** Mapee en memoria el archivo del modelo en Activos. */
  private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
    AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  /** Escribe datos de imagen en un{@code ByteBuffer}. */
  private void convertBitmapToByteBuffer(Bitmap bitmap) {
    if (imgData == null) {
      return;
    }
    imgData.rewind();
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    // Convert the image to floating point.
    int pixel = 0;
    long startTime = SystemClock.uptimeMillis();
    for (int i = 0; i < getImageSizeX(); ++i) {
      for (int j = 0; j < getImageSizeY(); ++j) {
        final int val = intValues[pixel++];
        addPixelValue(val);
      }
    }
    long endTime = SystemClock.uptimeMillis();
    Log.d(TAG, "Coste de tiempo para poner valores en ByteBuffe`r: " + Long.toString(endTime - startTime));
  }

  /** Imprime etiquetas top-K, que se mostrarán en la interfaz de usuario como resultados. */
  private void printTopKLabels(SpannableStringBuilder builder) {
    for (int i = 0; i < getNumLabels(); ++i) {
      sortedLabels.add(
          new AbstractMap.SimpleEntry<>(labelList.get(i), getNormalizedProbability(i)));
      if (sortedLabels.size() > RESULTS_TO_SHOW) {
        sortedLabels.poll();
      }
    }

    final int size = sortedLabels.size();
    for (int i = 0; i < size; i++) {
      Map.Entry<String, Float> label = sortedLabels.poll();
      SpannableString span =
          new SpannableString(String.format("%s:  %4.2f\n", label.getKey(), label.getValue()));
      int color;
      // Make it white when probability larger than threshold.
      if (label.getValue() > GOOD_PROB_THRESHOLD) {
        color = Color.BLACK;
      } else {
        color = SMALL_COLOR;
      }
      // Make first item bigger.
      if (i == size - 1) {
        float sizeScale = (i == size - 1) ? 1.75f : 0.8f;
        span.setSpan(new RelativeSizeSpan(sizeScale), 0, span.length(), 0);
      }
      span.setSpan(new ForegroundColorSpan(color), 0, span.length(), 0);
      builder.insert(0, span);
    }
  }

  /**
   *Obtenga el nombre del archivo de modelo almacenado en Activos.
   *
   * @return
   */
  protected abstract String getModelPath();

  /**
   * Obtenga el nombre del archivo de etiqueta almacenado en Activos.
   *
   * @return
   */
  protected abstract String getLabelPath();

  /**
   * Obtenga el tamaño de la imagen a lo largo del eje x.
   *
   * @return
   */
  protected abstract int getImageSizeX();

  /**
   * Obtenga el tamaño de la imagen a lo largo del eje y.
   *
   * @return
   */
  protected abstract int getImageSizeY();

  /**
   * Obtenga el número de bytes que se utiliza para almacenar un valor de canal de color único.
   *
   * @return
   */
  protected abstract int getNumBytesPerChannel();

  /**
   * Agregar pixelValue a byteBuffer.
   *
   * @param pixelValue
   */
  protected abstract void addPixelValue(int pixelValue);

  /**
   * Leer el valor de probabilidad para la etiqueta especificada Este es el valor original tal como estaba
   * leer de la salida de la red o el valor actualizado después de que se aplicó el filtro.
   *
   * @param labelIndex
   * @return
   */
  protected abstract float getProbability(int labelIndex);

  /**
   * Establecer el valor de probabilidad para la etiqueta especificada.
   *
   * @param labelIndex
   * @param value
   */
  protected abstract void setProbability(int labelIndex, Number value);

  /**
   * Obtenga el valor de probabilidad normalizado para la etiqueta especificada. Este es el valor final ya que
   * se mostrará al usuario.
   *
   * @return
   */
  protected abstract float getNormalizedProbability(int labelIndex);

  /**
   * Ejecute la inferencia utilizando la entrada preparada en {@link #imgData}. Posteriormente, el resultado será
   * proporcionado por getProbability ().
   *
   * <p> Este método adicional es necesario, porque no tenemos una base común para diferentes
   * tipos de datos primitivos.
   */
  protected abstract void runInference();

  /**
   * Obtenga el número total de etiquetas.
   *
   * @return
   */
  protected int getNumLabels() {
    return labelList.size();
  }
}
