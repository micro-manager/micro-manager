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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Joystick;
import org.micromanager.asidispim.Data.Positions;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;

/**
 *
 * @author nico
 * @author Jon
 */
public class PanelUtils {
   private final Prefs prefs_;
   private final Properties props_;
   private final Devices devices_;
   
   public PanelUtils(Prefs prefs, Properties props, Devices devices) {
      prefs_ = prefs;
      props_ = props;
      devices_ = devices;
   }
   
   /**
    * makes JSlider for double values where the values are multiplied by a scale factor
    * before internal representation as integer (as JSlider requires)
    * @param min
    * @param max
    * @param scalefactor
    * @param devKey
    * @param propKey
    * @return
    */
   public JSlider makeSlider(double min, double max, final int scalefactor,
         Devices.Keys devKey, Properties.Keys propKey) {
      
      class sliderListener implements ChangeListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         private final JSlider js_;
         private final int scalefactor_;
         private final Devices.Keys devKey_;
         private final Properties.Keys propKey_;
         
         public sliderListener(JSlider js, int scalefactor,
                 Devices.Keys devKey, Properties.Keys propKey) {
            js_ = js;
            scalefactor_ = scalefactor;
            devKey_ = devKey;
            propKey_ = propKey;
         }
         
         @Override
         public void stateChanged(ChangeEvent ce) {
            if (!((JSlider)ce.getSource()).getValueIsAdjusting()) {  // only change when user releases
               props_.setPropValue(devKey_, propKey_, (float)js_.getValue()/(float)scalefactor_, true);
            }
         }
         
         @Override
         public void updateFromProperty() {
            js_.setValue((int)(scalefactor_*props_.getPropValueFloat(devKey_, propKey_)));
         }
         
         @Override
         public void devicesChangedAlert() {
            // TODO refresh limits
            updateFromProperty();
         }
                
      }
      
      int intmin = (int)(min*scalefactor);
      int intmax = (int)(max*scalefactor);
      
      JSlider js = new JSlider(intmin, intmax, intmin);  // initialize with min value, will set to current value shortly 
      ChangeListener l = new sliderListener(js, scalefactor, devKey, propKey);
      ((UpdateFromPropertyListenerInterface) l).updateFromProperty();  // set to value of property at present
      js.addChangeListener(l);
      devices_.addListener((DevicesListenerInterface) l);
      props_.addListener((UpdateFromPropertyListenerInterface) l);

      js.setMajorTickSpacing(intmax-intmin);
      js.setMinorTickSpacing(scalefactor);
      
      // resolve issue where slider is too long/wide
      // half preferred width, then use growx in MigLayout to stretch again 
      Dimension size = js.getPreferredSize();
      size.width = size.width/2;
      js.setPreferredSize(size);
      
      //Create the label table
      Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
      labelTable.put(intmax, new JLabel(Double.toString(max)) );
      labelTable.put(intmin, new JLabel(Double.toString(min)) );
      
      js.setLabelTable( labelTable );
      js.setPaintTicks(true);
      js.setPaintLabels(true);
      
