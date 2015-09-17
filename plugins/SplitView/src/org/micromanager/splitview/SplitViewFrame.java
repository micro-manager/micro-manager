///////////////////////////////////////////////////////////////////////////////
//FILE:          SplitViewFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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


/**

 * Created on Aug 28, 2011, 9:41:57 PM
 */
package org.micromanager.splitview;

import com.swtdesigner.SwingResourceManager;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

import java.util.prefs.Preferences;

import javax.swing.JColorChooser;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.micromanager.MMStudio;
import org.micromanager.api.MMTags;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/** 
 * Micro-Manager plugin that can split the acquired image top-down or left-right
 * and display the split image as a two channel image
 * Work is in progress to apply this transform also during acquisition
 *
 * @author nico
 */
public class SplitViewFrame extends javax.swing.JFrame {

   private final ScriptInterface gui_;
   private final CMMCore core_;
   private Preferences prefs_;
   private long imgDepth_;
   private int width_;
   private int height_;
   private int newWidth_;
   private int newHeight_;
   private String orientation_;
   Color col1_;
   Color col2_;
   private int frameXPos_ = 100;
   private int frameYPos_ = 100;
   private Timer timer_;
   private double interval_ = 30;
   private static final String ACQNAME = "Split View";
   public static final String LR = "lr";
   public static final String TB = "tb";
   private static final String TOPLEFTCOLOR = "TopLeftColor";
   private static final String BOTTOMRIGHTCOLOR = "BottomRightColor";
   private static final String ORIENTATION = "Orientation";
   private static final String FRAMEXPOS = "FRAMEXPOS";
   private static final String FRAMEYPOS = "FRAMEYPOS";
   private boolean autoShutterOrg_;
   private String shutterLabel_;
   private boolean shutterOrg_;
   private SplitViewProcessor processor_;

   

   public SplitViewFrame(SplitViewProcessor processor, 
         ScriptInterface gui) throws Exception {
      processor_ = processor;
      gui_ = gui;
      core_ = gui_.getMMCore();
      prefs_ = Preferences.userNodeForPackage(this.getClass());

      col1_ = new Color(prefs_.getInt(TOPLEFTCOLOR, Color.red.getRGB()));
      col2_ = new Color(prefs_.getInt(BOTTOMRIGHTCOLOR, Color.green.getRGB()));
      orientation_ = prefs_.get(ORIENTATION, LR);
      processor_.setOrientation(orientation_);

      // initialize timer
      // TODO: Replace with Sequence-based live mode
      interval_ = 30;
      ActionListener timerHandler = new ActionListener() {

         @Override
         public void actionPerformed(ActionEvent evt) {
            calculateSize();
            addSnapToImage();    
         }
      };
      timer_ = new Timer((int) interval_, timerHandler);
      timer_.stop();


      frameXPos_ = prefs_.getInt(FRAMEXPOS, frameXPos_);
      frameYPos_ = prefs_.getInt(FRAMEYPOS, frameYPos_);

      Font buttonFont = new Font("Arial", Font.BOLD, 10);

      initComponents();

      setLocation(frameXPos_, frameYPos_);

      setBackground(gui_.getBackgroundColor());

      Dimension buttonSize = new Dimension(120, 20);

      lrRadioButton.setSelected(orientation_.equals(LR));
      if (orientation_.equals(LR)) {
         topLeftColorButton.setText("Left Color");
         bottomRightColorButton.setText("Right Color");
      }
      tbRadioButton.setSelected(orientation_.equals(TB));
      if (orientation_.equals(TB)) {
         topLeftColorButton.setText("Top Color");
         bottomRightColorButton.setText("Bottom Color");
      }

      topLeftColorButton.setForeground(col1_);
      topLeftColorButton.setPreferredSize(buttonSize);

      bottomRightColorButton.setForeground(col2_);
      bottomRightColorButton.setPreferredSize(buttonSize);

      liveButton.setIconTextGap(6);
      liveButton.setFont(buttonFont);
      liveButton.setIcon(SwingResourceManager.getIcon(MMStudio.class, "/org/micromanager/icons/camera_go.png"));
      liveButton.setText("Live");

      snapButton.setIconTextGap(6);
      snapButton.setText("Snap");
      snapButton.setIcon(SwingResourceManager.getIcon(SplitView.class, "/org/micromanager/icons/camera.png"));
      snapButton.setFont(buttonFont);
      snapButton.setToolTipText("Snap single image");

   }

