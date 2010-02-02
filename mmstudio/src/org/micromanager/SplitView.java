///////////////////////////////////////////////////////////////////////////////
//FILE:          SplitView.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu JFeb 17, 2008

//COPYRIGHT:    University of California, San Francisco, 2008

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager;

import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.SpringLayout;
import javax.swing.Timer;

import mmcorej.CMMCore;

import org.micromanager.api.DeviceControlGUI;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.ImagePropertyKeys;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.utils.Annotator;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.MMDialog;

import com.swtdesigner.SwingResourceManager;
import org.micromanager.utils.ReportingUtils;

public class SplitView extends MMDialog 
{
   private static final long serialVersionUID = 8624269914627495660L;
   private CMMCore core_;
   private Image5D image5D_;
   private Image5DWindow image5DWindow_;
   private long imgDepth_;
   private int width_, height_, newWidth_, newHeight_;
   private JToggleButton toggleButtonLive_;
   private JRadioButton lrButton_;
   private JRadioButton tbButton_;
   private MMOptions options_;
   private Preferences prefs_;
   private GUIColors guiColors_;
   private Timer timer_;
   private double interval_;
   private String orientation_;
   private boolean autoShutterOrg_;
   private boolean shutterOrg_;
   private String shutterLabel_;
   private Color col1_, col2_;
   private DeviceControlGUI parent_;

   private static final String LR = "lr";
   private static final String TB = "tb";
   private static final String TOPLEFTCOLOR = "TopLeftColor";
   private static final String BOTTOMRIGHTCOLOR = "BottomRightColor";
   private static final String ORIENTATION = "Orientation";

