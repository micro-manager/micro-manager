///////////////////////////////////////////////////////////////////////////////
//PROJECT:       PWS Plugin
//
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nick Anthony, 2021
//
// COPYRIGHT:    Northwestern University, 2021
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.display.inspector.internal.panels.sharpnessinspector.ui;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.display.inspector.internal.panels.sharpnessinspector.SharpnessInspectorController;
import org.micromanager.display.inspector.internal.panels.sharpnessinspector.SharpnessInspectorPlugin;
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;

/**
 *
 * @author nick
 */
public class SharpnessInspectorPanel extends JPanel {
    
   private static final String SERIES_NAME = "DATA";

   private final JFreeChart zChart = ChartFactory.createXYLineChart(
          null, //title
          "Z", // xlabel
          "Sharpness (A.U.)", // ylabel
          new XYSeriesCollection(new XYSeries(SERIES_NAME, true, false)),
          PlotOrientation.VERTICAL,
          false, // legend
          false, //tooltips
          false //urls
   );
    
   private final JFreeChart tChart = ChartFactory.createXYLineChart(
              null, //title
          "Time", // xlabel
         "Sharpness (A.U.)", // ylabel
          new XYSeriesCollection(new XYSeries(SERIES_NAME, true, false)),
          PlotOrientation.VERTICAL,
          false, // legend
          false, //tooltips
          false //urls
   );

   private final ChartPanel chartPanel = new ChartPanel(
         tChart,
         200, // int width,
         200, // int height,
         100, // int minimumDrawWidth,
         100, // int minimumDrawHeight,
         10000, // int maximumDrawWidth,
         10000, // int maximumDrawHeight,
         true, // boolean useBuffer,
         true, // boolean properties,
         true, // boolean copy,
         true, // boolean save,
         true, // boolean print,
         true, // boolean zoom,
         true // boolean tooltips
   );
        
   private final JButton resetButton = new JButton("Reset Plot");
   private final JButton scanButton = new JButton("Scan...");

   private final List<SharpnessInspectorController.RequestScanListener> scanRequestedListeners
           = new ArrayList<>();
   private final ScanDialog scanDlg = new ScanDialog();
   private final JComboBox<SharpnessInspectorController.PlotMode> plotModeBox
           = new JComboBox<>(new DefaultComboBoxModel<>(SharpnessInspectorController
           .PlotMode.values()));
   private final JComboBox<ImgSharpnessAnalysis.Method> evaluationMode
           = new JComboBox<>(new DefaultComboBoxModel<>(ImgSharpnessAnalysis.Method.values()));
   private final JFreeTextOverlay noRoiOverlay = new JFreeTextOverlay("No Roi Drawn");
    
   private final XYSeries zDataSeries = ((XYSeriesCollection) zChart.getXYPlot().getDataset())
           .getSeries(SERIES_NAME);
   private final XYSeries tDataSeries = ((XYSeriesCollection) tChart.getXYPlot().getDataset())
           .getSeries(SERIES_NAME);

   private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
   public SharpnessInspectorPanel() {
      super(new MigLayout("fill, nogrid"));

      resetButton.addActionListener((evt) -> {
         this.clearData();
      });

      scanButton.addActionListener((evt) -> {
         this.scanDlg.setVisible(true);
      });

      // A crosshair overlay to display the current z position.
      this.zChart.getXYPlot().setDomainCrosshairVisible(true);
      // black crosshair
      this.zChart.getXYPlot().setDomainCrosshairPaint(new Color(0, 0, 0));
      // A crosshair overlay to display the current sharpness.
      this.zChart.getXYPlot().setRangeCrosshairVisible(true);
      // black crosshair
      this.zChart.getXYPlot().setRangeCrosshairPaint(new Color(0, 0, 0));
      // hide the values for `time`
      this.tChart.getXYPlot().getDomainAxis().setTickLabelsVisible(false);
      // An overlay that tells the user that there needs to be a roi selected.
      this.chartPanel.addOverlay(noRoiOverlay);
      // disable the right-click menu
      this.chartPanel.setPopupMenu(null);
      // disable zooming by click-drag
      this.chartPanel.setDomainZoomable(false);
      this.chartPanel.setRangeZoomable(false);

      //make plot background transparent
      Color trans = new Color(0xFF, 0xFF, 0xFF, 0);
      zChart.setBackgroundPaint(trans);
      zChart.getXYPlot().setBackgroundPaint(trans);
      //Don't always include 0 in the vertical autoranging.
      ((NumberAxis) zChart.getXYPlot().getRangeAxis()).setAutoRangeIncludesZero(false);
      tChart.setBackgroundPaint(trans);
      tChart.getXYPlot().setBackgroundPaint(trans);
      //Don't always include 0 in the vertical autoranging.
      ((NumberAxis) tChart.getXYPlot().getRangeAxis()).setAutoRangeIncludesZero(false);

      this.plotModeBox.addItemListener((evt) -> {
         SharpnessInspectorController.PlotMode mode = (SharpnessInspectorController.PlotMode)
                 this.plotModeBox.getSelectedItem();
         this.setPlotMode(mode);
      });

      evaluationMode.addActionListener((evt) -> {
         this.pcs.firePropertyChange("evalMethod", null,
                 (ImgSharpnessAnalysis.Method) evaluationMode.getSelectedItem());
      });
        
      JButton infoButton = new JButton("?");
      infoButton.addActionListener((evt) -> {
         JOptionPane.showMessageDialog(infoButton,
                  "<html><body><p style='width: 200px;'>" + SharpnessInspectorPlugin.README
                          + " </p></body></html>",  // Wrapping in HTML for word wrapping.
                  "Plugin Information",
                  JOptionPane.INFORMATION_MESSAGE);
      });

      super.add(chartPanel, "wrap, spanx, grow, pushy");
      super.add(scanButton);
      super.add(resetButton);
      super.add(new JLabel("X axis:"));
      super.add(plotModeBox, "wrap");
      super.add(new JLabel("Method:"));
      super.add(evaluationMode);
      super.add(infoButton);
   }