      return js;
   }
   
   /**
    * Creates spinner for integers in the GUI.
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI.
    * @param min - minimum value for the spinner
    * @param max - maximum value for the spinner
    * @param devKeys - array of device keys, use inline constructor "new Devices.Keys[]{list of devices}"
    * @param propKey - property key for this spinner
    * @param defaultVal - initial value
    * @return the created JSpinner
    */
   public JSpinner makeSpinnerInteger(int min, int max, 
         Devices.Keys [] devKeys, Properties.Keys propKey, int defaultVal) {

      class SpinnerListenerInt implements ChangeListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         private final JSpinner sp_;
         private final Devices.Keys [] devKeys_;
         private final Properties.Keys propKey_;
         
         public SpinnerListenerInt(JSpinner sp, 
               Devices.Keys [] devKeys, Properties.Keys propKey) {
            sp_ = sp;
            devKeys_ = devKeys;
            propKey_ = propKey;
         }
         
         private int getSpinnerValue() {
            return ((Integer)sp_.getValue());
         }

         @Override
         public void stateChanged(ChangeEvent ce) {
            int spinnerValue = getSpinnerValue();
            for (Devices.Keys devKey : devKeys_) {
               // property reads (core calls) are inexpensive compared to 
               //   property writes (serial comm) so only write if needed
               // however, this doesn't solve problem of properties that are really
               //    card-specific (not axis-specific) because other devices on same
               //    card may have been changed but not refreshed in micro-Manager
               if (props_.getPropValueInteger(devKey, propKey_) != spinnerValue) {
                  props_.setPropValue(devKey, propKey_, spinnerValue, true);
                  // ignore error for sake of missing device assignment
               }
            }
         }

         @Override
         public void updateFromProperty() {
            sp_.setValue(props_.getPropValueInteger(devKeys_[0], propKey_));
            stateChanged(new ChangeEvent(sp_));  // fire manually to set all the devices is devKeys
         }
         
         @Override
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      // read the existing value of 1st device and make sure it is within our min/max limits
      int origVal = props_.getPropValueInteger(devKeys[0], propKey);
      if (origVal == 0) {
      // if getPropValue returned 0 (sign no value existed) then use default
         origVal = defaultVal;
         props_.setPropValue(devKeys[0], propKey, defaultVal, true);
      }
      if (origVal < min) {
         origVal = min;
         props_.setPropValue(devKeys[0], propKey, min, true);
         // ignore error for sake of missing device assignment
      }
      if (origVal > max) {
         origVal = max;
         props_.setPropValue(devKeys[0], propKey, max, true);
         // ignore error for sake of missing device assignment
      }

      SpinnerModel jspm = new SpinnerNumberModel(origVal, min, max, 1);
      JSpinner jsp = new JSpinner(jspm);
      SpinnerListenerInt ispl = new SpinnerListenerInt(jsp, devKeys, propKey);
      jsp.addChangeListener(ispl);
      devices_.addListener(ispl);
      props_.addListener(ispl);
      JComponent editor = jsp.getEditor();
      JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
      tf.setColumns(4);
      return jsp;
   }
   
   /**
    * Creates spinner for integers in the GUI.
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI.
    * @param min - minimum value for the spinner
    * @param max - maximum value for the spinner
    * @param devKey - device keys
    * @param propKey - property key for this spinner
    * @param defaultVal - initial value
    * @return the created JSpinner
    */
   public JSpinner makeSpinnerInteger(int min, int max, 
         Devices.Keys devKey, Properties.Keys propKey, int defaultVal) {
      return makeSpinnerInteger(min, max, new Devices.Keys[]{devKey}, propKey, defaultVal);
   }
   
   /**
    * Utility function to access the spinner value.
    * @param sp
    * @return
    */
   public static float getSpinnerFloatValue(JSpinner sp) {
      // TODO figure out why the type of value in the numbermodel is 
      // changing type to float which necessitates this code
      float f;
      try {
         f = (float) ((Double) sp.getValue()).doubleValue();
      } catch (Exception ex) {
         f = ((Float) sp.getValue()).floatValue();
      }
      return f;
   }
   
   /**
    * Utility function to write to the spinner value.
    * @param sp - spinning that should be set
    * @param f - new value for the spinner
    */
   public static void setSpinnerFloatValue(JSpinner sp, float f) {
      // TODO figure out why the type of value in the numbermodel is 
      // changing type to float which necessitates this code
      // this should call change listener
      try {
         sp.setValue((Double)((double)f));
      } catch (Exception ex) {
         sp.setValue((Float)f);
      }
   }
   
   /**
    * Utility function to write to the spinner value.
    * @param sp spinner whole value should be set
    * @param d new value for the spinner
    */
   public static void setSpinnerFloatValue(JSpinner sp, double d) {
      // TODO figure out why the type of value in the numbermodel is 
      // changing type to float which necessitates this code
      try {
         sp.setValue((Double)d);
      } catch (Exception ex) {
         sp.setValue((Float)((float)d));
      }
   }
   
   /**
    * Creates spinner for floats in the GUI.
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI.
    * @param min - minimum value for the spinner
    * @param max - maximum value for the spinner
    * @param step - stepsize for the spinner
    * @param devKeys - array of device keys, use inline constructor "new Devices.Keys[]{list of devices}"
    * @param propKey - property key for this spinner
    * @param defaultVal - 
    * @return the created JSpinner
    */
   public JSpinner makeSpinnerFloat(double min, double max, double step,
         Devices.Keys [] devKeys, Properties.Keys propKey, double defaultVal) {
      // same as IntSpinnerListener except
      //  - different getSpinnerValue() implementation
      //  - getPropValueFloat in stateChanged()
      class SpinnerListenerFloat implements ChangeListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         private final JSpinner sp_;
         private final Devices.Keys [] devKeys_;
         private final Properties.Keys propKey_;

         public SpinnerListenerFloat(JSpinner sp, Devices.Keys [] devKeys, 
               Properties.Keys propKey) {
            sp_ = sp;
            devKeys_ = devKeys;
            propKey_ = propKey;
         }
                
         @Override
         public void stateChanged(ChangeEvent ce) {
            float spinnerValue = getSpinnerFloatValue(sp_);
            for (Devices.Keys devKey : devKeys_) {
               // property reads (core calls) are inexpensive compared to 
               //   property writes (serial comm) so only write if needed
               // however, this doesn't solve problem of properties that are really
               //    for the card (not for the axis) because other devices on same
               //    card may have been changed but not refreshed in micro-Manager
               if (!MyNumberUtils.floatsEqual(props_.getPropValueFloat(devKey, propKey_), spinnerValue)) {
                  props_.setPropValue(devKey, propKey_, spinnerValue, true);
               // ignore error for sake of missing device assignment
               }
            }
         }

         @Override
         public void updateFromProperty() {
            sp_.setValue(props_.getPropValueFloat(devKeys_[0], propKey_));
            stateChanged(new ChangeEvent(sp_));  // fire manually to set all the devices is devKeys
         }
         
         @Override
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      // read the existing value of 1st device and make sure it is within our min/max limits
      double origVal = (double)props_.getPropValueFloat(devKeys[0], propKey);
      if (MyNumberUtils.floatsEqual((float) origVal, (float) 0)) {
         // if getPropValue returned 0 (sign no value existed) then use default
         origVal = defaultVal;
         props_.setPropValue(devKeys[0], propKey, (float)defaultVal, true);
      }
      if (origVal < min) {
         origVal = min;
         props_.setPropValue(devKeys[0], propKey, (float)min, true);
         // ignore error for sake of missing device assignment
      }
      if (origVal > max) {
         origVal = max;
         props_.setPropValue(devKeys[0], propKey, (float)max, true);
         // ignore error for sake of missing device assignment
      }
      
      // all devices' properties will be set on tab's gotSelected which calls updateFromProperty
      
      SpinnerModel jspm = new SpinnerNumberModel(origVal, min, max, step);
      JSpinner jsp = new JSpinner(jspm);
      SpinnerListenerFloat ispl = new SpinnerListenerFloat(jsp, devKeys, propKey);
      jsp.addChangeListener(ispl);
      devices_.addListener(ispl);
      props_.addListener(ispl);
      JComponent editor = jsp.getEditor();
      JFormattedTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
      tf.setColumns(4);
      return jsp;
   }
   
   /**
    * Creates spinner for floats in the GUI.
    * Implements UpdateFromPropertyListenerInterface, causing updates in the model
    * that were generated by changes in the device to be propagated back to the UI.
    * @param min - minimum value for the spinner
    * @param max - maximum value for the spinner
    * @param step - stepsize for the spinner
    * @param devKey - device key
    * @param propKey - property key for this spinner
    * @param defaultVal - 
    * @return the created JSpinner
    */
   public JSpinner makeSpinnerFloat(double min, double max, double step,
         Devices.Keys devKey, Properties.Keys propKey, double defaultVal) {
      return makeSpinnerFloat(min, max, step, new Devices.Keys[]{devKey}, propKey, defaultVal);
   }

   /**
    * Constructs a DropDown box selecting between multiple strings.
    * Sets selection based on property value and attaches a Listener.
    * 
    * @param vals - array of strings, each one is a different option in the dropdown 
    * @param devKeys 
    * @param propKey - property key as known in the params
    * @param defaultVal - if no value exists in properties/preferences then this is added
    * @return constructed JComboBox
    */
   public JComboBox makeDropDownBox(String[] vals,
         Devices.Keys [] devKeys, Properties.Keys propKey,
         String defaultVal) {
      /**
       * Listener for the string-based dropdown boxes
       * Updates the model in the params class with any GUI changes
       */
      class StringBoxListener implements ActionListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         private final JComboBox box_;
         private final Devices.Keys [] devKeys_;
         private final Properties.Keys propKey_;

         public StringBoxListener(JComboBox box, Devices.Keys [] devKeys, Properties.Keys propKey) {
            box_ = box;
            devKeys_ = devKeys;
            propKey_ = propKey;
         }
         
         private String getBoxValue() {
            return (String) box_.getSelectedItem();
         }

         @Override
         public void actionPerformed(ActionEvent ae) {
            // unlike analogous int/float functions, this handler is called on any setSelectedItem 
            String boxValue = getBoxValue();
            if (boxValue == null) {
               boxValue = "";
            }
            for (Devices.Keys devKey : devKeys_) {
               // property reads (core calls) are inexpensive compared to 
               //   property writes (serial comm) so only write if needed
               // however, this doesn't solve problem of properties that are really
               //    for the card (not for the axis) because other devices on same
               //    card may have been changed but not refreshed in Micro-Manager
               if (!props_.getPropValueString(devKey, propKey_).equals(boxValue)) {
                  props_.setPropValue(devKey, propKey_, boxValue, true);
               }
            }
         }
         
         @Override
         public void updateFromProperty() {
            box_.setSelectedItem(props_.getPropValueString(devKeys_[0], propKey_));
         }
         
         @Override
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      if (!props_.hasProperty(devKeys[0], propKey)) {
         props_.setPropValue(devKeys[0], propKey, defaultVal);
      }
      
      String origVal = props_.getPropValueString(devKeys[0], propKey);
      JComboBox jcb = new JComboBox(vals);
      jcb.setSelectedItem(origVal);
      StringBoxListener l = new StringBoxListener(jcb, devKeys, propKey);
      jcb.addActionListener(l);
      devices_.addListener(l);
      props_.addListener(l);
      return jcb;
   }
   
   /**
    * Constructs a DropDown box selecting between multiple strings.
    * Sets selection based on property value and attaches a Listener.
    * 
    * @param propKey - property key as known in the params
    * @param defaultVal
    * @param vals - array of strings, each one is a different option in the dropdown 
    * @param devKey
    * @return constructed JComboBox
    */
   public JComboBox makeDropDownBox(String[] vals,
         Devices.Keys devKey, Properties.Keys propKey,
         String defaultVal) {
      return makeDropDownBox(vals, new Devices.Keys[]{devKey}, propKey, defaultVal);
   }
   
   
   /**
    * Constructs JCheckBox with boolean preference store.
    * The boolean value is read using prefs_.getBoolean(), NOT props_.get...
    * Stored in the prefNode indicated, usually the panel name.
    * @param label the GUI label
    * @param propKey (needed for read/write to preferences now that preferences is based on properties)
    * @param prefNode 
    * @param defaultValue
    * @return constructed JCheckBox
    */
   public JCheckBox makeCheckBox(String label, Properties.Keys propKey,
         String prefNode, boolean defaultValue) {
      
      /**
       * nested inner class 
       * @author Jon
       */
      class checkBoxListener implements ItemListener {
         final JCheckBox jc_;
         final Properties.Keys propKey_;
         final String prefNode_;
         
         public checkBoxListener(JCheckBox jc, Properties.Keys propKey,
               String prefNode) {
            jc_ = jc;
            propKey_ = propKey;
            prefNode_ = prefNode;
         }

         @Override
         public void itemStateChanged(ItemEvent e) {
            prefs_.putBoolean(prefNode_, propKey_, jc_.isSelected());
         }
         
      }
      
      final JCheckBox jc = new JCheckBox(label, 
            prefs_.getBoolean(prefNode, propKey, defaultValue));
      jc.addItemListener(new checkBoxListener(jc, propKey, prefNode));
      return jc;
   }

   
   /**
    * Constructs JCheckBox tied to "usual" property and also stored in preferences.
    * Uses a separate "prefKey" so that the same property name with different devices
    * can be distinguished.  Specify property values for checked/unchecked state.
    * @param label the GUI label
    * @param offValue the value of the property when not checked
    * @param onValue the value of the property when checked
    * @param devKey
    * @param propKey
    * @param prefNode
    * @param prefKey
    * @return constructed JCheckBox
    */
   public JCheckBox makeCheckBox(String label,
         Properties.Values offValue, Properties.Values onValue,
         Devices.Keys devKey, Properties.Keys propKey,
         String prefNode, Prefs.Keys prefKey) {
      
      /**
       * nested inner class 
       * @author Jon
       */
      class checkBoxListener implements ItemListener, UpdateFromPropertyListenerInterface, DevicesListenerInterface {
         private final JCheckBox jc_;
         private final Properties.Values offValue_;
         private final Properties.Values onValue_;
         private final Devices.Keys devKey_;
         private final Properties.Keys propKey_;
         private final String prefNode_;
         private final Prefs.Keys prefKey_;
         
         public checkBoxListener(JCheckBox jc, 
               Properties.Values offValue, Properties.Values onValue,
               Devices.Keys devKey, Properties.Keys propKey,
               String prefNode, Prefs.Keys prefKey) {
            jc_ = jc;
            offValue_ = offValue;
            onValue_ = onValue;
            devKey_ = devKey;
            propKey_ = propKey;
            prefNode_ = prefNode;
            prefKey_ = prefKey;
         }
         
         // only called when the user selects/deselects from GUI or the value _changes_ programmatically
         @Override
         public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
               props_.setPropValue(devKey_, propKey_, onValue_, true);
               prefs_.putBoolean(prefNode_, prefKey_, true);
            } else {
               props_.setPropValue(devKey_, propKey_, offValue_, true);
               prefs_.putBoolean(prefNode_, prefKey_, false);
            }
         }
         
         @Override
         public void updateFromProperty() {
            jc_.setSelected(props_.getPropValueString(devKey_, propKey_).equals(onValue_.toString()));
         }
         
         @Override
         public void devicesChangedAlert() {
            updateFromProperty();
         }
      }
      
      JCheckBox jc = new JCheckBox(label, 
            prefs_.getBoolean(prefNode, prefKey, false));
      checkBoxListener l = new checkBoxListener(jc, offValue, onValue, devKey, propKey, prefNode, prefKey);
      jc.addItemListener(l);
      devices_.addListener(l);
      return jc;
   }
   
   
  /**
   * Creates formatted text field for user to enter decimal (double) values.
   * @param prefNode - String identifying preference node where this variable 
   *                    be store such that its value can be retrieved on restart
   * @param prefKey - String used to identify this preference
   * @param defaultValue - initial (default) value.  Will be overwritten by
   *                       value in Preferences
   * @param numColumns - width of the GUI element
   * @return - JFormattedTextField element
   */
   public JFormattedTextField makeFloatEntryField(String prefNode, String prefKey, 
           double defaultValue, int numColumns) {
      
      class FieldListener implements PropertyChangeListener {
         private final JFormattedTextField tf_;
         private final String prefNode_;
         private final String prefKey_;

         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            try {
               prefs_.putFloat(prefNode_, prefKey_, ((Double)tf_.getValue()).floatValue());
            } catch (Exception e) {
               MyDialogUtils.showError(e);
            }
         }
         
         public FieldListener(JFormattedTextField tf, String prefNode, String prefKey) {
            prefNode_ = prefNode;
            prefKey_ = prefKey;
            tf_ = tf;
         }
      }
      
      JFormattedTextField tf = new JFormattedTextField();
      tf.setValue((double) prefs_.getFloat(prefNode, prefKey, (float)defaultValue));
      tf.setColumns(numColumns);
      PropertyChangeListener listener = new FieldListener(tf, prefNode, prefKey);
      tf.addPropertyChangeListener("value", listener);
      return tf;
   }
   
   
   /**
    * Creates formatted text field for user to enter integer values.
    * @param prefNode - String identifying preference node where this variable 
    *                    be store such that its value can be retrieved on restart
    * @param prefKey - String used to identify this preference
    * @param defaultValue - initial (default) value.  Will be overwritten by
    *                       value in Preferences
    * @param numColumns - width of the GUI element
    * @return - JFormattedTextField element
    */
    public JFormattedTextField makeIntEntryField(String prefNode, String prefKey, 
            int defaultValue, int numColumns) {
       
       class FieldListener implements PropertyChangeListener {
          private final JFormattedTextField tf_;
          private final String prefNode_;
          private final String prefKey_;

          @Override
          public void propertyChange(PropertyChangeEvent evt) {
             try {
                prefs_.putInt(prefNode_, prefKey_, ((Integer)tf_.getValue()).intValue());
             } catch (Exception e) {
                MyDialogUtils.showError(e);
             }
          }
          
          public FieldListener(JFormattedTextField tf, String prefNode, String prefKey) {
             prefNode_ = prefNode;
             prefKey_ = prefKey;
             tf_ = tf;
          }
       }
       
       JFormattedTextField tf = new JFormattedTextField();
       tf.setValue( prefs_.getInt(prefNode, prefKey, defaultValue) );
       tf.setColumns(numColumns);
       PropertyChangeListener listener = new FieldListener(tf, prefNode, prefKey);
       tf.addPropertyChangeListener("value", listener);
       return tf;
    }
   
   
   /**
    * Creates field for user to type in new position for an axis, with default value of 0.
    * @param key
    * @param dir
    * @param positions
    * @return
    */
   public JFormattedTextField makeSetPositionField(Devices.Keys key, Joystick.Directions dir, Positions positions) {

      class setPositionListener implements PropertyChangeListener { 
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;
         private final Positions positions_;

         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            try {
               positions_.setPosition(key_, dir_, ((Number)evt.getNewValue()).doubleValue());
            } catch (Exception e) {
               MyDialogUtils.showError(e);
            }
         }

         setPositionListener(Devices.Keys key, Joystick.Directions dir, Positions positions) {
            key_ = key;
            dir_ = dir;
            positions_ = positions;
         }
      }
      
      // this is an attempt to allow enter presses to register as "changes" too
      // useful when the desired value is already in the field but the position is elsewhere
      // now enter works but only if the field has been edited, for instance if 0 is there you
      // can delete the 0 and then retype it and enter will register as an action and set the position
      class setPositionListener2 implements ActionListener {
         private final Devices.Keys key_;
         private final Joystick.Directions dir_;
         private final Positions positions_;
         private final JFormattedTextField tf_;

         @Override
         public void actionPerformed(ActionEvent evt) {
            try {
               positions_.setPosition(key_, dir_, ((Number)tf_.getValue()).doubleValue());
            } catch (Exception e) {
               MyDialogUtils.showError(e);
            }
         }
         
         setPositionListener2(Devices.Keys key, Joystick.Directions dir, Positions positions, JFormattedTextField tf) {
            key_ = key;
            dir_ = dir;
            positions_ = positions;
            tf_ = tf;
         }
      }

      JFormattedTextField tf = new JFormattedTextField();
      
      tf.setValue(0.0);
      tf.setColumns(4);
      PropertyChangeListener pc = new setPositionListener(key, dir, positions);
      ActionListener al = new setPositionListener2(key, dir, positions, tf);
      tf.addPropertyChangeListener("value", pc);
      tf.addActionListener(al);
      return tf;
   }
  
   
   /**
    * takes a JSpinner and adds a listener that is guaranteed to be called 
    * after the other listeners.
    * Modifies the JSpinner!!
    * @param js - spinner to which listener will be added
    * @param lastListener - listener that will be added at the end
    */
   public void addListenerLast(JSpinner js, final ChangeListener lastListener) {
      final ChangeListener [] origListeners = js.getChangeListeners();
      for (ChangeListener list : origListeners) {
         js.removeChangeListener(list);
      }

      ChangeListener newListener = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            for (ChangeListener list : origListeners) {
               list.stateChanged(e);
            }
            lastListener.stateChanged(e);
         }
      };
      js.addChangeListener(newListener);
   }
   
   /**
    * takes a JComboBox and adds a listener that is guaranteed to be called 
    * after the other listeners.
    * Modifies the JComboBox!!
    * @param jcb - combo box to which listener will be added
    * @param lastListener - listener that will be added at the end
    */
   public void addListenerLast(JComboBox jcb, final ActionListener lastListener) {
      final ActionListener [] origListeners = jcb.getActionListeners();
      for (ActionListener list : origListeners) {
         jcb.removeActionListener(list);
      }

      ActionListener newListener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            for (ActionListener list : origListeners) {
               list.actionPerformed(e);
            }
            lastListener.actionPerformed(e);
         }
      };
      jcb.addActionListener(newListener);
   }
   
   /**
    * takes a JSlider and adds a listener that is guaranteed to be called 
    * after the other listeners.
    * Modifies the JSlider!!
    * @param jcb - combo box to which listener will be added
    * @param lastListener - listener that will be added at the end
    */
   public void addListenerLast(JSlider jsl, final ChangeListener lastListener) {
      final ChangeListener [] origListeners = jsl.getChangeListeners();
      for (ChangeListener list : origListeners) {
         jsl.removeChangeListener(list);
      }

      ChangeListener newListener = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            for (ChangeListener list : origListeners) {
               list.stateChanged(e);
            }
            lastListener.stateChanged(e);
         }
      };
      jsl.addChangeListener(newListener);
   }
   
   /**
    * creates change listener for float-based spinner that will coerce the value
    * to quarter integers (e.g. 1.00, 1.25, 1.50, 1.75, 2.00, etc.)
    * @param sp
    * @return
    */
   public static ChangeListener coerceToQuarterIntegers(final JSpinner sp) {
      return new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent ce) {
            // make sure is multiple of 0.25
            float userVal = PanelUtils.getSpinnerFloatValue(sp);
            float nearestValid = MyNumberUtils.roundToQuarterMs(userVal);
            if (!MyNumberUtils.floatsEqual(userVal, nearestValid)) {
               PanelUtils.setSpinnerFloatValue(sp, nearestValid);
            }
         }
      };
   }
   
   /**
    * makes border with centered title text
    * @param title
    * @return
    */
   public static TitledBorder makeTitledBorder(String title) {
      TitledBorder myBorder = BorderFactory.createTitledBorder(
              BorderFactory.createLineBorder(ASIdiSPIM.borderColor), " " + title + " ");
      myBorder.setTitleJustification(TitledBorder.CENTER);
      return myBorder;
   }
   
