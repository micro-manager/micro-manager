///////////////////////////////////////////////////////////////////////////////
//FILE:          PanelUtils.java
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

package org.micromanager.asidispim.Utils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Hashtable;
import java.util.prefs.Preferences;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.asidispim.ASIdiSPIMFrame;
import org.micromanager.asidispim.Data.Devices;

/**
 *
 * @author nico
 * @author Jon
 */
public class PanelUtils {
   
   /**
    * Listener for Selection boxes that attach joysticks to drives
    */
   public class StageSelectionBoxListener implements ItemListener,
           ActionListener, DevicesListenerInterface {
      Devices.JoystickDevice joystickDevice_;
      JComboBox jc_;
      Devices devices_;
      Preferences prefs_;
      String prefName_;

      public StageSelectionBoxListener(Devices.JoystickDevice joyStickDevice, 
              JComboBox jc, Devices devices, Preferences prefs, String prefName) {
         joystickDevice_ = joyStickDevice;
         jc_ = jc;
         devices_ = devices;
         prefs_ = prefs;
         prefName_ = prefName;
      }    

      /**
       * This will be called whenever the user selects a new item, but also when
       * the tab to which this selectionbox belongs is selected
       *
       * @param ae
       */
      public void actionPerformed(ActionEvent ae) {
         String stage = (String) jc_.getSelectedItem();
         if (stage != null) {
            String[] items = stage.split("-");
            DirectionalDevice dd;
            if (items.length > 1) {
               dd = new DirectionalDevice(items[0],
                       Labels.REVDIRECTIONS.get(items[1]));
            } else {
               dd = new DirectionalDevice(items[0], Labels.Directions.X);
            }
            devices_.setJoystickOutput(joystickDevice_, dd);
            prefs_.put(prefName_, stage);
         }
      }

      public void devicesChangedAlert() {
         String selectedItem = (String) jc_.getSelectedItem();
         jc_.removeAllItems();
         String[] devices;
         if (joystickDevice_ == Devices.JoystickDevice.JOYSTICK) { 
            devices = devices_.getTwoAxisTigerStages();
         } else {
            devices = devices_.getTigerStages();
         }
         for (String device : devices) {
            jc_.addItem(device);
         }
         if (inArray(devices, selectedItem)) {
            jc_.setSelectedItem(selectedItem);
         }
      }

      public void itemStateChanged(ItemEvent e) {
         String stage = (String) jc_.getSelectedItem();
         if (stage != null) {
            
            String[] items = stage.split("-");
            DirectionalDevice dd;
            if (items.length > 1) {
               dd = new DirectionalDevice(items[0],
                       Labels.REVDIRECTIONS.get(items[1]));
            } else {
               dd = new DirectionalDevice(items[0], Labels.Directions.X);
            }
            if (e.getStateChange() == ItemEvent.SELECTED) {
               // do not need to respond, will be done in ActionEvent
            } else if (e.getStateChange() == ItemEvent.DESELECTED) {
               devices_.unsetJoystickOutput(joystickDevice_, dd);
            }
         }
      }

   }//class StageSelectionBoxListener
   
   private boolean inArray (String[] array, String val) {
      for (String test : array) {
         if (val.equals(test)) {
            return true;
         }
      }
      return false;
   }
   
   
   public JComboBox makeJoystickSelectionBox(Devices.JoystickDevice joystickDevice, 
           String[] selections, String selectedItem, Devices devices_, 
           Preferences prefs, String prefName) {
      JComboBox jcb = new JComboBox(selections);
      jcb.setSelectedItem(selectedItem);  
      StageSelectionBoxListener ssbl = new StageSelectionBoxListener(
              joystickDevice , jcb, devices_, prefs, prefName);
      jcb.addActionListener(ssbl);
      jcb.addItemListener(ssbl);
      devices_.addListener(ssbl);
 
      return jcb;
   }
   
   /**
    * makes JSlider for double values where the values are multiplied by a scale factor
    * before internal representation as integer (as JSlider requires)
    * @param key
    * @param min
    * @param max
    * @param init
    * @param scalefactor
    * @return
    */
   public JSlider makeSlider(String key, double min, double max, double init, int scalefactor) {
      
      class sliderListener implements ChangeListener, UpdateFromPropertyListenerInterface {
         JSlider js_;
         String key_;
         int scalefactor_;
         
         public sliderListener(String key, JSlider js, int scalefactor) {
            js_ = js;
            key_ = key;
            scalefactor_ = scalefactor;
         }
         
         public void stateChanged(ChangeEvent ce) {
            if (!((JSlider)ce.getSource()).getValueIsAdjusting()) {  // only change when user releases
               ASIdiSPIMFrame.props_.setPropValue(key_, (float)js_.getValue()/(float)scalefactor_);
            }
         }
         
         public void updateFromProperty() {
            js_.setValue((int)(scalefactor_*ASIdiSPIMFrame.props_.getPropValueFloat(key_)));
         }
         
      }
      
      int intmin = (int)(min*scalefactor);
      int intmax = (int)(max*scalefactor);
      int intinit = (int)(init*scalefactor);
      
      JSlider js = new JSlider(JSlider.HORIZONTAL, intmin, intmax, intinit);
      ChangeListener l = new sliderListener(key, js, scalefactor);
      js.addChangeListener(l);
      js.setMajorTickSpacing(intmax-intmin);
      js.setMinorTickSpacing(scalefactor);
      //Create the label table
      Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
      labelTable.put( new Integer(intmax), new JLabel(Double.toString(max)) );
      labelTable.put( new Integer(intmin), new JLabel(Double.toString(min)) );
      js.setLabelTable( labelTable );
      js.setPaintTicks(true);
      js.setPaintLabels(true);
      return js;
   }