   private void doSnap() {
      calculateSize();
      if (!gui_.acquisitionExists(ACQNAME)) {
         try {
            openAcq();
         } catch (MMScriptException ex) {
            ReportingUtils.showError(ex, "Failed to open acquisition Window");
         }
      }
      if (gui_.acquisitionExists(ACQNAME)) {
         addSnapToImage();
      }
   }

   private void enableLiveMode(boolean enable) {
      try {
         if (enable) {
            if (gui_.isLiveModeOn()) {
               gui_.enableLiveMode(false);
            }
            if (timer_.isRunning()) {
               return;
            }
            if (!gui_.acquisitionExists(ACQNAME)) {
               try {
                  calculateSize();
                  openAcq();
               } catch (MMScriptException ex) {
                  ReportingUtils.showError(ex, "Failed to open acquisition Window");
               }
            }

            // turn off auto shutter and open the shutter
            autoShutterOrg_ = core_.getAutoShutter();
            shutterLabel_ = core_.getShutterDevice();
            if (shutterLabel_.length() > 0) {
               shutterOrg_ = core_.getShutterOpen();
            }
            core_.setAutoShutter(false);
            // only open the shutter when we have one and the Auto shutter checkbox was checked
            if ((shutterLabel_.length() > 0) && autoShutterOrg_) {
               core_.setShutterOpen(true);
            }

            timer_.start();
            liveButton.setText("Stop");
         } else {
            if (!timer_.isRunning()) {
               return;
            }
            timer_.stop();

            // add metadata
            //addMetaData ();

            // save window position since it is not saved on close
            // savePosition();

            // restore auto shutter and close the shutter                       
            if (shutterLabel_.length() > 0) {
               core_.setShutterOpen(shutterOrg_);
            }
            core_.setAutoShutter(autoShutterOrg_);

            liveButton.setText("Live");
         }
      } catch (Exception err) {
         ReportingUtils.showError(err);
      }
   }

   private void calculateSize() {
      imgDepth_ = core_.getBytesPerPixel();
      
      width_ = (int) core_.getImageWidth();
      height_ = (int) core_.getImageHeight();

      newWidth_ = processor_.calculateWidth(width_);
      newHeight_ = processor_.calculateHeight(height_);
   }
   
   private void openAcq() throws MMScriptException {

      gui_.openAcquisition(ACQNAME, "", 1, 2, 1, 1, true, false);
      gui_.initializeAcquisition(ACQNAME, newWidth_, newHeight_, (int) imgDepth_, 8 * (int)imgDepth_);
      gui_.promptToSaveAcquisition(ACQNAME, false);
      gui_.setChannelColor(ACQNAME, 0, col1_);
      gui_.setChannelColor(ACQNAME, 1, col2_);
      if (orientation_.equals(LR)) {
         gui_.setChannelName(ACQNAME, 0, "Left");
         gui_.setChannelName(ACQNAME, 1, "Right");
      } else {
         gui_.setChannelName(ACQNAME, 0, "Top");
         gui_.setChannelName(ACQNAME, 1, "Bottom");
      }
   }

