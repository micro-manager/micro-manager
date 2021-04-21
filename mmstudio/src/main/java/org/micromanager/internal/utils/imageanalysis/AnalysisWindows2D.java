package org.micromanager.internal.utils.imageanalysis;

/**
 * Code to generate Making Windows (https://en.wikipedia.org/wiki/Window_function) for instance
 * useful when masking edges to avoid Fourier border artefacts
 *
 * @author nico
 */
public class AnalysisWindows2D {

  public static float[][] hanWindow(int size) {
    float[] hanArray = new float[size];

    for (int i = 0; i < size; i++) {
      hanArray[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / size)));
    }

    float[][] han2DArray = new float[size][size];
    // non-isotropic, separable way to compute the 2D version
    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size; y++) {
        han2DArray[x][y] = hanArray[x] * hanArray[y];
      }
    }
    return han2DArray;
  }

  /**
   * returns a hanWindow as a single array.
   *
   * @param edgeSize
   * @return
   */
  public static float[] hanWindow1DA(int edgeSize) {
    float[] han1DArray = new float[edgeSize];

    for (int i = 0; i < edgeSize; i++) {
      han1DArray[i] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / edgeSize)));
    }

    float[] han2DArray = new float[edgeSize * edgeSize];
    // non-isotropic, separable way to compute the 2D version
    for (int x = 0; x < edgeSize; x++) {
      for (int y = 0; y < edgeSize; y++) {
        han2DArray[x + y * edgeSize] = han1DArray[x] * han1DArray[y];
      }
    }
    return han2DArray;
  }
}
