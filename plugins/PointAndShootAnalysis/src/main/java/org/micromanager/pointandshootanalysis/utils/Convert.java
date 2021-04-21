package org.micromanager.pointandshootanalysis.utils;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.jfree.data.xy.XYSeries;

/** @author nico */
public class Convert {

  public static List<Point2D> chartDataToPointList(XYSeries chartData) {
    List<Point2D> output = new ArrayList<>(chartData.getItemCount());
    for (int i = 0; i < chartData.getItemCount(); i++) {
      output.add(
          new Point2D.Double(chartData.getX(i).doubleValue(), chartData.getY(i).doubleValue()));
    }
    return output;
  }
}
