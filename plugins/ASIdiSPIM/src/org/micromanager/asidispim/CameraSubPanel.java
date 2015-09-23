///////////////////////////////////////////////////////////////////////////////
//FILE:          CameraSubPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;

import org.micromanager.asidispim.Data.Cameras;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.UpdateFromPropertyListenerInterface;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudio;
import org.micromanager.internalinterfaces.LiveModeListener;

import com.swtdesigner.SwingResourceManager;

import net.miginfocom.swing.MigLayout;


/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public final class CameraSubPanel extends ListeningJPanel implements LiveModeListener {
   
   private final Devices devices_;
   private final Prefs prefs_;
   private final Cameras cameras_;
   private final ScriptInterface gui_;
   private final Devices.Sides side_;
   private final String instanceLabel_;
   private final JComboBox cameraBox_;
   private final JToggleButton camAButton_;
   private final JToggleButton camBButton_;
   private final JToggleButton camMultiButton_;
   private final JToggleButton camBotButton_;
//   private final JToggleButton testAcqButton_;
   private final JToggleButton toggleButtonLive_;
   private List<UpdateFromPropertyListenerInterface> privateListeners_;
   
   /**
    * 
    * @param gui Micro-Manager script interface
    * @param cameras instance of Camera class
    * @param devices the (single) instance of the Devices class
    * @param instanceLabel name of the panel calling this class
    * @param side A, B, or none
    * @param prefs
    * @param showLiveButton if false then the live button is omitted
    */
   public CameraSubPanel(ScriptInterface gui, 
           Cameras cameras, 
           Devices devices, 
           String instanceLabel,
           Devices.Sides side, 
           Prefs prefs) {    
      super (MyStrings.PanelNames.CAMERA_SUBPANEL.toString() + instanceLabel,
            new MigLayout(
              "", 
              "[right]8[center]",
              "[]8[]"));
      setBorder(BorderFactory.createLineBorder(ASIdiSPIM.borderColor));

      final int columnWidth = 105;
      
      cameras_ = cameras;
      devices_ = devices;
      side_ = side;
      prefs_ = prefs;
      instanceLabel_ = instanceLabel;
      gui_ = gui;
      
      privateListeners_ = new ArrayList<UpdateFromPropertyListenerInterface>();

      JPanel camButtonPanel = new JPanel(new MigLayout(
            "",
            "0[center]4[center]4[center]4[center]0",
            "0[]0"));
      
      switch (side_) {
      case A:
         camAButton_ = makeCameraButton("Imaging", Devices.Keys.CAMERAA);
         camBButton_ = makeCameraButton("  Epi  ", Devices.Keys.CAMERAB);
         break;
      case B:
         camAButton_ = makeCameraButton("Imaging", Devices.Keys.CAMERAB);
         camBButton_ = makeCameraButton("  Epi  ", Devices.Keys.CAMERAA);
         break;
      case NONE:
      default:
         camAButton_ = makeCameraButton("Path A", Devices.Keys.CAMERAA);
         camBButton_ = makeCameraButton("Path B", Devices.Keys.CAMERAB);
         break;
      }

      camMultiButton_ = makeCameraButton("Multi", Devices.Keys.MULTICAMERA); 
      camBotButton_ = makeCameraButton("Bottom", Devices.Keys.CAMERALOWER);

      camButtonPanel.add(camAButton_);
      camButtonPanel.add(camMultiButton_);
      camButtonPanel.add(camBButton_);
      camButtonPanel.add(camBotButton_);
      
      // make sure only one can be selected at a time
      ButtonGroup group = new ButtonGroup();
      group.add(camAButton_);
      group.add(camBButton_);
      group.add(camMultiButton_);
      group.add(camBotButton_);

      add(camButtonPanel, "span 2, wrap");
      
      add(new JLabel("On tab activate:"));
      cameraBox_ = makeCameraSelectionBox(columnWidth);
      add(cameraBox_, "wrap");
      
      JPanel liveButtonPanel = new JPanel(new MigLayout(
            "",
            "[left]8[right]",
            "0[]0"));
      
//      testAcqButton_ = new JToggleButton("Test Acq.");
            
      toggleButtonLive_ = new JToggleButton();
      toggleButtonLive_.setMargin(new Insets(2, 15, 2, 15));
      toggleButtonLive_.setIconTextGap(6);
      toggleButtonLive_.setToolTipText("Continuous live view");
      setLiveButtonAppearance(gui_.isLiveModeOn());
      toggleButtonLive_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setLiveButtonAppearance(!gui_.isLiveModeOn());
            cameras_.enableLiveMode(!gui_.isLiveModeOn());
         }
      });
      
