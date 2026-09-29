package org.micromanager.plugins.isim;

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.Studio;
import org.micromanager.events.PropertyChangedEvent;

/**
 * Tab panel showing live JFreeChart plots of the iSIM waveforms.
 * Plots update automatically when device adapter properties change.
 */
public class WaveformPanel extends JPanel {
   private static final int CHART_HEIGHT_PX = 130;

   private final Studio studio_;
   private final String deviceLabel_;

   // One series per waveform channel; updated in place on each refresh
   private final XYSeries cameraSeries_;
   private final XYSeries galvoSeries_;
   private final XYSeries blankingSeries_;
   private final XYSeries[] modInSeries_ = new XYSeries[DeviceAdapterProperties.N_MOD_IN];

   // Chart panels for MOD IN channels (may be hidden when channel is not active)
   private final ChartPanel[] modInChartPanels_ = new ChartPanel[DeviceAdapterProperties.N_MOD_IN];

   // Chart panels for galvo and blanking (hidden when no MOD IN channels are enabled)
   private final ChartPanel galvoChartPanel_;
   private final ChartPanel blankingChartPanel_;

   // Inner panel holding all chart panels; hidemode 3 collapses invisible components
   private final JPanel chartsPanel_;

   public WaveformPanel(Studio studio, String deviceLabel) {
      studio_ = studio;
      deviceLabel_ = deviceLabel;

      setLayout(new MigLayout("fill, insets 0"));

      chartsPanel_ = new JPanel(new MigLayout("fillx, insets 8, gap 4, hidemode 3"));

      // Camera trigger
      cameraSeries_ = new XYSeries("Camera Trigger", false);
      chartsPanel_.add(makeChartPanel("Camera Trigger", cameraSeries_),
            "growx, pushx, wrap");

      // Galvo
      galvoSeries_ = new XYSeries("Galvo", false);
      galvoChartPanel_ = makeChartPanel("Galvo", galvoSeries_);
      chartsPanel_.add(galvoChartPanel_, "growx, pushx, wrap");

      // AOTF Blanking
      blankingSeries_ = new XYSeries("AOTF Blanking", false);
      blankingChartPanel_ = makeChartPanel("AOTF Blanking", blankingSeries_);
      chartsPanel_.add(blankingChartPanel_, "growx, pushx, wrap");

      // AOTF MOD IN 1-4 (hidden until configured and enabled)
      for (int i = 0; i < DeviceAdapterProperties.N_MOD_IN; i++) {
         modInSeries_[i] = new XYSeries("AOTF MOD IN " + (i + 1), false);
         ChartPanel cp = makeChartPanel("AOTF MOD IN " + (i + 1), modInSeries_[i]);
         cp.setVisible(false);
         modInChartPanels_[i] = cp;
         chartsPanel_.add(cp, "growx, pushx, wrap");
      }

      add(chartsPanel_, "grow, push");

      studio_.events().registerForEvents(this);
      updatePlots();
   }

   /**
    * Called when the window is closing. Unregisters from the event bus.
    */
   public void close() {
      studio_.events().unregisterForEvents(this);
   }

   @Subscribe
   public void onPropertyChanged(PropertyChangedEvent event) {
      if (!event.getDevice().equals(deviceLabel_)) {
         return;
      }
      SwingUtilities.invokeLater(this::updatePlots);
   }

   private void updatePlots() {
      WaveformParams p = WaveformParams.fromDevice(studio_, deviceLabel_);
      if (p == null) {
         studio_.logs().logDebugMessage("iSIM: waveform parameters unavailable; plots not updated");
         return;
      }

      replaceSeries(cameraSeries_, WaveformReconstructor.cameraWaveform(p));

      boolean anyEnabled = false;
      for (int i = 0; i < DeviceAdapterProperties.N_MOD_IN; i++) {
         boolean show = p.modInConfigured[i] && p.modInEnabled[i];
         modInChartPanels_[i].setVisible(show);
         if (show) {
            anyEnabled = true;
            replaceSeries(modInSeries_[i], WaveformReconstructor.modInWaveform(p, i));
         }
      }
      galvoChartPanel_.setVisible(anyEnabled);
      blankingChartPanel_.setVisible(anyEnabled);
      if (anyEnabled) {
         replaceSeries(galvoSeries_, WaveformReconstructor.galvoWaveform(p));
         replaceSeries(blankingSeries_, WaveformReconstructor.blankingWaveform(p));
      }
      chartsPanel_.revalidate();
   }

   /**
    * Replaces the data in {@code target} with the data from {@code source}.
    * Suppresses intermediate change events; {@code setNotify(true)} fires one
    * consolidated event when done (JFreeChart 1.5.0 fires on re-enable).
    */
   private static void replaceSeries(XYSeries target, XYSeries source) {
      target.setNotify(false);
      target.clear();
      for (int i = 0; i < source.getItemCount(); i++) {
         target.add(source.getX(i), source.getY(i), false);
      }
      target.setNotify(true);
   }

   private ChartPanel makeChartPanel(String title, XYSeries series) {
      XYSeriesCollection dataset = new XYSeriesCollection(series);
      JFreeChart chart = ChartFactory.createXYLineChart(
            title, "Time (ms)", "Voltage (V)",
            dataset, PlotOrientation.VERTICAL,
            false, false, false);

      // Remove shapes from the renderer so only lines are drawn
      XYPlot plot = chart.getXYPlot();
      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setDefaultShapesVisible(false);

      ChartPanel cp = new ChartPanel(chart);
      cp.setPreferredSize(new Dimension(400, CHART_HEIGHT_PX));
      cp.setMaximumDrawHeight(Integer.MAX_VALUE);
      cp.setMaximumDrawWidth(Integer.MAX_VALUE);
      cp.setMinimumDrawHeight(1);
      cp.setMinimumDrawWidth(1);
      return cp;
   }
}