//   /**
//    * Recursively get list of all the components inside a container
//    * credit http://stackoverflow.com/questions/6495769/how-to-get-all-elements-inside-a-jframe
//    * @param c
//    * @return
//    */
//   private static List<Component> getAllComponents(final Container c) {
//      Component[] comps = c.getComponents();
//      List<Component> compList = new ArrayList<Component>();
//      for (Component comp : comps) {
//          compList.add(comp);
//          if (comp instanceof Container)
//              compList.addAll(getAllComponents((Container) comp));
//      }
//      return compList;
//  }
//   
//   /**
//   * call setEnabled(boolean) recursively on all components in frame/panel
//    * @param panel
//    * @param enabled
//    */
//   public static void componentsSetEnabledRecursive(Container container, boolean enabled) {
//      List<Component> compList = new ArrayList<Component>();
//      compList = getAllComponents(container);
//      for (Component comp : compList) {
//         comp.setEnabled(enabled);
//      }
//   }
//   
//   /**
//   * call setEnabled(boolean) recursively on all components in list of frame/panel
//    * @param panel
//    * @param enabled
//    */
//   public static void componentsSetEnabledRecursive(Container[] containers, boolean enabled) {
//      for (Container cont : containers) {
//         componentsSetEnabledRecursive(cont, enabled);
//      }
//   }
   
   /**
   * call setEnabled(boolean) on all top-level components in frame/panel
    * @param panel
    * @param enabled
    */
   public static void componentsSetEnabled(Container container, boolean enabled) {
      for (Component comp : container.getComponents()) {
         comp.setEnabled(enabled);
      }
   }
   
   /**
   * call setEnabled(boolean) on all top-level components in list of frame/panel
    * @param panel
    * @param enabled
    */
   public static void componentsSetEnabled(Container[] containers, boolean enabled) {
      for (Container cont : containers) {
         componentsSetEnabled(cont, enabled);
      }
   }
   
   /**
    * call setEnabled(boolean) on all components in list
    * @param components
    * @param enabled
    */
   public static void componentsSetEnabled(JComponent[] components, boolean enabled) {
      for (JComponent c : components) {
         c.setEnabled(enabled);
      }
   }
}
