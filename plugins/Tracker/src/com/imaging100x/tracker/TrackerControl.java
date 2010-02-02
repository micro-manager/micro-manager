///////////////////////////////////////////////////////////////////////////////
//FILE:           TrackerControl.java
//PROJECT:        Micro-Manager-100X
//SUBSYSTEM:      100X Imaging Inc micro-manager extentsions
//-----------------------------------------------------------------------------
//
//AUTHOR:         Nenad Amodaj, nenad@amodaj.com, June, 2008
//
//COPYRIGHT:      100X Imaging Inc, www.100ximaging.com, 2008
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
//
//CVS:            $Id: TrackerControlDlg.java 2019 2009-01-26 05:21:09Z nenad $

package com.imaging100x.tracker;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
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
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;

import mmcorej.Configuration;
import mmcorej.MMCoreJ;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.utils.Annotator;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.TextUtils;

public class TrackerControl extends MMFrame implements MMPlugin {
   private JTextField nameField_;
   private JTextField rootField_;
   private ButtonGroup buttonGroup = new ButtonGroup();
   private static final long serialVersionUID = 1L;
   private JTextField resField_;
   private JTextField offsetField_;
   private JTextField pixelSizeField_;
   private JTextField intervalField_;
   private ScriptInterface app_;
   private int intervalMs_ = 1000;
   private double pixelSizeUm_ = 1.0;
   private int resolutionPix_ = 5;
   private int offsetPix_ = 100;
   private Timer timer_;
   private ImageProcessor ipPrevious_ = null;
   private ImageProcessor ipCurrent_ = null;
   private String stage_ = "XYStage";
   private Roi roi_;
   private ImageStack stack_;
   private boolean mirrorX_ = false;
   private boolean mirrorY_ = false;
   private boolean rotate_ = false;
   private Preferences prefs_;
   private MMRect limits_;
   private AcquisitionData acq_;
   private int imageCounter_;

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
   private JLabel labelTopLeft_;
   private JLabel labelBottomRight_;
   private JRadioButton stackRadioButton_;
   private JRadioButton image5dRadioButton_;
   private JLabel speedLabel_;
   private double distUm_;
   private JButton topLeftButton_;
   private JButton bottomRightButton_;
   
   static private final String VERSION_INFO = "1.0";
   static private final String COPYRIGHT_NOTICE = "Copyright by 100X Imaging Inc, 2009";
   static private final String DESCRIPTION = "Live cell tracking module";
   static private final String INFO = "Not available";


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
   public TrackerControl() {
      super();
      imageCounter_ = 0;
      limits_ = new MMRect();
      prefs_ = Preferences.userNodeForPackage(this.getClass());

      addWindowListener(new WindowAdapter() {
         public void windowOpened(WindowEvent e) {
            resolutionPix_ = prefs_.getInt(RESOLUTION_PIX, resolutionPix_);
            offsetPix_ = prefs_.getInt(OFFSET_PIX, offsetPix_);
            intervalMs_ = prefs_.getInt(INTERVAL_MS, intervalMs_);
            image5dRadioButton_.setSelected(prefs_.getBoolean(DISK_RECORDING, image5dRadioButton_.isSelected()));
            rootField_.setText(prefs_.get(ROOT, ""));
            nameField_.setText(prefs_.get(NAME, ""));

            resField_.setText(Integer.toString(resolutionPix_));
            offsetField_.setText(Integer.toString(offsetPix_));
            pixelSizeField_.setText(Double.toString(pixelSizeUm_));
            intervalField_.setText(Integer.toString(intervalMs_));
         }

         public void windowClosing(final WindowEvent e) {
            prefs_.putInt(RESOLUTION_PIX, resolutionPix_);
            prefs_.putInt(OFFSET_PIX, offsetPix_);
            prefs_.putInt(INTERVAL_MS, intervalMs_);
            prefs_.putBoolean(DISK_RECORDING, image5dRadioButton_.isSelected());
            prefs_.put(ROOT, rootField_.getText());
            prefs_.put(NAME, nameField_.getText());
         }
      });

      setTitle("MM Tracker");
      setResizable(false);
      getContentPane().setLayout(null);
      setBounds(100, 100, 412, 346);

      final JLabel intervalmsLabel = new JLabel();
      intervalmsLabel.setText("Interval [ms]");
      intervalmsLabel.setBounds(10, 10, 100, 14);
      getContentPane().add(intervalmsLabel);

      intervalField_ = new JTextField();
      intervalField_.setBounds(10, 30, 84, 19);
      getContentPane().add(intervalField_);

      final JButton trackButton = new JButton();
      trackButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            intervalMs_ = Integer.parseInt(intervalField_.getText());
            pixelSizeUm_ = Double.parseDouble(pixelSizeField_.getText());
            offsetPix_ = Integer.parseInt(offsetField_.getText());
            resolutionPix_ = Integer.parseInt(resField_.getText());
            ipPrevious_ = null;
            ipCurrent_ = null;
            stack_ = null;
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
      offsetLabel.setText("Offset [pixels]");
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

      stackRadioButton_ = new JRadioButton();
      buttonGroup.add(stackRadioButton_);
      stackRadioButton_.setText("Stack (in-memory)");
      stackRadioButton_.setBounds(240, 185, 160, 24);
      getContentPane().add(stackRadioButton_);
      stackRadioButton_.setSelected(true);

      image5dRadioButton_ = new JRadioButton();
      buttonGroup.add(image5dRadioButton_);
      image5dRadioButton_.setText("Image5D (disk)");
      image5dRadioButton_.setBounds(240, 203, 160, 24);
      getContentPane().add(image5dRadioButton_);

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

      final JButton clearButton = new JButton();
      clearButton.addActionListener(new ActionListener() {
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
      //

      // Setup timer
      ActionListener timerHandler = new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            snapSingleImage();
            processOneFrame(true);
         }
      };
      timer_ = new Timer(intervalMs_, timerHandler);
      timer_.stop();

   }

