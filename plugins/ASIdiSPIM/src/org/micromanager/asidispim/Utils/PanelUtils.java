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

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Properties;

/**
 *
 * @author nico
 * @author Jon
 */
public class PanelUtils {
   
   
   /**
    * makes JSlider for double values where the values are multiplied by a scale factor
    * before internal representation as integer (as JSlider requires)
    * @return
    */
   public JSlider makeSlider(double min, double max, int scalefactor, Properties props, Devices devs,
         Devices.Keys devKey, Properties.Keys propKey,
         boolean restart, Properties.Keys restartKey, 
         Properties.Values restartValue, Properties.Values stopValue) {
      
      class sliderListener implements ChangeListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         JSlider js_;
         int scalefactor_;
         Properties props_;
         Devices.Keys devKey_;
         Properties.Keys propKey_;
         boolean restart_;
         Properties.Keys restartKey_;
         Properties.Values restartValue_;
         Properties.Values stopValue_;
         
         public sliderListener(JSlider js, int scalefactor, Properties props, 
                 Devices.Keys devKey, Properties.Keys propKey, boolean restart, 
                 Properties.Keys restartKey, Properties.Values restartValue,
                 Properties.Values stopValue) {
            js_ = js;
            scalefactor_ = scalefactor;
            props_ = props;
            devKey_ = devKey;
            propKey_ = propKey;
            restart_ = restart;
            restartKey_ = restartKey;
            restartValue_ = restartValue;
            stopValue_ = stopValue;
         }
         
         public void stateChanged(ChangeEvent ce) {
            if (!((JSlider)ce.getSource()).getValueIsAdjusting()) {  // only change when user releases
               restart_ = props_.getPropValueString(devKey_, restartKey_).equals
                       (restartValue_.toString());
               if (restart_) {
                  props_.setPropValue(devKey_, restartKey_, stopValue_);
               }
               props_.setPropValue(devKey_, propKey_, (float)js_.getValue()/(float)scalefactor_, true);
               if (restart_) {
                  props_.setPropValue(devKey_, restartKey_, restartValue_);
               }
            }
         }
         
         public void updateFromProperty() {
            js_.setValue((int)(scalefactor_*props_.getPropValueFloat(devKey_, propKey_, true)));
         }
         
         public void devicesChangedAlert() {
            // TODO refresh limits
            updateFromProperty();
         }
         
      }
      
      int intmin = (int)(min*scalefactor);
      int intmax = (int)(max*scalefactor);
      