//      liveButtonPanel.add(testAcqButton_, "growy, shrinky");
      liveButtonPanel.add(toggleButtonLive_, "width " + columnWidth + "px");
      
      add(liveButtonPanel, "skip 1");
   }
   
   /**
    * required by LiveModeListener interface
    * @param enable
    */
   @Override
   public void liveModeEnabled(boolean enable) {
      setLiveButtonAppearance(enable);
   } 
   
   /** 
   * Changes the looks of the live button 
   * @param enable - true: live mode is switched on 
   */ 
   public final void setLiveButtonAppearance(boolean enable) {
      toggleButtonLive_.setIcon(enable ? SwingResourceManager.
            getIcon(MMStudio.class,
            "/org/micromanager/icons/cancel.png")
            : SwingResourceManager.getIcon(MMStudio.class,
                  "/org/micromanager/icons/camera_go.png"));
      toggleButtonLive_.setSelected(false);
      toggleButtonLive_.setText(enable ? "Stop Live" : "Live");
   }
   
   
   private JComboBox makeCameraSelectionBox(int maxBoxWidth) {
      JComboBox cameraBox = new JComboBox();
      cameraBox.setMaximumSize(new Dimension(maxBoxWidth, 30));
      CameraSelectionBoxListener csbl = new CameraSelectionBoxListener(cameraBox);
      cameraBox.addActionListener(csbl);
      devices_.addListener(csbl);
      // intentionally set from prefs after adding listeners so
      // programmatic change from prefs will be handled like user change
      String selectedItem = prefs_.getString(instanceLabel_, 
            Prefs.Keys.CAMERA, Devices.Keys.CAMERAPREVIOUS.toString());
      cameraBox.setSelectedItem(selectedItem);
      return cameraBox;
   }
   
   /**
    * Listener for selection boxes that attach cameras
    */
   private class CameraSelectionBoxListener implements ActionListener, DevicesListenerInterface {
      JComboBox box_;
      HashMap<String, Cameras.CameraData> CameraDataHash_;
      boolean updatingList_;
      
      public CameraSelectionBoxListener(JComboBox box) {
         box_ = box;
         CameraDataHash_ = new HashMap<String, Cameras.CameraData>();
         this.updateCameraSelections();  // do initial rendering
      }    

      /**
       * This will be called whenever the user selects a new item, but also when
       * the tab to which this selectionbox belongs is selected (via gotSelected() method)
       * Save the selection to preferences every time (easy but inefficient)
       *
       * @param ae
       */
      @Override
      public void actionPerformed(ActionEvent ae) {
         if (updatingList_ == true) {
            return;  // don't go through this if we are rebuilding selections
         }
         Cameras.CameraData sel = CameraDataHash_.get( (String) box_.getSelectedItem());
         if (cameras_.getCurrentCamera() != sel.deviceKey) {
            cameras_.setCamera(sel.deviceKey);
         }
         prefs_.putString(instanceLabel_, Prefs.Keys.CAMERA, (String) box_.getSelectedItem());
      }
      
      /**
       * called whenever one of the devices is changed in the "Devices" tab
       */
      @Override
      public void devicesChangedAlert() {
         updateCameraSelections();
      }
      
      /**
       * Resets the items in the combo box based on current contents of device tab.
       * Besides being called on original combo box creation, it is called whenever something in the devices tab is changed.
       * Based on similar code for joystick combo box, which is more complex
       */
      private void updateCameraSelections() {
         // save the existing selection if it exists
         String itemOrig = (String) box_.getSelectedItem();
         
         // get the appropriate list of strings (in form of CameraData array)
         Cameras.CameraData[] CameraDataItems;
         CameraDataHash_.clear();
         CameraDataItems = cameras_.getCameraData();

         boolean itemInNew = false;
         updatingList_ = true;
         box_.removeAllItems();
         for (Cameras.CameraData a : CameraDataItems) {
            String s = a.displayString;
            if (side_ != Devices.Sides.NONE) {  // add imaging/epi label as appropriate
               if (side_ == a.side) {
                  s += " (Imaging)";
               } else if (Devices.getOppositeSide(side_) == a.side){
                  s += " (Epi)";
               }
            }
            box_.addItem(s);
            CameraDataHash_.put(s, a);
            if (s.equals(itemOrig)) {
               itemInNew = true;
            }
         }
         updatingList_ = false;
         
         // restore the original selection if it's still present
         if (itemInNew) {
            box_.setSelectedItem(itemOrig);
         }
         else {
            box_.setSelectedItem(Devices.Keys.CAMERAPREVIOUS);
         }
      }//updateCameraSelections
      
   }//class CameraSelectionBoxListener
   
   
   /**
   * Should be called when enclosing panel gets focus.
   */
   @Override
  public void gotSelected() {
      cameraBox_.setSelectedItem(cameraBox_.getSelectedItem());
      // update which camera button is selected based on current camera
      for (UpdateFromPropertyListenerInterface listener : privateListeners_) {
         listener.updateFromProperty();
      }
  }

   private JToggleButton makeCameraButton(String label, Devices.Keys devKey) {

      class setCameraListener implements ActionListener,
      UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         private final JToggleButton jtb_;
         private final Devices.Keys devKey_;
         
         public setCameraListener(JToggleButton jtb, Devices.Keys devKey) {
            jtb_ = jtb;
            devKey_ = devKey;
         }
         
         @Override
         public void actionPerformed(ActionEvent e) {
            if (cameras_.getCurrentCamera() != devKey_) {
               cameras_.setCamera(devKey_);
            }
         }

         @Override
         public void updateFromProperty() {
            Devices.Keys currentCam = cameras_.getCurrentCamera();
            jtb_.setSelected(currentCam == devKey_);
         }

         @Override
         public void devicesChangedAlert() {
            jtb_.setEnabled(devices_.isValidMMDevice(devKey_));
         }
      }

      JToggleButton jtb = new JToggleButton(label);
      jtb.setMargin(new Insets(4,4,4,4));
      setCameraListener l = new setCameraListener(jtb, devKey);
      jtb.addActionListener(l);
      privateListeners_.add((UpdateFromPropertyListenerInterface)l);
      devices_.addListener((DevicesListenerInterface) l);
      jtb.setEnabled(devices_.isValidMMDevice(devKey));
      return jtb;
   }
   
}