   /**
    * Create Dialog to start SPlitview
    */
   public SplitView(CMMCore core, DeviceControlGUI parent, MMOptions options) {
      super ();

      parent_ = parent;

      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
            prefs_.putInt(TOPLEFTCOLOR, col1_.getRGB());
            prefs_.putInt(BOTTOMRIGHTCOLOR, col2_.getRGB());
            savePosition();
            parent_.makeActive();
         }
      });

      core_ = core;
      options_ = options;
      // The following are use to get the default 8 and 16 bit color models:
      //tmpProcByte_ = new ByteProcessor(1, 1);
      //tmpProcShort_ = new ShortProcessor(1, 1);

      final JButton topLeftColorButton = new JButton();
      final JButton bottomRightColorButton = new JButton();

      guiColors_ = new GUIColors();
      setTitle("Split View");
      setBounds(100, 100, 280, 130);
      setResizable(false);
      Dimension buttonSize = new Dimension(120, 20);

      loadPosition(100, 100);

      setBackground(guiColors_.background.get(options_.displayBackground));

      SpringLayout sp = new SpringLayout();
      getContentPane().setLayout(sp);
      final int gap = 5;

      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/SplitView");
      setPrefsNode(prefs_);

      col1_ = new Color(prefs_.getInt(TOPLEFTCOLOR, Color.red.getRGB()));
      col2_ = new Color(prefs_.getInt(BOTTOMRIGHTCOLOR, Color.green.getRGB()));

      // Add radio buttons for left-right versus top-bottom split
      orientation_ = prefs_.get(ORIENTATION, LR);
      lrButton_ = new JRadioButton("Left-Right Split");
      if (orientation_.equals(LR))
         lrButton_.setSelected(true);
      else
         lrButton_.setSelected(false);
      sp.putConstraint(SpringLayout.NORTH, lrButton_, gap, SpringLayout.NORTH, getContentPane());
      sp.putConstraint(SpringLayout.WEST, lrButton_, gap, SpringLayout.WEST, getContentPane());
      lrButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            orientation_ = LR;
            prefs_.put(ORIENTATION, LR);
            topLeftColorButton.setText("Left Color");
            bottomRightColorButton.setText("Right Color");
         }
      });

      tbButton_ = new JRadioButton("Top-Bottom Split");
      if (orientation_.equals(TB))
         tbButton_.setSelected(true);
      else
         tbButton_.setSelected(false);
      sp.putConstraint(SpringLayout.NORTH, tbButton_, gap, SpringLayout.NORTH, getContentPane());
      sp.putConstraint(SpringLayout.WEST, tbButton_, 135, SpringLayout.WEST, lrButton_);
      tbButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            orientation_ = TB;
            prefs_.put(ORIENTATION, TB);
            topLeftColorButton.setText("Top Color");
            bottomRightColorButton.setText("Bottom Color");
         }
      });

      // ButtonGroup for the two radio buttons
      ButtonGroup orientationChoice = new ButtonGroup();
      orientationChoice.add(lrButton_);
      orientationChoice.add(tbButton_);

      getContentPane().add(lrButton_);
      getContentPane().add(tbButton_);

      // Top/Left Color Chooser
      topLeftColorButton.setFont(new Font("", Font.PLAIN, 10)); 
      topLeftColorButton.setText("Left Color");
      topLeftColorButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            col1_ = JColorChooser.showDialog(getContentPane(), "Choose left/top color", col1_);
            topLeftColorButton.setForeground(col1_);
            prefs_.putInt(TOPLEFTCOLOR, col1_.getRGB());
         }
      });
      topLeftColorButton.setForeground(col1_);
      topLeftColorButton.setPreferredSize(buttonSize);
      sp.putConstraint(SpringLayout.NORTH, topLeftColorButton, 3 * gap, SpringLayout.SOUTH, lrButton_);
      sp.putConstraint(SpringLayout.WEST, topLeftColorButton, 3* gap, SpringLayout.WEST, getContentPane());
      getContentPane().add(topLeftColorButton);


      // Bottom/Right Color Chooser
      bottomRightColorButton.setFont(new Font("", Font.PLAIN, 10)); 
      bottomRightColorButton.setText("Right Color");
      bottomRightColorButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            col2_ = JColorChooser.showDialog(getContentPane(), "Choose right/bottom color", col2_);
            bottomRightColorButton.setForeground(col2_);
            prefs_.putInt(BOTTOMRIGHTCOLOR, col2_.getRGB());
         }
      });
      bottomRightColorButton.setForeground(col2_);
      bottomRightColorButton.setPreferredSize(buttonSize);
      sp.putConstraint(SpringLayout.NORTH, bottomRightColorButton, 3 * gap, SpringLayout.SOUTH, tbButton_);
      sp.putConstraint(SpringLayout.EAST, bottomRightColorButton, -3 * gap, SpringLayout.EAST, getContentPane());
      getContentPane().add(bottomRightColorButton);

      // Snap Button
      final JButton buttonSnap = new JButton();
      buttonSnap.setIconTextGap(6);
      buttonSnap.setText("Snap");
      buttonSnap.setIcon(SwingResourceManager.getIcon(SplitView.class, "/org/micromanager/icons/camera.png")); 
      buttonSnap.setFont(new Font("", Font.PLAIN, 10)); 
      buttonSnap.setToolTipText("Snap single image");
      buttonSnap.setPreferredSize(buttonSize);
      buttonSnap.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            doSnap();
            addMetaData ();
            savePosition();
         }
      });
      sp.putConstraint(SpringLayout.WEST, buttonSnap, 3 * gap, SpringLayout.WEST, getContentPane());
      sp.putConstraint(SpringLayout.SOUTH, buttonSnap, -3 * gap, SpringLayout.SOUTH, getContentPane());
      getContentPane().add(buttonSnap);

      // initialize timer
      interval_ = 30;
      ActionListener timerHandler = new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            doSnap();
         }
      };
      timer_ = new Timer((int)interval_, timerHandler);
      timer_.stop();

      // Live button
      toggleButtonLive_ = new JToggleButton();
      toggleButtonLive_.setIcon(SwingResourceManager.getIcon(SplitView.class, "/org/micromanager/icons/camera_go.png"));
      toggleButtonLive_.setIconTextGap(6);
      toggleButtonLive_.setToolTipText("Continuously acquire images");
      toggleButtonLive_.setFont(new Font("Arial", Font.BOLD, 10));
      toggleButtonLive_.setPreferredSize(buttonSize);
      toggleButtonLive_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            if (toggleButtonLive_.isSelected()){
               timer_.setDelay((int)interval_);
               enableLiveMode(true);
               toggleButtonLive_.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/cancel.png"));
            } else {
               enableLiveMode(false);
               toggleButtonLive_.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/camera_go.png"));
            }
         }
      });

      toggleButtonLive_.setText("Live");
      getContentPane().add(toggleButtonLive_);
      sp.putConstraint(SpringLayout.EAST, toggleButtonLive_, -3 * gap, SpringLayout.EAST, getContentPane());
      sp.putConstraint(SpringLayout.SOUTH, toggleButtonLive_, -3 * gap, SpringLayout.SOUTH, getContentPane());

   }

   private void doSnap()
   {
      calculateSize();
      addSnapToImage();
   }

   private void enableLiveMode(boolean enable) 
   {
      try {
         if (enable) {
            if (timer_.isRunning())
               return;

            // turn off auto shutter and open the shutter
            autoShutterOrg_ = core_.getAutoShutter();
            shutterLabel_ = core_.getShutterDevice();                           
            if (shutterLabel_.length() > 0)
               shutterOrg_ = core_.getShutterOpen();
            core_.setAutoShutter(false);
            // only open the shutter when we have one and the Auto shutter checkbox was checked
            if ((shutterLabel_.length() > 0) && autoShutterOrg_)                
               core_.setShutterOpen(true); 

            timer_.start();
            toggleButtonLive_.setText("Stop"); 
         }
         else {
            if (!timer_.isRunning())
               return;
            timer_.stop();

            // add metadata
            addMetaData ();

            // save window position since it is not saved on close
            savePosition();

            // restore auto shutter and close the shutter                       
            if (shutterLabel_.length() > 0)                                     
               core_.setShutterOpen(shutterOrg_);                               
            core_.setAutoShutter(autoShutterOrg_);                              

            toggleButtonLive_.setText("Live"); 
         }
      } catch (Exception err) {                                                  
        ReportingUtils.showError(err);
      }
   }

   private void calculateSize()
   {
      imgDepth_ = core_.getBytesPerPixel();
      width_ = (int) core_.getImageWidth();
      height_ = (int) core_.getImageHeight();
      if (!orientation_.equals(LR) && !orientation_.equals(TB))
         orientation_ = LR;
      if (orientation_.equals(LR)) {
         newWidth_ = width_/2;
         newHeight_ = height_;
      } else if (orientation_.equals(TB)) {
         newWidth_ = width_;
         newHeight_ = height_/2;
      }
   }

   private void addSnapToImage()
   {
      Object img;
      ImageProcessor tmpImg;
      try {
         core_.snapImage();
         img = core_.getImage();
         if (imgDepth_ == 1)
            tmpImg = new ByteProcessor(width_, height_);
         else  if (imgDepth_ == 2)
            tmpImg = new ShortProcessor(width_, height_);
         else  // TODO throw error
            return;
         tmpImg.setPixels(img);

         if (image5D_ != null && image5DWindow_ != null && image5DWindow_.isVisible()) {
            if (newWidth_ != image5D_.getWidth() || newHeight_ != image5D_.getHeight())
               image5DWindow_.close();
         }

         if (image5D_ != null && image5DWindow_ != null && (image5D_.getBitDepth() != (imgDepth_ * 8)))
            image5DWindow_.close();


         if (image5D_ == null || image5DWindow_ == null || image5DWindow_.isClosed()) {
            ImageStack stack = new ImageStack(newWidth_, newHeight_);
            tmpImg.setRoi(0,0, newWidth_, newHeight_);
            stack.addSlice("Left", tmpImg.crop());
            if (orientation_.equals(LR))
               tmpImg.setRoi(newWidth_, 0, newWidth_, height_);
            else if (orientation_.equals(TB))
               tmpImg.setRoi(0, newHeight_, newWidth_, newHeight_);
            stack.addSlice("Right", tmpImg.crop());
            ReportingUtils.logMessage("Opening new Image5D" + " "  + newWidth_ + " " +  newHeight_);
            image5D_ = new Image5D("Split-View", stack, 2, 1, 1);
            Calibration cal = new Calibration();
            double pixSizeUm = core_.getPixelSizeUm();
            if (pixSizeUm > 0) {
               cal.setUnit("um");
               cal.pixelWidth = pixSizeUm;
               cal.pixelHeight = pixSizeUm;
            }
            image5D_.setCalibration(cal);

            ChannelCalibration cal1 = new ChannelCalibration();
            ChannelCalibration cal2 = new ChannelCalibration();
            if (orientation_.equals(LR)) {
               cal1.setLabel ("Left");
               cal2.setLabel ("Right");
            }
            else if (orientation_.equals(TB)) {
               cal1.setLabel ("Top");
               cal2.setLabel ("Bottom");
            }
            image5D_.setChannelCalibration(1, cal1);
            image5D_.setChannelCalibration(2, cal2);
            image5D_.setChannelColorModel(1, ChannelDisplayProperties.createModelFromColor(col1_));
            image5D_.setChannelColorModel(2, ChannelDisplayProperties.createModelFromColor(col2_));

            image5D_.show();
            image5D_.setDisplayMode(ChannelControl.OVERLAY);
            image5DWindow_ = (Image5DWindow) image5D_.getWindow();
            WindowListener wndCloser = new WindowAdapter() {
               public void windowClosing(WindowEvent e) {
                  enableLiveMode(false);
                  toggleButtonLive_.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/camera_go.png"));
                  toggleButtonLive_.setSelected(false);
               }
            };
            image5DWindow_.addWindowListener(wndCloser);
         } else {
            // Split the image in two halfs and add them to Image5D
            tmpImg.setRoi(0,0, newWidth_, newHeight_);
            image5D_.setPixels(tmpImg.crop().getPixels(), 1, 1, 1);
            if (orientation_ == LR)
               tmpImg.setRoi(newWidth_, 0, newWidth_, height_);
            else if (orientation_ == TB)
               tmpImg.setRoi(0, newHeight_, newWidth_, newHeight_);

            image5D_.setPixels(tmpImg.crop().getPixels(), 2, 1, 1);
            image5D_.updateAndDraw();
         }
         image5DWindow_.toFront();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   private void addMetaData () 
   {
      AcquisitionData ad = new AcquisitionData();
      try {
         ad.createNew();
         ad.setImagePhysicalDimensions(image5D_.getWidth(), image5D_.getHeight(), (int)imgDepth_);
         ad.setDimensions(1, 2, 1);

         // set channel names depending on the orientation
         String channelNames[] = new String[2];
         if (orientation_.equals(LR)) {
            channelNames[0] = "Left";
            channelNames[1] = "Right";
         } else {
            channelNames[0] = "Top";
            channelNames[0] = "Bottom";
         }
         ad.setChannelNames(channelNames);

         // set channel colors
         Color colors[] = {col1_, col2_};
         ad.setChannelColors(colors);

         // set other data
         ad.setComment("Split View 1.2");
         ad.setPixelSizeUm(core_.getPixelSizeUm());
         double exposureMs = core_.getExposure();

         // set image data
         //String channel = "";
         for (int i=0; i<2; i++) {
            /*
            if (i==0 && orientation_ == LR)
               channel = "Left";
            if (i==1 && orientation_ == LR)
               channel = "Right";
            if (i==0 && orientation_ == TB)
               channel = "Top";
            if (i==1 && orientation_ == TB)
               channel = "Bottom";
            */
            ad.insertImageMetadata(0, i, 0);
            ad.setImageValue(0, i, 0, ImagePropertyKeys.EXPOSURE_MS, exposureMs);
            Annotator.setStateMetadata(ad, 0, i, 0, core_.getSystemStateCache());
         }

      } catch (MMAcqDataException e) {
         ReportingUtils.logError(e);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      if (image5DWindow_ != null)
         image5DWindow_.setAcquisitionData(ad);

   }
}
