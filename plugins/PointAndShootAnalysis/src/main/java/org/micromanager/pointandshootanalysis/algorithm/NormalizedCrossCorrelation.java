package org.micromanager.pointandshootanalysis.algorithm;

import ij.process.ShortProcessor;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes Normalized Cross Correlation in the spatial domain Is very slow, and results are not
 * convincing
 *
 * @author nico
 */
public class NormalizedCrossCorrelation {
  private final float[] normalizedTemplate_;
  private final Point templateDim_;
  private final double templateStdDev_;

  public NormalizedCrossCorrelation(ShortProcessor template) {
    short[] pixels = (short[]) template.getPixels();
    templateDim_ = new Point(template.getWidth(), template.getHeight());

    double avg = shortAverage(pixels);

    normalizedTemplate_ = normalizeShorts(pixels, (float) avg);

    templateStdDev_ = stdDevFromNormalizedData(normalizedTemplate_);
  }

  /**
   * Performs Zero-normalized cross-correlation in the spatial domain using the normalized template
   * from the constructor
   *
   * @param target image target to which we match our template
   * @param center center position (in pixels) in the target around we cross-correlate
   * @param range in pixels over which we will do cross correlate
   * @return position (in pixels) in the target where we find the highest cross-correlation
   */
  public Point correlate(ShortProcessor target, Point center, Point range) {

    target.snapshot();
    // TODO: check dimensions
    Point halfTemplateDim_ = new Point(templateDim_.x / 2, templateDim_.y / 2);
    Point startPos = new Point(center.x - halfTemplateDim_.x, center.y - halfTemplateDim_.y);
    Map<Point, Float> result = new HashMap<Point, Float>();

    for (int x = startPos.x - range.x; x <= startPos.x + range.x; x++) {
      for (int y = startPos.y - range.y; y <= startPos.y + range.y; y++) {
        target.setRoi(x, y, templateDim_.x, templateDim_.y);
        target.crop();
        short[] targetPixels = (short[]) target.getPixels();
        double targetAvg = shortAverage(targetPixels);
        float[] tmpTarget = normalizeShorts(targetPixels, (float) targetAvg);
        double targetStdDev = stdDevFromNormalizedData(tmpTarget);
        float cc = 0.0f;
        for (int i = 0; i < normalizedTemplate_.length; i++) {
          cc += normalizedTemplate_[i] * tmpTarget[i];
        }
        cc = cc / (float) (targetStdDev * templateStdDev_);
        Point t = new Point(x, y);
        result.put(t, cc);
        target.reset();
      }
    }

    float max = -Float.MAX_VALUE;
    Point maxPoint = null;
    for (Map.Entry<Point, Float> entry : result.entrySet()) {
      if (entry.getValue() > max) {
        maxPoint = entry.getKey();
      }
    }

    return new Point(maxPoint.x + halfTemplateDim_.x, maxPoint.y + halfTemplateDim_.y);
  }

  public static double shortAverage(short[] data) {
    double avg = 0.0;
    for (short d : data) {
      avg += d;
    }
    return (avg / data.length);
  }

  public static float[] normalizeShorts(short[] data, float avg) {
    float[] result = new float[data.length];
    for (int i = 0; i < data.length; i++) {
      result[i] = data[i] - avg;
    }
    return result;
  }

  public static double stdDevFromNormalizedData(float[] data) {
    double stdDev = 0.0;
    for (float p : data) {
      stdDev += (p) * (p);
    }
    return Math.sqrt(stdDev / data.length);
  }
}