   private void addSnapToImage() {
     TaggedImage img;
      ImageProcessor tmpImg;
      try {
         core_.snapImage();
         img = core_.getTaggedImage();
         if (imgDepth_ == 1) {
            tmpImg = new ByteProcessor(width_, height_);
         } else if (imgDepth_ == 2) {
            tmpImg = new ShortProcessor(width_, height_);
         } else // TODO throw error
         {
            return;
         }
         tmpImg.setPixels(img.pix);

         if (!gui_.acquisitionExists(ACQNAME)) {
            enableLiveMode(false);
            return;
         } else if (gui_.getAcquisitionImageHeight(ACQNAME) != newHeight_
                 || gui_.getAcquisitionImageWidth(ACQNAME) != newWidth_
                 || gui_.getAcquisitionImageByteDepth(ACQNAME) != imgDepth_) {
            gui_.closeAcquisitionWindow(ACQNAME);
            gui_.closeAcquisition(ACQNAME);
            openAcq();
         }

         tmpImg.setRoi(0, 0, newWidth_, newHeight_);
         // first channel
         TaggedImage firstChannel = new TaggedImage(tmpImg.crop().getPixels(), img.tags);
         firstChannel.tags.put(MMTags.Image.WIDTH, newWidth_);
         firstChannel.tags.put(MMTags.Image.HEIGHT, newHeight_);
         gui_.addImageToAcquisition(ACQNAME, 0, 0, 0, 0, firstChannel);
         
         // second channel
         if (orientation_.equals(LR)) {
            tmpImg.setRoi(newWidth_, 0, newWidth_, height_);
         } else if (orientation_.equals(TB)) {
            tmpImg.setRoi(0, newHeight_, newWidth_, newHeight_);
         }
         TaggedImage secondChannel = new TaggedImage(tmpImg.crop().getPixels(), img.tags);
         secondChannel.tags.put(MMTags.Image.WIDTH, newWidth_);
         secondChannel.tags.put(MMTags.Image.HEIGHT, newHeight_);
         gui_.addImageToAcquisition(ACQNAME, 0, 1, 0, 0, secondChannel);

      } catch (Exception e) {
         if (gui_.isLiveModeOn())
            enableLiveMode(false);
         else
            ReportingUtils.showError(e);
      }
   }

   public void safePrefs() {
      prefs_.putInt(FRAMEXPOS, this.getX());
      prefs_.putInt(FRAMEYPOS, this.getY());

   }

