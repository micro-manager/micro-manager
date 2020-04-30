/**
 * ExampleFrame.java
 *
 * This module shows an example of creating a GUI (Graphical User Interface).
 * There are many ways to do this in Java; this particular example uses the
 * MigLayout layout manager, which has extensive documentation online.
 *
 *
 * Nico Stuurman, copyright UCSF, 2012, 2015
 *
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */
package org.micromanager.plugins.rtintensities;

import com.google.common.eventbus.Subscribe;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.Studio;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import org.micromanager.internal.utils.WindowPositioning;

public class RTIntensitiesFrame extends JFrame {

   private Studio studio_;
   private DisplayWindow window_ = null;
   private ImagePlus  image_;
   private final JLabel imageInfoLabel_;
   private final JLabel roiInfoLabel_;
   private JButton clearRoiButton_;
   private JButton setRoiButton_;
   private final JLabel acqInfoLabel_;
   private JButton acquireButton_;
   private Datastore ds_;
   private int skip_ = 0;
   private Roi[] roi_ = new Roi[100];
   private int ROIs_ = 0;
   private int imagesReceived_ = 0;
   static final long serialVersionUID = 1;
   private JLabel title;
   private XYSeriesCollection      dataset;
   XYSeries[] data = new XYSeries[100];

   public RTIntensitiesFrame(Studio studio) {
      super("Real time intensity GUI");
      studio_ = studio;
      super.setLocation(100, 100); // Default location
      WindowPositioning.setUpLocationMemory(this, RTIntensitiesFrame.class, "Main");

      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));

      title = new JLabel("                Prepare for instructions!            ");
      title.setFont(new Font("Arial", Font.BOLD, 14));
      super.add(title, "span, alignx center, wrap");

      // Snap an image, remember, show the image in the Snap/Live view, and show some
      // stats on the image in our frame.
      imageInfoLabel_ = new JLabel();
      super.add(imageInfoLabel_, "growx, split, span");
      JButton snapButton = new JButton("Snap Image");
      snapButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
        	 // Multiple images are returned only if there are multiple
        	 // cameras. We only care about the first image.
             List<Image> images = studio_.live().snap(true);
             Image firstImage = images.get(0);
             showImageInfo(firstImage);
             title.setText("Now define ROIs ...");
             clearRoiButton_.setEnabled(true);
             setRoiButton_.setEnabled(true);
         }
      });
      super.add(snapButton, "wrap");

      // Assign ROI to image
      roiInfoLabel_ = new JLabel();
      super.add(roiInfoLabel_, "growx, split, span");
      clearRoiButton_ = new JButton("Clear ROIs");
      clearRoiButton_.addActionListener(new ActionListener() {
    	  @Override
    	  public void actionPerformed(ActionEvent e) {
   			  title.setText("Define ROIs ...");
   			  acquireButton_.setEnabled(false);
   			  ROIs_ = 0;
   			roiInfoLabel_.setText("");
         }
      });
      clearRoiButton_.setEnabled(false);
      super.add(clearRoiButton_, "split, span");
            
      setRoiButton_ = new JButton("Set ROI");
      setRoiButton_.addActionListener(e -> {
         image_ = studio_.displays().getAllImageWindows().get(0).getImagePlus();
         SummaryMetadata md = studio_.displays().getAllDataViewers().get(0).getDataProvider().getSummaryMetadata();
         if (ROIs_ < 100 && image_.getRoi().isVisible()) {
            title.setText("Ready to acquire");
            roi_[ROIs_] = image_.getRoi();
            ROIs_++;
            roiInfoLabel_.setText(String.format("Defined: %d", ROIs_));
            acquireButton_.setEnabled(true);
         }
      });
      setRoiButton_.setEnabled(false);
      super.add(setRoiButton_, "wrap");
      
      acqInfoLabel_ = new JLabel("");
      super.add(acqInfoLabel_, "split, span, growx");

      // Run an acquisition using the current MDA parameters.
      acquireButton_ = new JButton("Run Acquisition");
      acquireButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // All GUI event handlers are invoked on the EDT (Event Dispatch
            // Thread). Acquisitions are not allowed to be started from the
            // EDT. Therefore we must make a new thread to run this.
            Thread acqThread = new Thread(new Runnable() {
               @Override
               public void run() {
            	   studio_.getDisplayManager().getCurrentWindow().close();
            	   ds_ = studio_.acquisitions().runAcquisitionNonblocking();
            	   if (studio.acquisitions().getAcquisitionSettings().intervalMs < 1000) {
            		   skip_ = (int) (1000 / studio.acquisitions().getAcquisitionSettings().intervalMs);
            	   }
            	   ds_.registerForEvents(RTIntensitiesFrame.this);
            	   dataset = new XYSeriesCollection();
            	   for (int i = 0; i < ROIs_; i++) {
            		   data[i] = new XYSeries("ROI_" + i);
            		   dataset.addSeries(data[i]);
            	   }
            	   plotData("Intensities", dataset, "Time", "Value", 100, 100);
               }
            });
            acqThread.start();
         }
      });
      acquireButton_.setEnabled(false);
      super.add(acquireButton_, "wrap");

      super.pack();

      // Registering this class for events means that its event handlers
      // (that is, methods with the @Subscribe annotation) will be invoked when
      // an event occurs. You need to call the right registerForEvents() method
      // to get events; this one is for the application-wide event bus, but
      // there's also Datastore.registerForEvents() for events specific to one
      // Datastore, and DisplayWindow.registerForEvents() for events specific
      // to one image display window.
      studio_.events().registerForEvents(this);
      title.setText("Snap an image ...");
   }

   @Subscribe
   public void onNewImage(DataProviderHasNewImageEvent event) {
	   imagesReceived_++;
	   if (skip_ == 0 || imagesReceived_ % (skip_+1) == 1) { // 1Hz max
		   double t;
		   double v;
		   title.setText("You should be seeing data on the plot.");
		   Image image = event.getImage();
		   t = (double)image.getCoords().getT();
		   if (window_ == null) window_ = studio_.getDisplayManager().getDisplays(ds_).get(0);
		   ImageProcessor processor = studio_.data().ij().createProcessor(image);
		   processor.setLineWidth(1);
		   processor.setColor(Color.red);
		   
		   for (int i = 0; i < ROIs_-1; i++) {
			   processor.setRoi(roi_[i]);
			   v = processor.getStats().mean;
			   data[i].add(t, v, false);
			   processor.draw(roi_[i]);
		   }
		   processor.setRoi(roi_[ROIs_ - 1]);
		   v = processor.getStats().mean;
		   data[ROIs_ - 1].add(t, v, true);
		   processor.draw(roi_[ROIs_ - 1]);
	   }
   }
   
 
   /**
    * Display some information on the data in the provided image.
    */
   private void showImageInfo(Image image) {
      // See DisplayManager for information on these parameters.
      //HistogramData data = studio_.displays().calculateHistogram(
      //   image, 0, 16, 16, 0, true);
      imageInfoLabel_.setText(String.format(
            "Image size: %dx%d", // min: %d, max: %d, mean: %d, std: %.2f",
            image.getWidth(), image.getHeight() ) ); //, data.getMinVal(),
            //data.getMaxVal(), data.getMean(), data.getStdDev()));
   }
   
   /**
    * Create a frame with a plot of the data given in XYSeries
    * @param title
    * @param xTitle
    * @param yTitle
    * @param xLocation
    * @param yLocation
    */
   public static void plotData(String title, XYSeriesCollection dataset, String xTitle,
           String yTitle, int xLocation, int yLocation) {
      // JFreeChart code
      JFreeChart chart = ChartFactory.createScatterPlot(title, // Title
                xTitle, // x-axis Label
                yTitle, // y-axis Label
                dataset, // Dataset
                PlotOrientation.VERTICAL, // Plot Orientation
                false, // Show Legend
                true, // Use tooltips
                false // Configure chart to generate URLs?
            );
      XYPlot plot = (XYPlot) chart.getPlot();
      plot.setBackgroundPaint(Color.white);
      plot.setRangeGridlinePaint(Color.lightGray);
      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setDefaultShapesVisible(true);
      renderer.setSeriesPaint(0, plot.getDrawingSupplier().getNextPaint());
      renderer.setSeriesFillPaint(0, Color.white);
      renderer.setSeriesLinesVisible(0, true);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);
      renderer.setUseFillPaint(true);
      for(int i=1;i<100;i++){
          renderer.setSeriesShape(i, circle, false);
          renderer.setSeriesLinesVisible(i, true);
          renderer.setSeriesPaint(i, plot.getDrawingSupplier().getNextPaint());
      }

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setVisible(true);
   }

}
