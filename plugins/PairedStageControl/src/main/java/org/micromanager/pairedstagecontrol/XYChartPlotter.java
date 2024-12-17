package org.micromanager.pairedstagecontrol;

import java.awt.Color;
import java.awt.geom.Ellipse2D;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


/**
 * Class to draw Stage1/Stage2 positions and the line fit
 * in the LightSheetManager plugin.
 */
public class XYChartPlotter {
   private final XYSeriesCollection dataset_;
   private final JFreeChart chart_;
   private ChartPanel chartPanel_;

   /**
    * Constructor.
    *
    * @param title Title of the plot
    * @param xAxisLabel X-Axis label
    * @param yAxisLabel Y-Axis label
    */
   public XYChartPlotter(String title, String xAxisLabel, String yAxisLabel) {
      // Initialize the dataset
      dataset_ = new XYSeriesCollection();

      // Create the chart
      chart_ = ChartFactory.createScatterPlot(
            title,          // Chart title
            xAxisLabel,     // X-Axis Label
            yAxisLabel,     // Y-Axis Label
            dataset_,        // Dataset
            PlotOrientation.VERTICAL,
            true,           // Show legend
            true,           // Use tooltips
            false           // Configure chart to generate URLs?
      );
   }

   /**
    * Add a series of XY data to the plot.
    *
    * @param seriesName Name of the data series
    * @param data List of ReferencePoints (which are xy data)
    */
   public void addSeries(String seriesName, ReferencePointList data) {
      // Create a new XY series from the ReferencePointList
      XYSeries series = new XYSeries(seriesName);
      for (int i = 0; i < data.getNumberOfPoints(); i++) {
         series.add(data.getPoint(i).getStagePosition1(), data.getPoint(i).getStagePosition2());
      }

      // Replace series to the dataset
      dataset_.removeAllSeries();
      dataset_.addSeries(series);
      renderPlot();
      chart_.fireChartChanged();
   }

   /**
    * Add a fitted line to the plot using linear regression.
    */
   public void addFittedLine(double offset, double slope) {
      // Create a fitted line series
      XYSeries firstSeries = dataset_.getSeries(0);
      XYSeries fittedLine = new XYSeries("Fitted Line");
      double minX = firstSeries.getMinX();
      double maxX = firstSeries.getMaxX();

      // Add two points to define the line
      fittedLine.add(minX, offset + slope * minX);
      fittedLine.add(maxX, offset + slope * maxX);

      dataset_.addSeries(fittedLine);
      renderPlot();

   }

   private void renderPlot() {
      // Customize the renderer to make the fitted line distinct
      final XYPlot plot = chart_.getXYPlot();
      XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
      renderer.setSeriesLinesVisible(0, true);
      renderer.setSeriesShapesVisible(0, true);
      renderer.setSeriesShape(0, new Ellipse2D.Double(-3d, -3d, 6d, 6d));
      renderer.setSeriesPaint(0, Color.RED);
      renderer.setSeriesLinesVisible(1, true);
      renderer.setSeriesShapesVisible(1, false);
      renderer.setSeriesPaint(1, Color.BLACK);
      plot.setRenderer(renderer);

   }

   /**
    * Get the ChartPanel for adding to a JFrame.
    *
    * @return ChartPanel containing the chart
    */
   public ChartPanel getChartPanel() {
      if (chartPanel_ == null) {
         chartPanel_ = new ChartPanel(chart_);
      }
      return chartPanel_;
   }

}