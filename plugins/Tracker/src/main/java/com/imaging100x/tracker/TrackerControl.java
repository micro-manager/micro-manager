///////////////////////////////////////////////////////////////////////////////
//FILE:           TrackerControl.java
//PROJECT:        Micro-Manager-100X
//SUBSYSTEM:      100X Imaging Inc micro-manager extentsions
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June, 2008
//                Nico Stuurman, updated to current API, Jan. 2014
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
//                University of California, San Francisco, 2014
//                
//LICENSE:        This file is distributed under the GPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package com.imaging100x.tracker;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Insets;
import java.io.File;
import java.util.GregorianCalendar;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;

import mmcorej.MMCoreJ;
import mmcorej.TaggedImage;
import org.jfree.data.xy.XYSeries;
import org.json.JSONException;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.TextUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

public class TrackerControl extends MMFrame {
   public static final String menuName = "Live Tracking";
   public static final String tooltipDescription =
      "Use image correlation based tracking to countersteer the XY stage";

   private Datastore store_;
   private DisplayWindow display_;
   private JTextField nameField_;
   private JTextField rootField_;
   private final ButtonGroup buttonGroup = new ButtonGroup();
   private static final long serialVersionUID = 1L;
   private JTextField resField_;
   private JTextField offsetField_;
   private JTextField pixelSizeField_;
   private JTextField intervalField_;
   private Studio app_;
   private int intervalMs_ = 1000;
   private double pixelSizeUm_ = 1.0;
   private int resolutionPix_ = 5;
   private int offsetPix_ = 100;
   private Timer timer_;
   private float[] pixelsPrev_ = null;
   private float[] pixelsCur_ = null;
   private int imWidth_ = 0;
   private String stage_ = "XYStage";
   private Roi roi_;
   private ImageStack corrStack_;
   private ImagePlus corrImplus_;
   private boolean mirrorX_ = false;
   private boolean mirrorY_ = false;
   private boolean rotate_ = false;
   private double dxUmPrev_ = 0.0;
   private double dyUmPrev_ = 0.0;
   private MMRect limits_;
   //private AcquisitionData acq_;
   private int imageCounter_;
   private String acqName_;
   private XYSeries xySeries_;

   private static final String RESOLUTION_PIX = "resolution_pix";
   private static final String OFFSET_PIX = "offset_pix";
   private static final String INTERVAL_MS = "interval_pix";
   private static final String DISK_RECORDING = "disk_recording";
   private static final String ROOT = "root";
   private static final String NAME = "name";
   private static final String TRACK_Y = "TRACK_X_UM";
   private static final String TRACK_X = "TRACK_Y_UM";
   private static final String TRACK_DY = "TRACK_DX_PIX";
   private static final String TRACK_DX = "TRACK_DY_PIX";
   private static final String D = "STEP_UM";
   private static final String V = "VELOCITY_UMPS";
   private static final String L = "TOTAL_TRAVEL_UM";
   private static final String RECT_X = "RECT_X";
   private static final String RECT_Y = "RECT_Y";
   private static final String RECT_W = "RECT_W";
   private static final String RECT_H = "RECT_H";
   private static final String ACQNAME = "LiveTracking";
   private JLabel labelTopLeft_;
   private JLabel labelBottomRight_;
   private final JRadioButton memoryRadioButton_;
   private JRadioButton diskRadioButton_;
   private final JLabel speedLabel_;
   private double distUm_;
   private final JButton topLeftButton_;
   private final JButton bottomRightButton_;
   
   private double firstX_;
   private double firstY_;


   private class MMRect {
      public double xmin;
      public double xmax;
      public double ymin;
      public double ymax;

      public MMRect() {
         xmin = 0;
         xmax = 0;
         ymin = 0;
         ymax = 0;         
      }
      public boolean isValid() {
         return (xmax-xmin)>0 && (ymax-ymin)>0;
      }

      public boolean isWithin(double x, double y) {
         return x>xmin && x<xmax && y>ymin && y<ymax;
      }

