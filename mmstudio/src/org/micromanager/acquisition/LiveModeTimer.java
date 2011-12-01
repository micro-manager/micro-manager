/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class LiveModeTimer extends javax.swing.Timer {

      public static final int MONOCHROME = 0, RGB = 1, MULTI_CAMERA = 2;
      private static final String CCHANNELINDEX = "CameraChannelIndex";

      private int type_;
      private VirtualAcquisitionDisplay multiCamWin_,rgbWin_;
      private CMMCore core_;
      private MMStudioMainFrame gui_;
      private long multiChannelCameraNrCh_;
      private Object monochromeImg_;
      

      public LiveModeTimer(int delay, int type) {
         super(delay, null);
         gui_ = MMStudioMainFrame.getInstance();
         core_ = gui_.getCore();
         setType(type);
      }
  
     public void setType(int type) {
      if (!super.isRunning()) {
         ActionListener[] listeners = super.getActionListeners();
         for (ActionListener a : listeners)
            this.removeActionListener(a);
         type_ = type;
         if (type_ == MONOCHROME) 
         {
            this.addActionListener(monochromeLiveAction());
         } else if (type_ == RGB)   {
            this.addActionListener(rgbLiveAction());
         } else if (type_ == MULTI_CAMERA) {
            multiChannelCameraNrCh_ = core_.getNumberOfCameraChannels();
            this.addActionListener(multiCamLiveAction());    
         }
      }
   }

      @Override
      public void start() {
         initialize();
         try {
            core_.startContinuousSequenceAcquisition(0);
//            Thread.sleep(200);  //needed for core to correctly give 1st tagged image
            super.start();        
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
      }

      @Override
      public void stop() {
         super.stop();
         try {
            core_.stopSequenceAcquisition();
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
      }

      private void initialize() {
         try {
            if (type_ == MULTI_CAMERA) {
               gui_.getAcquisition(gui_.MULTI_CAMERA_ACQ).toFront();
               multiCamWin_ = gui_.getAcquisition(gui_.MULTI_CAMERA_ACQ).getAcquisitionWindow();
            } else if (type_ == RGB) {
               gui_.getAcquisition(gui_.RGB_ACQ).toFront();
               rgbWin_ = gui_.getAcquisition(gui_.RGB_ACQ).getAcquisitionWindow(); 
            }
         } catch (MMScriptException ex) {
            ReportingUtils.showError(ex);
         }
      }

      private ActionListener monochromeLiveAction() {
         return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (core_.getRemainingImageCount() == 0) {
                  System.out.println("zero count");
                  return;
               }
               if (!gui_.isImageWindowOpen())  //check is user closed window             
                  gui_.enableLiveMode(false);
               else
                  try {
                     Object img = core_.getLastImage();
                     if(monochromeImg_ != img) {
                        monochromeImg_ = img;
                        gui_.displayImage(monochromeImg_);
                        System.out.println(System.currentTimeMillis());
                     }
                  } catch (Exception ex) {
                     ReportingUtils.showError(ex);
                     gui_.enableLiveMode(false);
                  }
            }};
      }  
      
      private ActionListener rgbLiveAction() {
         return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (core_.getRemainingImageCount() == 0) {                  
                  return;
               }
               if (rgbWin_.windowClosed())  //check is user closed window
                  gui_.enableLiveMode(false);
               else
                  try {
                     TaggedImage ti = core_.getLastTaggedImage();
                     MDUtils.setChannelIndex(ti.tags, 0);
                     MDUtils.setFrameIndex(ti.tags, 0);
                     MDUtils.setPositionIndex(ti.tags, 0);
                     MDUtils.setSliceIndex(ti.tags, 0);
                     gui_.addImage(gui_.RGB_ACQ, ti, true, true, false);
                     
                  } catch (Exception ex) {
                     ReportingUtils.showError(ex);
                     gui_.enableLiveMode(false);
                  }
            }};
      } 

      private ActionListener multiCamLiveAction() {
         return new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
               if (core_.getRemainingImageCount() == 0) {                  
                  return;
               }
               if (multiCamWin_.windowClosed() || !gui_.acquisitionExists(gui_.MULTI_CAMERA_ACQ)) {
                  gui_.enableLiveMode(false);  //disable live if user closed window
               } else {
                  try {
                     TaggedImage[] images = new TaggedImage[(int)multiChannelCameraNrCh_];
                     String camera = core_.getCameraDevice();

                     TaggedImage ti = core_.getLastTaggedImage();

                     int channel = ti.tags.getInt(camera + "-" + CCHANNELINDEX);
                     images[channel] = ti;
                     int numFound = 1;
                     int index = 1;
                     while (numFound < images.length && index <= 2*images.length ) {      //play with this number
                       try {
                        ti = core_.getNBeforeLastTaggedImage(index);
                       } catch (Exception ex) {                  
                             break;
                       }                  
                        channel = ti.tags.getInt(camera + "-" + CCHANNELINDEX);
                        if (images[channel] == null)
                           numFound++;
                        images[channel] = ti;
                        index++;
                     }
                        
                     if (numFound == images.length) {
                        for (channel = 0; channel < images.length; channel++) {
                           ti = images[channel];
                           ti.tags.put("Channel", core_.getCameraChannelName(channel));
                           MDUtils.setChannelIndex(ti.tags, channel);
                           MDUtils.setFrameIndex(ti.tags, 0);
                           MDUtils.setPositionIndex(ti.tags, 0);
                           MDUtils.setSliceIndex(ti.tags, 0);
                          }


                          int lastChannelToAdd = multiCamWin_.getHyperImage().getChannel() - 1;
                          for (int i = 0; i < images.length; i++) {
                              if (i != lastChannelToAdd) {
                                  gui_.addImage(MMStudioMainFrame.MULTI_CAMERA_ACQ, images[i], false, true, false);
                              }
                          }
                          gui_.addImage(MMStudioMainFrame.MULTI_CAMERA_ACQ, images[lastChannelToAdd], true, true, false);
                      }

                  } catch (Exception ex) {
                     ReportingUtils.logError(ex);
                  }
               }
            }
         };
      }
   
}