   /** This method is called from within the constructor to
    * initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is
    * always regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      buttonGroup1 = new javax.swing.ButtonGroup();
      buttonGroup2 = new javax.swing.ButtonGroup();
      buttonGroup3 = new javax.swing.ButtonGroup();
      lrRadioButton = new javax.swing.JRadioButton();
      tbRadioButton = new javax.swing.JRadioButton();
      topLeftColorButton = new javax.swing.JButton();
      bottomRightColorButton = new javax.swing.JButton();
      snapButton = new javax.swing.JButton();
      liveButton = new javax.swing.JButton();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("SplitView");
      addWindowListener(new java.awt.event.WindowAdapter() {
         public void windowClosed(java.awt.event.WindowEvent evt) {
            formWindowClosed(evt);
         }
      });

      buttonGroup1.add(lrRadioButton);
      lrRadioButton.setText("Left-Right Split");
      lrRadioButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            lrRadioButtonActionPerformed(evt);
         }
      });

      buttonGroup1.add(tbRadioButton);
      tbRadioButton.setText("Top-Bottom Split");
      tbRadioButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            tbRadioButtonActionPerformed(evt);
         }
      });

      topLeftColorButton.setText("Left Color");
      topLeftColorButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            topLeftColorButtonActionPerformed(evt);
         }
      });

      bottomRightColorButton.setText("Right Color");
      bottomRightColorButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            bottomRightColorButtonActionPerformed(evt);
         }
      });

      snapButton.setText("Snap");
      snapButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            snapButtonActionPerformed(evt);
         }
      });

      liveButton.setText("Live");
      liveButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            liveButtonActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
               .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                  .addGap(9, 9, 9)
                  .addComponent(lrRadioButton))
               .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                  .addContainerGap()
                  .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(layout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(snapButton))
                     .addComponent(topLeftColorButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createSequentialGroup()
                  .addGap(18, 18, 18)
                  .addComponent(tbRadioButton))
               .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                     .addGap(21, 21, 21)
                     .addComponent(liveButton))
                  .addComponent(bottomRightColorButton, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addContainerGap())
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(lrRadioButton)
               .addComponent(tbRadioButton))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(topLeftColorButton)
               .addComponent(bottomRightColorButton))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(liveButton)
               .addComponent(snapButton))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void lrRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lrRadioButtonActionPerformed
       processor_.setOrientation(LR);
       orientation_ = LR;
       prefs_.put(ORIENTATION, LR);
       topLeftColorButton.setText("Left Color");
       bottomRightColorButton.setText("Right Color");
    }//GEN-LAST:event_lrRadioButtonActionPerformed

    private void tbRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tbRadioButtonActionPerformed
       processor_.setOrientation(TB);
       orientation_ = TB;
       prefs_.put(ORIENTATION, TB);
       topLeftColorButton.setText("Top Color");
       bottomRightColorButton.setText("Bottom Color");
    }//GEN-LAST:event_tbRadioButtonActionPerformed

    private void snapButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_snapButtonActionPerformed
       doSnap();
    }//GEN-LAST:event_snapButtonActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
       safePrefs();
    }//GEN-LAST:event_formWindowClosed

    private void liveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_liveButtonActionPerformed
       if (timer_.isRunning()) {
          enableLiveMode(false);
          liveButton.setIcon(SwingResourceManager.getIcon(MMStudio.class, "/org/micromanager/icons/camera_go.png"));

       } else {
          timer_.setDelay((int) interval_);
          enableLiveMode(true);
          liveButton.setIcon(SwingResourceManager.getIcon(MMStudio.class, "/org/micromanager/icons/cancel.png"));
       }
    }//GEN-LAST:event_liveButtonActionPerformed

    private void topLeftColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_topLeftColorButtonActionPerformed
       col1_ = JColorChooser.showDialog(getContentPane(), "Choose left/top color", col1_);
       topLeftColorButton.setForeground(col1_);
       prefs_.putInt(TOPLEFTCOLOR, col1_.getRGB());
       try {
          if (gui_.acquisitionExists(ACQNAME)) {
             gui_.setChannelColor(ACQNAME, 0, col1_);
          }
       } catch (MMScriptException ex) {
          ReportingUtils.logError(ex);
       }
    }//GEN-LAST:event_topLeftColorButtonActionPerformed

    private void bottomRightColorButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bottomRightColorButtonActionPerformed
       col2_ = JColorChooser.showDialog(getContentPane(), "Choose right/bottom color", col2_);
       bottomRightColorButton.setForeground(col2_);
       prefs_.putInt(BOTTOMRIGHTCOLOR, col2_.getRGB());
       try {
          if (gui_.acquisitionExists(ACQNAME)) {
             gui_.setChannelColor(ACQNAME, 0, col2_);
          }
       } catch (MMScriptException ex) {
          ReportingUtils.logError(ex);
       }
    }//GEN-LAST:event_bottomRightColorButtonActionPerformed

    public JSONArray getColors() throws JSONException {
       JSONArray myColors = new JSONArray();
       myColors.put(0, (Object) col1_.getRGB());
       myColors.put(1, (Object) col2_.getRGB());
       return myColors;
    }
    
   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton bottomRightColorButton;
   private javax.swing.ButtonGroup buttonGroup1;
   private javax.swing.ButtonGroup buttonGroup2;
   private javax.swing.ButtonGroup buttonGroup3;
   private javax.swing.JButton liveButton;
   private javax.swing.JRadioButton lrRadioButton;
   private javax.swing.JButton snapButton;
   private javax.swing.JRadioButton tbRadioButton;
   private javax.swing.JButton topLeftColorButton;
   // End of variables declaration//GEN-END:variables

   public Color getColor1() {
      return col1_;
   }

   public Color getColor2() {
      return col2_;
   }
}
