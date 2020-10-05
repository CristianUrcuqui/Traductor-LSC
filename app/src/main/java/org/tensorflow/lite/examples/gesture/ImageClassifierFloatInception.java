

package org.tensorflow.lite.examples.gesture;

import android.app.Activity;
import java.io.IOException;

/**
 * This classifier works with the Inception-v3 slim model. It applies floating point inference
 * rather than using a quantized model.
 */
public class ImageClassifierFloatInception extends ImageClassifier {

  /** La red de inicio requiere una normalización adicional de la entrada utilizada. */
  private static final float IMAGE_MEAN = 1.0f;

  private static final float IMAGE_STD = 127.0f;

  /**
   * Una matriz para contener los resultados de la inferencia, para ser alimentados a Tensorflow Lite como salidas. Esto no es parte
   * de la superclase, porque aquí necesitamos una matriz primitiva.
   */
  private float[][] labelProbArray = null;

  /**
   * Inicializa un {@code ImageClassifier}.
   *
   * @param activity
   */
  ImageClassifierFloatInception(Activity activity) throws IOException {
    super(activity);
    labelProbArray = new float[1][getNumLabels()];
  }

  @Override
  protected String getModelPath() {
    // you can download this file from
    return "model.tflite";
  }

  @Override
  protected String getLabelPath() {
    return "labels.txt";
  }

  @Override
  protected int getImageSizeX() {
    return 224;
  }

  @Override
  protected int getImageSizeY() {
    return 224;
  }

  @Override
  protected int getNumBytesPerChannel() {
    // a 32bit float value requires 4 bytes
    return 4;
  }

  @Override
  protected void addPixelValue(int pixelValue) {

    imgData.putFloat((((pixelValue >> 16) & 0xFF) / IMAGE_STD) - IMAGE_MEAN);
    imgData.putFloat((((pixelValue >> 8) & 0xFF) / IMAGE_STD) - IMAGE_MEAN);
    imgData.putFloat(((pixelValue & 0xFF) / IMAGE_STD) - IMAGE_MEAN);
  }

  @Override
  protected float getProbability(int labelIndex) {
    return labelProbArray[0][labelIndex];
  }

  @Override
  protected void setProbability(int labelIndex, Number value) {
    labelProbArray[0][labelIndex] = value.floatValue();
  }

  @Override
  protected float getNormalizedProbability(int labelIndex) {
    // TODO the following value isn't in [0,1] yet, but may be greater. Why?
    return getProbability(labelIndex);
  }

  @Override
  protected void runInference() {
    tflite.run(imgData, labelProbArray);
  }
}