      public void clear() {
         xmin = 0;
         xmax = 0;
         ymin = 0;
         ymax = 0;                  
      }
      
      public void normalize() {
         if (xmin > xmax) {
            double tmp = xmin;
            xmin = xmax;
            xmax = tmp;
         }
         if (ymin > ymax) {
            double tmp = ymin;
            ymin = ymax;
            ymax = tmp;
         }
            
      }
   };

   /**
    * Create the dialog
    */
   public TrackerControl(Studio app) {
      super();
      imageCounter_ = 0;
      limits_ = new MMRect();
      initialize();
      app_ = app;
      final UserProfile up  = MMStudio.getInstance().profile();

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowOpened(WindowEvent e) {
            resolutionPix_ = up.getInt(this.getClass(), RESOLUTION_PIX, resolutionPix_);
            offsetPix_ = up.getInt(this.getClass(), OFFSET_PIX, offsetPix_);
            intervalMs_ = up.getInt(this.getClass(), INTERVAL_MS, intervalMs_);
            diskRadioButton_.setSelected(up.getBoolean(this.getClass(), 
                    DISK_RECORDING, diskRadioButton_.isSelected()));
            rootField_.setText(up.getString(this.getClass(), ROOT, ""));
            nameField_.setText(up.getString(this.getClass(), NAME, ""));

            resField_.setText(Integer.toString(resolutionPix_));
            offsetField_.setText(Integer.toString(offsetPix_));
            pixelSizeField_.setText(Double.toString(pixelSizeUm_));
            intervalField_.setText(Integer.toString(intervalMs_));
         }

         @Override
         public void windowClosing(final WindowEvent e) {
            up.setInt(this.getClass(), RESOLUTION_PIX, resolutionPix_);
            up.setInt(this.getClass(), OFFSET_PIX, offsetPix_);
            up.setInt(this.getClass(), INTERVAL_MS, intervalMs_);
            up.setBoolean(this.getClass(), DISK_RECORDING, 
                    diskRadioButton_.isSelected());
            up.setString(this.getClass(), ROOT, rootField_.getText());
            up.setString(this.getClass(), NAME, nameField_.getText());
         }
      });

      setTitle("Live Tracking");
      setResizable(false);
      getContentPane().setLayout(null);
      loadAndRestorePosition(100, 100, 412, 346);

      final JLabel intervalmsLabel = new JLabel();
      intervalmsLabel.setText("Interval [ms]");
      intervalmsLabel.setBounds(10, 10, 100, 14);
      getContentPane().add(intervalmsLabel);

      intervalField_ = new JTextField();
      intervalField_.setBounds(10, 30, 84, 19);
      getContentPane().add(intervalField_);

      final JButton trackButton = new JButton();
      trackButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            intervalMs_ = Integer.parseInt(intervalField_.getText());
            pixelSizeUm_ = Double.parseDouble(pixelSizeField_.getText());
            offsetPix_ = Integer.parseInt(offsetField_.getText());
            resolutionPix_ = Integer.parseInt(resField_.getText());
            pixelsPrev_ = null;
            pixelsCur_ = null;
            timer_.setDelay(intervalMs_);
            track(); 
         }
      });
      trackButton.setText("Track!");
      trackButton.setBounds(303, 10, 93, 23);
      getContentPane().add(trackButton);

      final JLabel pixelSizeumLabel = new JLabel();
      pixelSizeumLabel.setText("Pixel size [um]");
      pixelSizeumLabel.setBounds(10, 55, 110, 14);
      getContentPane().add(pixelSizeumLabel);

      pixelSizeField_ = new JTextField();
      pixelSizeField_.setBounds(10, 75, 84, 19);
      getContentPane().add(pixelSizeField_);

      final JLabel offsetLabel = new JLabel();
      offsetLabel.setText("Range [pixels]");
      offsetLabel.setBounds(140, 10, 122, 14);
      getContentPane().add(offsetLabel);

      offsetField_ = new JTextField();
      offsetField_.setBounds(140, 30, 93, 19);
      getContentPane().add(offsetField_);

      final JLabel resolutionpixelsLabel = new JLabel();
      resolutionpixelsLabel.setText("Resolution [pixels]");
      resolutionpixelsLabel.setBounds(140, 55, 129, 14);
      getContentPane().add(resolutionpixelsLabel);

      resField_ = new JTextField();
      resField_.setBounds(140, 75, 93, 19);
      getContentPane().add(resField_);

      final JButton stopButton = new JButton();
      stopButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            stopTracking();
         }
      });
      stopButton.setText("Stop");
      stopButton.setBounds(303, 39, 93, 26);
      getContentPane().add(stopButton);

      topLeftButton_ = new JButton();
      topLeftButton_.setText("Top Left");
      topLeftButton_.setBounds(10, 140, 125, 26);
      getContentPane().add(topLeftButton_);

      bottomRightButton_ = new JButton();
      bottomRightButton_.setText("Bottom Right");
      bottomRightButton_.setBounds(10, 170, 125, 26);
      bottomRightButton_.setMargin(new Insets(0,0,0,0));
      getContentPane().add(bottomRightButton_);

      labelTopLeft_ = new JLabel();
      labelTopLeft_.setText("not set");
      labelTopLeft_.setBounds(140, 145, 93, 16);
      getContentPane().add(labelTopLeft_);

      labelBottomRight_ = new JLabel();
      labelBottomRight_.setText("not set");
      labelBottomRight_.setBounds(140, 175, 93, 16);
      getContentPane().add(labelBottomRight_);

      final JLabel trackingRegionLabel = new JLabel();
      trackingRegionLabel.setText("XY Stage Limits:");
      trackingRegionLabel.setBounds(10, 120, 217, 16);
      getContentPane().add(trackingRegionLabel);

      memoryRadioButton_ = new JRadioButton();
      buttonGroup.add(memoryRadioButton_);
      memoryRadioButton_.setText("In Memory");
      memoryRadioButton_.setBounds(240, 185, 160, 24);
      getContentPane().add(memoryRadioButton_);
      memoryRadioButton_.setSelected(true);

      diskRadioButton_ = new JRadioButton();
      buttonGroup.add(diskRadioButton_);
      diskRadioButton_.setText("On Disk");
      diskRadioButton_.setBounds(240, 203, 160, 24);
      getContentPane().add(diskRadioButton_);

      final JLabel sequenceDataLabel = new JLabel();
      sequenceDataLabel.setText("Sequence data:");
      sequenceDataLabel.setBounds(240, 170, 160, 16);
      getContentPane().add(sequenceDataLabel);

      rootField_ = new JTextField();
      rootField_.setBounds(64, 263, 286, 20);
      getContentPane().add(rootField_);

      nameField_ = new JTextField();
      nameField_.setBounds(64, 289, 286, 20);
      getContentPane().add(nameField_);

      final JLabel rootLabel = new JLabel();
      rootLabel.setText("Root:");
      rootLabel.setBounds(10, 265, 48, 16);
      getContentPane().add(rootLabel);

      final JLabel nzameLabel = new JLabel();
      nzameLabel.setText("Name:");
      nzameLabel.setBounds(10, 291, 48, 16);
      getContentPane().add(nzameLabel);

      final JButton button = new JButton();
      button.setText("...");
      button.setBounds(358, 260, 38, 26);
      getContentPane().add(button);
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            browse();
         }
      });

      final JButton clearButton = new JButton();
      clearButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            limits_.clear();
            labelBottomRight_.setText("not set");
            labelTopLeft_.setText("not set");
         }
      });
      clearButton.setText("Clear");
      clearButton.setBounds(10, 202, 125, 26);
      getContentPane().add(clearButton);

      speedLabel_ = new JLabel();
      speedLabel_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      speedLabel_.setBounds(10, 100, 386, 19);
      getContentPane().add(speedLabel_);

      final JLabel fileLocationsLabel = new JLabel();
      fileLocationsLabel.setText("Data location");
      fileLocationsLabel.setBounds(10, 241, 143, 16);
      getContentPane().add(fileLocationsLabel);

      // Setup timer
      final AtomicBoolean taskRunning = new AtomicBoolean(false);
      ActionListener timerHandler = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            Runnable doTrack = new Runnable() {
               @Override
               public void run() {
                  TaggedImage tagged = snapSingleImage();
                  if (tagged != null) {
                     processOneFrame(tagged, true);
                  }
                  taskRunning.set(false);
               }
            };
            if (!taskRunning.get()) {
               taskRunning.set(true);
               (new Thread(doTrack)).start();
            }
         }
       };
      timer_ = new Timer(intervalMs_, timerHandler);
      timer_.stop();

   }

   /**
    * Choose the root directory to save files.
    */
   protected void browse() {
      FileDialogs.FileType ft = new FileDialogs.FileType("LiveTracking root", "LiveTracking root",
           System.getProperty("user.home") + "/LiveTracking",
           true, "");
      File f = FileDialogs.openDir(null, "Live Tracking file location", ft);
      if (f != null) {
         rootField_.setText(f.getAbsolutePath());
      }
   }

   public void track() {
      imageCounter_ = 0;
      distUm_ = 0.0;
     
      // Detect desired ROI in Snap/Live Window
      ImagePlus implus = null;
      DisplayWindow win = app_.live().getDisplay();
      if (win != null) {
         implus = win.getImagePlus();
      }
      if (implus == null) {
         app_.logs().showMessage("First snap an image and select ROI to be tracked.", this);
         return;
      }

      roi_ = implus.getRoi();
      if (roi_ == null || roi_.getType() != Roi.RECTANGLE) {
         app_.logs().showError("Rectangular roi required.", this);
         return;
      }

      // Set up new ImageJ window to display the correlation image
      int kCount = 2 * offsetPix_ / resolutionPix_;
      int lCount = 2 * offsetPix_ / resolutionPix_;
      corrStack_ = new ij.ImageStack(lCount, kCount);
      ImageProcessor corrImproc = new ij.process.FloatProcessor(lCount, kCount);
      corrStack_.addSlice(corrImproc);
      corrImplus_ = new ij.ImagePlus("Cross Correlation", corrStack_);
      corrImplus_.show();
      
      app_.logs().logMessage("Tracking started at " + GregorianCalendar.getInstance().getTime());

      acqName_ = nameField_.getText();
      if (acqName_.length() == 0) {
         acqName_ = ACQNAME;
      }
      nameField_.setText(acqName_);
      if (diskRadioButton_.isSelected()) {
         try {
            store_ = app_.data().createMultipageTIFFDatastore(
                  rootField_.getText(), true, false);
         }
         catch (java.io.IOException e) {
            app_.logs().showError(e, "Error opening file " + rootField_.getText() + " for saving");
         }
      }
      else {
         store_ = app_.data().createRAMDatastore();
      }
      display_ = app_.displays().createDisplay(store_);
      xySeries_ = new XYSeries("Track",false);
      TrackerUtils.plotData("Cell Track: " + acqName_, xySeries_, "X (micron)", 
               "Y (micron)", 100, 100);
      timer_.start();
   }
   
   
   public void stopTracking() {

      app_.logs().logMessage("Tracking stopped at " + GregorianCalendar.getInstance().getTime());
      timer_.stop();
      roi_ = null;
   }

   private TaggedImage snapSingleImage() {
      try {
         app_.core().snapImage();
         TaggedImage tagged = app_.core().getTaggedImage();
        
         if (acqName_ != null) {
            MDUtils.setFrameIndex(tagged.tags, imageCounter_);
            Image image = app_.data().convertTaggedImage(tagged);
            image = image.copyAtCoords(app_.data().getCoordsBuilder()
                  .time(imageCounter_).build());
            store_.putImage(image);
            int size = image.getWidth() * image.getHeight();
            if (tagged.pix instanceof byte[]) {
               pixelsCur_ = new float[size];
               byte[] pixels = (byte[])tagged.pix;
               for (int i = 0; i < size; i++)
                  pixelsCur_[i] = pixels[i];
            }
            if (tagged.pix instanceof short[]) {
               pixelsCur_ = new float[size];
               short[] pixels = (short[])tagged.pix;
               for (int i = 0; i < size; i++)
                  pixelsCur_[i] = pixels[i];
            }
            if (tagged.pix instanceof float[]) {
               pixelsCur_ = java.util.Arrays.copyOf((float[])tagged.pix, size);
            }
            imWidth_ = image.getWidth();
         }
         return tagged;
      } catch (Exception e) {
         IJ.error(e.getMessage());
         timer_.stop();
         return null;
      }
   }
   

   private void processOneFrame(TaggedImage tagged, boolean moveStage) {
      if (pixelsPrev_ == null) {
         pixelsPrev_ = pixelsCur_;
         dxUmPrev_ = 0.0;
         dyUmPrev_ = 0.0;
         return;
      }

      int kCount = 2 * offsetPix_ / resolutionPix_;
      int lCount = 2 * offsetPix_ / resolutionPix_;
      ImageProcessor corrImproc = new ij.process.FloatProcessor(lCount, kCount);
      corrStack_.addSlice(corrImproc);

      // position of correlation maximum
      int kMax = 0;
      int lMax = 0;

      Rectangle r = roi_.getBounds();
      display_.getImagePlus().setRoi(roi_, true);
      //IJ.write("ROI pos: " + r.x + "," + r.y);

      int width = r.width, height = r.height;
      double corScale = width * height;

      double maxCor = 0;
      for (int k=-offsetPix_; k<offsetPix_; k += resolutionPix_) {
         for (int l=-offsetPix_; l<offsetPix_; l += resolutionPix_) {

            // calculate correlation
            double sum = 0.0;
            double meanPrev = 0.0;
            double meanCur = 0.0;
            for (int i = 0; i < height; i++) {
               for (int j = 0; j < width; j++) {
                  int row = r.y + i;
                  int col = r.x + j;
                  double pixPrev = pixelsPrev_[row * imWidth_ + col];
                  double pixCur = pixelsCur_[(row + k) * imWidth_ + (col + l)];
                  sum += pixPrev * pixCur;
                  meanPrev += pixPrev;
                  meanCur += pixCur;
               }
            }
            sum /= corScale;
            meanPrev /= corScale;
            meanCur /= corScale;
            sum /= meanPrev*meanCur;

            int x = (l + offsetPix_) / resolutionPix_;
            int y = (k + offsetPix_) / resolutionPix_;
            corrImproc.setf(x + lCount * y, (float) sum);

            // check for max value
            if (sum > maxCor) {
               maxCor = sum;
               kMax = k;
               lMax = l;
            }
         }
      }

      if (corrImplus_ == null) {
         corrImplus_ = new ij.ImagePlus("Cross Correlation", corrStack_);
         corrImplus_.show();
      }
      else {
         corrImplus_.setPosition(imageCounter_ + 1);
         corrImplus_.updateAndRepaintWindow();
      }

      //IJ.write("maxc=" + maxCor + ", offset=(" + lMax + "," + kMax + ")");
      pixelsPrev_ = pixelsCur_;

      // offset in um
      double shiftXUm = -lMax * pixelSizeUm_;
      double shiftYUm = -kMax * pixelSizeUm_;

      // apply image transposition
      if (mirrorX_)
         shiftXUm = -shiftXUm;
      if (mirrorY_)
         shiftYUm = -shiftYUm;
      if (rotate_) {
         double tmp = shiftXUm;
         shiftXUm = shiftYUm;
         shiftYUm = tmp;
      }

      double dxUm = shiftXUm + dxUmPrev_;
      double dyUm = shiftYUm + dyUmPrev_;
      dxUmPrev_ = dxUm;
      dyUmPrev_ = dyUm;

      if (moveStage) {

         try {
            // obtain current XY stage position
            // NOTE: due to Java parameter passing convention, x and y parameters must be arrays
            double[] xCur = new double[1];
            double[] yCur = new double[1];            
            app_.core().getXYPosition(stage_, xCur, yCur);
            tagged.tags.put(TRACK_X, xCur[0]);
            tagged.tags.put(TRACK_Y, yCur[0]);
            tagged.tags.put(TRACK_DX, lMax);
            tagged.tags.put(TRACK_DY, kMax);
            tagged.tags.put(RECT_X, r.x);
            tagged.tags.put(RECT_Y, r.y);
            tagged.tags.put(RECT_W, r.width);
            tagged.tags.put(RECT_H, r.height);                   

            // update the XY position based on the offset
            double newX = xCur[0] + dxUm;
            double newY = yCur[0] + dyUm;
            
            // Plot relative coordinates, swap Y axis to match image direction
            if (xySeries_.isEmpty()) {
                firstX_ = newX;
                firstY_ = newY;
            }
            xySeries_.add(firstX_ - newX, firstY_ - newY);

            if ((limits_.isValid() && limits_.isWithin(newX, newY)) || (!limits_.isValid())) {
               app_.core().setXYPosition(stage_, newX, newY);
               app_.core().waitForDevice(stage_);
               app_.core().getXYPosition(stage_, xCur, yCur);
               app_.logs().logMessage(xCur[0] + "," + yCur[0]);
            } else {
               app_.logs().logMessage("Skipped. Stage limits reached.");
            }
         } catch (Exception e) {
            IJ.error(e.getMessage());
            timer_.stop();
         } // relative motion
      } else {
         // move the roi
         roi_.setLocation(r.x + lMax, r.y + kMax);

         display_.getImagePlus().setRoi(roi_, true);
      }

      double d = Math.sqrt(dxUm * dxUm + dyUm * dyUm);
      distUm_ += d;
      double v = d / intervalMs_ * 1000.0;
      speedLabel_.setText("n=" + imageCounter_ + ", t=" + TextUtils.FMT2.format(((double) imageCounter_ * intervalMs_) / 1000.0)
              +     " s, d=" + TextUtils.FMT2.format(d) + " um, l=" + TextUtils.FMT2.format(distUm_) + " um, v=" + TextUtils.FMT2.format(v) + " um/s");
      try {
         tagged.tags.put(D, d);
         tagged.tags.put(V, v);
         tagged.tags.put(L, distUm_);
      } catch (JSONException ex) {
         app_.logs().showError(ex, "Problem adding tags to image", this);
      }

      imageCounter_++;
   }
 
   private void initialize() {
      if (app_ == null)
         return;
      
      stage_ = app_.core().getXYStageDevice();
      pixelSizeUm_ = app_.core().getPixelSizeUm();
      String camera = app_.core().getCameraDevice();
      try {
         mirrorX_ = app_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX()).equals("1");
         mirrorY_ = app_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY()).equals("1");
         rotate_ = app_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY()).equals("1");

      } catch (Exception e1) {
         // TODO Auto-generated catch block
         app_.logs().showError(e1, "Problem initializing Live Tracking plugin", this);
      }
      
      topLeftButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            double[] x = new double[1];
            double[] y = new double[1];
            try {
               app_.core().getXYPosition(stage_, x, y);
               limits_.xmin = x[0];
               limits_.ymin = y[0];
               labelTopLeft_.setText(Double.toString(x[0]) + "," + Double.toString(y[0]));
               limits_.normalize();
            } catch (Exception e1) {
               IJ.error(e1.getMessage());
            }
         }
      });
      
      bottomRightButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            double[] x = new double[1];
            double[] y = new double[1];
            try {
               app_.core().getXYPosition(stage_, x, y);
               limits_.xmax = x[0];
               limits_.ymax = y[0];
               labelBottomRight_.setText(Double.toString(x[0]) + "," + Double.toString(y[0]));
               limits_.normalize();
            } catch (Exception e1) {
               IJ.error(e1.getMessage());
            }
         }
      });
  }
   
   public void configurationChanged() {
      initialize();
   }
}