   @Override
   public void addPropertyChangeListener(PropertyChangeListener listener) {
      this.pcs.addPropertyChangeListener(listener);
   }
    
   @Override
   public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
      this.pcs.addPropertyChangeListener(property, listener);
   }
    
   public void setValue(double z, double time, double sharpness) {
      //Add an XY value to the plot. if the x value already exists the old value will be replaced.
      this.zDataSeries.addOrUpdate(z, sharpness);
      //Set the vertical crosshair to the current sharpness value.
      this.zChart.getXYPlot().setRangeCrosshairValue(sharpness);
      this.setZPos(z);

      //Find the index associated with data that is more than `timeLimit` seconds old and delete it.
      double timeLimit = 30;
      int i;
      for (i = 0; i < tDataSeries.getItemCount(); i++) {
         Double t = (Double) tDataSeries.getX(i);
         if (((time - t) / 1000) < timeLimit) {
            break;
         }
      }
      if (i == tDataSeries.getItemCount()) {
         tDataSeries.clear();
      } else if (i > 0) {
         tDataSeries.delete(0, i);
      }
      this.tDataSeries.addOrUpdate(time, sharpness);
   }
    
   public void setZPos(double z) {
      //Set the currect z position for the cursor to be set to.
      this.zChart.getXYPlot().setDomainCrosshairValue(z);
   }
    
   public void clearData() {
      this.zDataSeries.clear();
      this.tDataSeries.clear();
   }
    
   public void setRoiSelected(boolean hasRoi) {
      // If there is no roi selected use this method to make an overlay visible that tells
      // this to the user.
      if (!hasRoi) {
         if (!this.noRoiOverlay.isVisible()) {
            this.noRoiOverlay.setVisible(true);
            this.repaint(); // Make sure the change in visibility is rendered.
         }
      } else {
         if (this.noRoiOverlay.isVisible()) {
            this.noRoiOverlay.setVisible(false);
            this.repaint(); // Make sure the change in visibility is rendered.
         }
      }
   }
    
   public void addScanRequestedListener(SharpnessInspectorController.RequestScanListener listener) {
      //Add a listener that will be fired when the `scan` button is pressed.
      this.scanRequestedListeners.add(listener);
   }

   public void setPlotMode(SharpnessInspectorController.PlotMode mode) {
      switch (mode) {
         case Time:
            this.chartPanel.setChart(tChart);
            break;
         case Z:
            this.chartPanel.setChart(zChart);
            break;
         default:
            break;
      }
      if (plotModeBox.getSelectedItem() != mode) {
         plotModeBox.setSelectedItem(mode);
      }
      this.pcs.firePropertyChange("plotMode", null, mode);
   }
        
   private class ScanDialog extends JDialog {
      private final JFormattedTextField interval =
              new JFormattedTextField(NumberFormat.getNumberInstance());
      private final JFormattedTextField range =
              new JFormattedTextField(NumberFormat.getNumberInstance());
      private final JButton startButton = new JButton("Start");

      public ScanDialog() {
         super(SwingUtilities.getWindowAncestor(SharpnessInspectorPanel.this));
         this.setLayout(new MigLayout());
         this.setLocationRelativeTo(SharpnessInspectorPanel.this);
         this.setTitle("Scan Parameters");

         this.interval.setColumns(5);
         this.interval.setValue(0.1);
         this.range.setColumns(5);
         this.range.setValue(5);

         this.startButton.addActionListener((evt) -> {
            SharpnessInspectorController.RequestScanEvent event =
                    new SharpnessInspectorController.RequestScanEvent(this,
                            ((Number) interval.getValue()).doubleValue(),
                            ((Number) range.getValue()).doubleValue());
            for (SharpnessInspectorController.RequestScanListener listener :
                    SharpnessInspectorPanel.this.scanRequestedListeners) {
               listener.actionPerformed(event);
            }
            this.setVisible(false);
         });

         this.add(new JLabel("Interval (um):"));
         this.add(interval, "wrap");
         this.add(new JLabel("Range (um):"));
         this.add(range, "wrap");
         this.add(startButton, "spanx, align center");
         this.pack();
      }
   }
    
   public void setEvaluationMethod(ImgSharpnessAnalysis.Method method) {
      evaluationMode.setSelectedItem(method);
   }
}