      JSlider js = new JSlider(JSlider.HORIZONTAL, intmin, intmax, intmin);  // initialize with min value, will set to current value shortly 
      ChangeListener l = new sliderListener(js, scalefactor, props, devKey, 
              propKey, restart, restartKey, restartValue, stopValue);
      ((UpdateFromPropertyListenerInterface) l).updateFromProperty();  // set to value of property at present
      js.addChangeListener(l);
      devs.addListener((DevicesListenerInterface) l);
      props.addListener((UpdateFromPropertyListenerInterface) l);
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
    * @param label the GUI label
    * @param offValue the value of the property when not checked
    * @param onValue the value of the property when checked
    * @param props
    * @param devs
    * @param devKey
    * @param propKey
    * @return constructed JCheckBox
    */
   public JCheckBox makeCheckBox(String label, String offValue, String onValue, Properties props, Devices devs, Devices.Keys devKey, Properties.Keys propKey) {
      
      /**
       * nested inner class 
       * @author Jon
       */
      class checkBoxListener implements ItemListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         JCheckBox jc_;
         String offValue_;
         String onValue_;
         Properties props_;
         Devices.Keys devKey_;
         Properties.Keys propKey_;
         
         public checkBoxListener(JCheckBox jc, String offValue, String onValue, Properties props, Devices.Keys devKey, Properties.Keys propKey) {
            jc_ = jc;
            offValue_ = offValue;
            onValue_ = onValue;
            props_ = props;
            devKey_ = devKey;
            propKey_ = propKey;
         }
         
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               props_.setPropValue(devKey_, propKey_, onValue_, true);
            } else {
               props_.setPropValue(devKey_, propKey_, offValue_, true);
            }
         }
         
         public void updateFromProperty() {
            jc_.setSelected(props_.getPropValueString(devKey_, propKey_, true).equals(onValue_));
         }
         
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      JCheckBox jc = new JCheckBox(label);
      ItemListener l = new checkBoxListener(jc, offValue, onValue, props, devKey, propKey);
      jc.addItemListener(l);
      devs.addListener((DevicesListenerInterface) l);
      props.addListener((UpdateFromPropertyListenerInterface) l);
      ((UpdateFromPropertyListenerInterface) l).updateFromProperty();  // set to value of property at present
      return jc;
   }
   
   
   
   /**
    * Creates spinner for integers in the GUI
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI
    */
   public JSpinner makeSpinnerInteger(int min, int max, Properties props, Devices devs, Devices.Keys devKey, Properties.Keys propKey) {

      class SpinnerListenerInt implements ChangeListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         JSpinner sp_;
         Properties props_;
         Devices.Keys devKey_;
         Properties.Keys propKey_;
         
         public SpinnerListenerInt(JSpinner sp, Properties props, Devices.Keys devKey, Properties.Keys propKey) {
            sp_ = sp;
            props_ = props;
            devKey_ = devKey;
            propKey_ = propKey;
         }

         public void stateChanged(ChangeEvent ce) {
            props_.setPropValue(devKey_, propKey_, ((Integer)sp_.getValue()).intValue(), true);
         }

         public void updateFromProperty() {
            sp_.setValue(props_.getPropValueInteger(devKey_, propKey_, true));
         }
         
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
   // read the existing value from property and make sure it is within our min/max limits
      int origVal = props.getPropValueInteger(devKey, propKey, true);
      if (origVal < min) {
         origVal = min;
      }
      if (origVal > max) {
         origVal = max;
      }

      SpinnerModel jspm = new SpinnerNumberModel(origVal, min, max, 1);
      JSpinner jsp = new JSpinner(jspm);
      SpinnerListenerInt ispl = new SpinnerListenerInt(jsp, props, devKey, propKey);
      jsp.addChangeListener(ispl);
      devs.addListener(ispl);
      props.addListener(ispl);
      return jsp;
   }
   
   /**
    * Creates spinner for floats in the GUI
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI
    */
   public JSpinner makeSpinnerFloat(double min, double max, double step, Properties props, Devices devs, Devices.Keys devKey, Properties.Keys propKey) {
      // same as IntSpinnerListener except
      //  - cast to Float object in stateChanged()
      //  - getPropValueFloat in spimParamsChangedAlert()
      class SpinnerListenerFloat implements ChangeListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         JSpinner sp_;
         Properties props_;
         Devices.Keys devKey_;
         Properties.Keys propKey_;

         public SpinnerListenerFloat(JSpinner sp, Properties props, Devices.Keys devKey, Properties.Keys propKey) {
            sp_ = sp;
            props_ = props;
            devKey_ = devKey;
            propKey_ = propKey;
         }

         public void stateChanged(ChangeEvent ce) {
            // TODO figure out why the type of value in the numbermodel is changing type to float which necessitates this code
            float f;
            try {
               f = (float)((Double)sp_.getValue()).doubleValue();
            } catch (Exception ex) {
               f = ((Float)sp_.getValue()).floatValue();
            }
            props_.setPropValue(devKey_, propKey_, f, true);
         }

         public void updateFromProperty() {
            sp_.setValue(props_.getPropValueFloat(devKey_, propKey_, true));
         }
         
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      // read the existing value from property and make sure it is within our min/max limits
      double origVal = (double)props.getPropValueFloat(devKey, propKey, true);
      if (origVal < min) {
         origVal = min;
      }
      if (origVal > max) {
         origVal = max;
      }
      
      SpinnerModel jspm = new SpinnerNumberModel(origVal, min, max, step);
      JSpinner jsp = new JSpinner(jspm);
      SpinnerListenerFloat ispl = new SpinnerListenerFloat(jsp, props, devKey, propKey);
      jsp.addChangeListener(ispl);
      devs.addListener(ispl);
      props.addListener(ispl);
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
   public JComboBox makeDropDownBox(String[] vals, Properties props, Devices devs, Devices.Keys devKey, Properties.Keys propKey) {
      /**
       * Listener for the string-based dropdown boxes
       * Updates the model in the params class with any GUI changes
       */
      class StringBoxListener implements ActionListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         JComboBox box_;
         Properties props_;
         Devices.Keys devKey_;
         Properties.Keys propKey_;

         public StringBoxListener(JComboBox box, Properties props, Devices.Keys devKey, Properties.Keys propKey) {
            box_ = box;
            props_ = props;
            devKey_ = devKey;
            propKey_ = propKey;
         }

         public void actionPerformed(ActionEvent ae) {
            props_.setPropValue(devKey_, propKey_, (String) box_.getSelectedItem(), true);
         }
         
         public void updateFromProperty() {
            box_.setSelectedItem(props_.getPropValueString(devKey_, propKey_, true));
         }
         
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }

      JComboBox jcb = new JComboBox(vals);
      jcb.setSelectedItem(props.getPropValueString(devKey, propKey, true));
      StringBoxListener l = new StringBoxListener(jcb, props, devKey, propKey);
      jcb.addActionListener(l);
      devs.addListener(l);
      props.addListener(l);
      return jcb;
   }
   
}
