/**
 * RTIntensitiesFrame.java
 *
 * Based on ExampleFrame, shows RealTime intensity plots during live views or acquisitions.
 *
 * Nico Stuurman, Carlos Mendioroz copyright UCSF, 2020
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
import java.awt.List;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.Studio;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.ImageOverwrittenEvent;
import org.micromanager.display.DisplayWindow;

import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import org.micromanager.internal.utils.WindowPositioning;

public class RTIntensitiesFrame extends JFrame {

   private final Studio studio_;
   private final SimpleDateFormat dateFormat_;
   private DisplayWindow window_ = null;
   private DataProvider dataProvider_;
   private JButton startButton_;
   private double lastElapsedTimeMs_ = 0.0;
   private Date firstImageDate_;
   // This is our local cache ...
   private int ROIs_ = 0;
   private Roi[] roi_ = new Roi[100];
   // of the manager kept ROIs
   private RoiManager manager_;
   private int imagesReceived_ = 0;
   static final long serialVersionUID = 1;
   private JLabel title;
   private XYSeriesCollection      dataset;
   XYSeries[] data = new XYSeries[100];

   private static final String ABSOLUTE_FORMAT_STRING = "yyyy-MM-dd HH:mm:ss.SSS Z";

   public RTIntensitiesFrame(Studio studio) {
      super("Real time intensity GUI");
      studio_ = studio;
      dateFormat_ = new SimpleDateFormat(ABSOLUTE_FORMAT_STRING);
      super.setLocation(100, 100); // Default location
      WindowPositioning.setUpLocationMemory(this, RTIntensitiesFrame.class, "Main");

      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));

      // Tell user what to do, in case he does not know...
      title = new JLabel("                Prepare for instructions!            ");
      title.setFont(new Font("Arial", Font.BOLD, 14));
      super.add(title, "span, alignx center, wrap");

      // Shortcut to ROI Manager
      JButton managerButton_ = new JButton("ROI Manager");
      managerButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
   			if (manager_ == null)
   			    manager_ = new RoiManager();
   			manager_.setVisible(true);
         }
      });
      super.add(managerButton_, "split, span");

      // Create a graph and start plotting, or tell user what's missing for so doing
      startButton_ = new JButton("Plot");
      startButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
         	// Active window ?
        	   window_ = studio.displays().getCurrentWindow();
        	   if (window_ == null) {
        	   	title.setText("You need an open window (Snap/Live).");
        	   	return;
        	   }
        	   dataProvider_ = window_.getDataProvider();
        	   // At least one ROI defined ?
        	   if (manager_ == null) {
        	   	title.setText("Please setup ROI(s).");
        	   	return;
        	   }
        	   List labels = manager_.getList();
        	   Hashtable<String, Roi> table = (Hashtable<String, Roi>)manager_.getROIs();
        	   ROIs_ = labels.getItemCount();
        	   if (ROIs_ == 0) {
        	   	title.setText("Please setup ROI(s).");
        	   	return;
        	   }
        	   // Hard limit of 100 ROIs
        	   if (ROIs_ > 100) ROIs_ = 100; 
        	   for (int i = 0; i < ROIs_; i++) {
        	   	String label = labels.getItem(i);
        	   	roi_[i] = table.get(label);
        	   }
        	   // We are all set ?
        	   imagesReceived_ = 0; // new plot
            lastElapsedTimeMs_ = 0;
        	   dataProvider_.registerForEvents(RTIntensitiesFrame.this);
        	   dataset = new XYSeriesCollection();
        	   for (int i = 0; i < ROIs_; i++) {
        		   data[i] = new XYSeries("" + (i + 1));
        		   dataset.addSeries(data[i]);
        	   }
        	   plotData("Intensities " + dataProvider_.getName(), dataset, "Time(ms)", "Value", 100, 100);
         }
      });
      startButton_.setEnabled(true);
      super.add(startButton_, "wrap");

      super.pack();

      // Registering this class for events means that its event handlers
      // (that is, methods with the @Subscribe annotation) will be invoked when
      // an event occurs. You need to call the right registerForEvents() method
      // to get events; this one is for the application-wide event bus, but
      // there's also Datastore.registerForEvents() for events specific to one
      // Datastore, and DisplayWindow.registerForEvents() for events specific
      // to one image display window.
      studio_.events().registerForEvents(this);
      title.setText("You need an active window and a ROI ...");
   }

   @Subscribe
   public void onNewImage(DataProviderHasNewImageEvent event) {
      processImage(event.getDataProvider(), event.getImage());
   }

   @Subscribe
   public void onOverWrittenImage(ImageOverwrittenEvent event) {
      processImage(event.getDatastore(), event.getNewImage());
   }

   private void processImage(DataProvider dp, Image image) {
      if (!dp.equals(dataProvider_)) {
         return;
      }
      Date imgTime;
      try {
          imgTime = dateFormat_.parse(image.getMetadata().getReceivedTime());
      } catch (ParseException pe) {
         studio_.logs().logError(pe);
         return;
      }
      if (imagesReceived_ == 0) {
         firstImageDate_ = imgTime;
      }
	   imagesReceived_++;
	   double elapsedTimeMs = imgTime.getTime() - firstImageDate_.getTime();
	   // do not process images at ore than 1 Hz
	   if (lastElapsedTimeMs_ == 0.0 || elapsedTimeMs - lastElapsedTimeMs_ >= 10.0) { // 100Hz max
         lastElapsedTimeMs_ = elapsedTimeMs;
		   double v;
		   title.setText("You should be seeing data on the plot.");


		   if (window_ == null) {
		      window_ = studio_.getDisplayManager().getDisplays(dataProvider_).get(0);
         }
		   ImageProcessor processor = studio_.data().ij().createProcessor(image);
		   processor.setLineWidth(1);
		   processor.setColor(Color.red);
		   
		   for (int i = 0; i < ROIs_-1; i++) {
			   processor.setRoi(roi_[i]);
			   v = processor.getStats().mean;
			   data[i].add(elapsedTimeMs, v, false);
		   }
		   processor.setRoi(roi_[ROIs_ - 1]);
		   v = processor.getStats().mean;
		   data[ROIs_ - 1].add(elapsedTimeMs, v, true);
	   }
   }
   
   /**
    * Create a frame with a plot of the data given in XYSeries
    * @param title Title of the plot
    * @param dataset Data to be plotted
    * @param xTitle Title of the x axis
    * @param yTitle Title of the y axis
    * @param xLocation Window location on the screen (x)
    * @param yLocation Window Location on the screen (y)
    */
   public static void plotData(String title, XYSeriesCollection dataset, String xTitle,
           String yTitle, int xLocation, int yLocation) {
      // JFreeChart code
      JFreeChart chart = ChartFactory.createScatterPlot(title, // Title
                xTitle, // x-axis Label
                yTitle, // y-axis Label
                dataset, // Dataset
                PlotOrientation.VERTICAL, // Plot Orientation
                true, // Show Legend
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
      graphFrame.setSize(400, 300);
      WindowPositioning.setUpBoundsMemory(graphFrame, RTIntensities.class, "plot");
      WindowPositioning.cascade(graphFrame, RTIntensities.class);
      graphFrame.setVisible(true);
   }

}