   /**
    * Choose the root directory to save files.
    */
   protected void browse() {
      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setCurrentDirectory(new File(rootField_.getText()));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         rootField_.setText(fc.getSelectedFile().getAbsolutePath());
      }
   }

   public void track() {
      imageCounter_ = 0;
      distUm_ = 0.0;
      //roiStart_ = roi_.getBoundingRect();
      IJ.write("Tracking started at " + GregorianCalendar.getInstance().getTime());
      if (image5dRadioButton_.isSelected()) {
         acq_ = new AcquisitionData();
         try {
            acq_.createNew(nameField_.getText(), rootField_.getText(), true);
            acq_.setDimensions(0, 1, 1);
            acq_.setImagePhysicalDimensions((int)app_.getMMCore().getImageWidth(), (int)app_.getMMCore().getImageHeight(), (int)app_.getMMCore().getBytesPerPixel());
            acq_.setImageIntervalMs(intervalMs_);
            acq_.setPixelSizeUm(pixelSizeUm_);
         } catch (MMAcqDataException e) {
            IJ.write(e.getMessage());
         }
      } else {
         acq_ = null;
      }
      timer_.start();
   }
   public void stopTracking() {

      //roiStart_ = roi_.getBoundingRect();
      IJ.write("Tracking stopped at " + GregorianCalendar.getInstance().getTime());
      timer_.stop();
      if (acq_ == null)
         createStack();
   }

   private boolean snapSingleImage() {

      ImagePlus implus = WindowManager.getCurrentImage();
      if (implus == null) {
         IJ.write("There are no windows open.\n" + "Tracker plugin is now exiting.");
         timer_.stop();

         return false;
      }

      roi_ = implus.getRoi();
      if (roi_ == null || roi_.getType() != Roi.RECTANGLE) {
         IJ.write("Rectangular roi requred.");
         timer_.stop();
         return false;
      }

      try {
         app_.getMMCore().snapImage();
         Object img = app_.getMMCore().getImage();
         implus.getProcessor().setPixels(img);
         implus.updateAndDraw();
         ImageWindow iwnd = implus.getWindow();
         iwnd.getCanvas().paint(iwnd.getCanvas().getGraphics());
         ipCurrent_ = implus.getProcessor();
         if (acq_ == null && stack_ == null)
            stack_ = new ImageStack(implus.getProcessor().getWidth(), implus.getHeight());
         if (acq_ != null) {
            IJ.write("Frame: " + imageCounter_);
            acq_.insertImage(img, imageCounter_, 0, 0);
            Configuration cfg = app_.getMMCore().getSystemStateCache();
            Annotator.setStateMetadata(acq_, imageCounter_, 0, 0, cfg);
         } else
            stack_.addSlice(Integer.toString(stack_.getSize()+1), implus.getProcessor());
      } catch (Exception e) {
         IJ.error(e.getMessage());
         timer_.stop();
         return false;
      }

      //IJ.write("Frame acquired!");
      return true;
   }
   private void createStack() {
      // create a stack window
      ImagePlus impStack = new ImagePlus("Tracker stack", stack_);
      impStack.show();
      impStack.draw();
   }

   private void processOneFrame(boolean moveStage) {
      if (ipPrevious_ == null) {
         ipPrevious_ = ipCurrent_;
         return;
      }

      // iterate on all offsets
      int kMax = 0;
      int lMax = 0;

      Rectangle r = roi_.getBoundingRect();
      //IJ.write("ROI pos: " + r.x + "," + r.y);

      double corScale = r.width * r.height;

      double maxCor = 0; // <<<
      for (int k=-offsetPix_; k<offsetPix_; k += resolutionPix_) {
         for (int l=-offsetPix_; l<offsetPix_; l += resolutionPix_) {

            // calculate correlation
            double sum = 0.0;
            double meanPrev = 0.0;
            double meanCur = 0.0;
            for (int i=0; i<r.height; i++) {
               for (int j=0; j<r.width; j++) {
                  int pixPrev = ipPrevious_.getPixel(r.x + j + l, r.y + i + k);
                  int pixCur = ipCurrent_.getPixel(r.x + j + l, r.y + i + k);
                  sum += (double)pixPrev*pixCur;
                  meanPrev += pixPrev;
                  meanCur += pixCur;
               }
            }
            sum /= corScale;
            meanPrev /= corScale;
            meanCur /= corScale;
            sum /= meanPrev*meanCur;

            // check for max value
            if (sum > maxCor) {
               maxCor = sum;
               kMax = k;
               lMax = l;
            }
         }
      }

      //IJ.write("maxc=" + maxCor + ", offset=(" + lMax + "," + kMax + ")");
      ipPrevious_ = ipCurrent_;
      // offset in um

      double x = lMax * pixelSizeUm_;
      double y = kMax * pixelSizeUm_;

      if (moveStage) {

         try {
            // obtain current XY stage position
            // NOTE: due to Java parameter passing convention, x and y parameters must be arrays
            double[] xCur = new double[1];
            double[] yCur = new double[1];            
            app_.getMMCore().getXYPosition(stage_, xCur, yCur);
            if (acq_ != null) {
               acq_.setImageValue(imageCounter_, 0, 0, TRACK_X, xCur[0]);
               acq_.setImageValue(imageCounter_, 0, 0, TRACK_Y, yCur[0]);
               acq_.setImageValue(imageCounter_, 0, 0, TRACK_DX, lMax);
               acq_.setImageValue(imageCounter_, 0, 0, TRACK_DY, kMax);
               acq_.setImageValue(imageCounter_, 0, 0, RECT_X, r.x);
               acq_.setImageValue(imageCounter_, 0, 0, RECT_Y, r.y);
               acq_.setImageValue(imageCounter_, 0, 0, RECT_W, r.width);
               acq_.setImageValue(imageCounter_, 0, 0, RECT_H, r.height);
            }

            // apply image transposition
            if (mirrorX_)
               x = -x;
            if (mirrorY_)
               y = -y;
            if (rotate_) {
               double tmp = x;
               x = y;
               y = tmp; 
            }

            // reverse x axis for the Nikon stage
            // TODO: automatically discover the orientation
            //x = -x;

            // update the XY position based on the offset
            double newX = xCur[0]-x;
            double newY = yCur[0]-y;

            if ((limits_.isValid() && limits_.isWithin(newX, newY)) || (!limits_.isValid())) {
               app_.getMMCore().setXYPosition(stage_, newX, newY);
               app_.getMMCore().waitForDevice(stage_);
               app_.getMMCore().getXYPosition(stage_, xCur, yCur);
               IJ.write(xCur[0] + "," + yCur[0]);
            } else {
               IJ.write("Skipped. Stage limits reached.");
            }
         } catch (Exception e) {
            IJ.error(e.getMessage());
            timer_.stop();
         } // relative motion
      } else {
         // move the roi
         roi_.setLocation(r.x+lMax, r.y+kMax);
      }
      double d = Math.sqrt(x*x + y*y);
      distUm_ += d;
      double v = d / intervalMs_ * 1000.0;
      speedLabel_.setText("n=" + imageCounter_ + ", t=" + TextUtils.FMT2.format(((double)imageCounter_ * intervalMs_)/1000.0) +
            " s, d=" + TextUtils.FMT2.format(d) + " um, l=" + TextUtils.FMT2.format(distUm_) + " um, v=" + TextUtils.FMT2.format(v) + " um/s");
      if (acq_ != null) {
         try {
            acq_.setImageValue(imageCounter_, 0, 0, D, d);
            acq_.setImageValue(imageCounter_, 0, 0, V, v);
            acq_.setImageValue(imageCounter_, 0, 0, L, distUm_);
         } catch (MMAcqDataException e) {
            IJ.write(e.getMessage());
            timer_.stop();
         }
      }
      imageCounter_++;
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
      // initialize();
   }
   
   private void initialize() {
      if (app_ == null)
         return;
      
      stage_ = app_.getMMCore().getXYStageDevice();
      pixelSizeUm_ = app_.getMMCore().getPixelSizeUm();
      String camera = app_.getMMCore().getCameraDevice();
      try {
         mirrorX_ = app_.getMMCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX()).equals("1");
         mirrorY_ = app_.getMMCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY()).equals("1");
         rotate_ = app_.getMMCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY()).equals("1");

//         if (mirrorX_)
//            IJ.write("X mirrored");
//         if (mirrorY_)
//            IJ.write("Y mirrored");
//         if (rotate_)
//            IJ.write("X and Y swapped");

      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }
      
      topLeftButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            double[] x = new double[1];
            double[] y = new double[1];
            try {
               app_.getMMCore().getXYPosition(stage_, x, y);
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
         public void actionPerformed(final ActionEvent e) {
            double[] x = new double[1];
            double[] y = new double[1];
            try {
               app_.getMMCore().getXYPosition(stage_, x, y);
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

   public String getCopyright() {
      return COPYRIGHT_NOTICE;
   }

   public String getDescription() {
      return DESCRIPTION;
   }

   public String getInfo() {
      return INFO;
   }

   public String getVersion() {
      return VERSION_INFO;
   }

   public static String menuName = "Tracker";
}