   /**
    * Constructs JCheckBox appropriately set up
    * @param key associated property key
    * @param label the GUI label
    * @param offValue the value of the property when not checked
    * @param onValue the value of the property when checked
    * @return constructed JCheckBox
    */
   public JCheckBox makeCheckBox(String key, String label, String offValue, String onValue) {
      
      /**
       * nested inner class 
       * @author Jon
       */
      class checkBoxListener implements ItemListener, UpdateFromPropertyListenerInterface {
         String key_;
         JCheckBox jc_;
         String offValue_;
         String onValue_;
         
         // TODO should this be private?  also similar nested inner classes...
         public checkBoxListener(String key, JCheckBox jc, String offValue, String onValue) {
            key_ = key;
            jc_ = jc;
            offValue_ = offValue;
            onValue_ = onValue;
         }
         
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               ASIdiSPIMFrame.props_.setPropValue(key_, onValue_);
            } else {
               ASIdiSPIMFrame.props_.setPropValue(key_, offValue_);
            }
         }
         
         public void updateFromProperty() {
            jc_.setSelected(ASIdiSPIMFrame.props_.getPropValueString(key_).equals(onValue_));
         }
         
      }
      
      JCheckBox jc = new JCheckBox(label);
      ItemListener l = new checkBoxListener(key, jc, offValue, onValue);
      jc.addItemListener(l);
      return jc;
   }
   
   
   
   /**
    * Creates spinner for integers in the GUI
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI
    */
   public JSpinner makeSpinnerInteger(String spimParamName, int min, int max) {

      class SpinnerListenerInt implements ChangeListener, UpdateFromPropertyListenerInterface {
         String paramKey_;
         JSpinner sp_;

         public SpinnerListenerInt(String paramKey, JSpinner sp) {
            paramKey_ = paramKey;
            sp_ = sp;
         }

         public void stateChanged(ChangeEvent ce) {
            ASIdiSPIMFrame.props_.setPropValue(paramKey_, ((Integer)sp_.getValue()).intValue());
         }

         public void updateFromProperty() {
            sp_.setValue(ASIdiSPIMFrame.props_.getPropValueInteger(paramKey_));
         }
      }

      SpinnerModel jspm = new SpinnerNumberModel(ASIdiSPIMFrame.props_.getPropValueInteger(spimParamName), min, max, 1);
      JSpinner jsp = new JSpinner(jspm);
      SpinnerListenerInt ispl = new SpinnerListenerInt(spimParamName, jsp);
      jsp.addChangeListener(ispl);
      return jsp;
   }
   
   /**
    * Creates spinner for floats in the GUI
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI
    */
   public JSpinner makeSpinnerFloat(String spimParamName, double min, double max, double step) {
      // same as IntSpinnerListener except
      //  - cast to Float object in stateChanged()
      //  - getPropValueFloat in spimParamsChangedAlert()
      class SpinnerListenerFloat implements ChangeListener, UpdateFromPropertyListenerInterface {
         String paramKey_;
         JSpinner sp_;

         public SpinnerListenerFloat(String paramKey, JSpinner sp) {
            paramKey_ = paramKey;
            sp_ = sp;
         }

         public void stateChanged(ChangeEvent ce) {
            ASIdiSPIMFrame.props_.setPropValue(paramKey_, ((Double)sp_.getValue()).floatValue());
         }

         public void updateFromProperty() {
            sp_.setValue(ASIdiSPIMFrame.props_.getPropValueFloat(paramKey_));
         }
      }
      
      SpinnerModel jspm = new SpinnerNumberModel((double)ASIdiSPIMFrame.props_.getPropValueFloat(spimParamName), min, max, step);
      JSpinner jsp = new JSpinner(jspm);
      SpinnerListenerFloat ispl = new SpinnerListenerFloat(spimParamName, jsp);
      jsp.addChangeListener(ispl);
      return jsp;
   }

   
   /**
    * Constructs a DropDown box selecting between multiple strings
    * Sets selection based on property value and attaches a Listener
    * 
    * @param key property key as known in the params
    * @param vals array of strings, each one is a different option in the dropdown 
    * @return constructed JComboBox
    */
   public JComboBox makeDropDownBox(String key, String[] vals) {
      /**
       * Listener for the string-based dropdown boxes
       * Updates the model in the params class with any GUI changes
       */
      class StringBoxListener implements ActionListener {
         String key_;
         JComboBox box_;

         public StringBoxListener(String key, JComboBox box) {
            key_ = key;
            box_ = box;
         }

         public void actionPerformed(ActionEvent ae) {
            ASIdiSPIMFrame.props_.setPropValue(key_, (String) box_.getSelectedItem());
         }
      }

      JComboBox jcb = new JComboBox(vals);
      jcb.setSelectedItem(ASIdiSPIMFrame.props_.getPropValueString(key));
      jcb.addActionListener(new StringBoxListener(key, jcb));
      return jcb;
   }
   
}
