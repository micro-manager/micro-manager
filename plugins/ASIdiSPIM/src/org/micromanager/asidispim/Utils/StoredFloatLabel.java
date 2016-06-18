///////////////////////////////////////////////////////////////////////////////
//FILE:          StoredFloatLabel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2014
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

import java.text.ParseException;

import javax.swing.JLabel;

import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.utils.NumberUtils;


/**
 * Utility class that ties a JLabel to a value stored in the preferences
 * @author Nico
 */
@SuppressWarnings("serial")
public class StoredFloatLabel extends JLabel {
   private final String prefNode_;
   private final String prefKey_;
   private final Prefs prefs_;
   private final String units_;

   /**
    * Creates a JLabel and overrides the setText methods so that 
    * changed in the Label will be written to the preferences
    * Adds a setFloat method for convenience
    * TODO consider storing float value instead of string.
    * TODO specify number of decimal points to display/store => could store as fixed-point value
    * 
    * @param prefNode - Node used to store the value in preferences
    * @param prefKey - Key used to store the value in preferences
    * @param defaultValue - default value in case nothing is found in prefs
    * @param prefs - Global preferences object used in this plugin
    * @param gui - MM ScriptInterface instance
    * @param units - string to be displayed after the float, usually containing units
    */
   public StoredFloatLabel(String prefNode, String prefKey, float defaultValue, 
         Prefs prefs, String units) {
      super();
      prefNode_ = prefNode;
      prefKey_ = prefKey;
      prefs_ = prefs;
      units_ = units;
      super.setText("" + (prefs_.getFloat(prefNode, prefKey, defaultValue)) + units);
   }

   /**
    * Sets the text of the JLabel and stores the value in Preferences
    * @param txt 
    */
   @Override
   public void setText(String txt) {
      super.setText(txt);
      if (prefNode_ != null && prefKey_ != null) {
         try {
            prefs_.putFloat(prefNode_, prefKey_,
                  ((Double) NumberUtils.displayStringToDouble(txt)).floatValue());
         } catch (ParseException e) {
            MyDialogUtils.showError(e);
         }
      }
   }

   /**
    * Convenience method to display a float and store value in prefs 
    * @param val 
    */
   public void setFloat(float val) {
      super.setText(NumberUtils.doubleToDisplayString(val) + units_);
      if (prefNode_ != null && prefKey_ != null) {
         prefs_.putFloat(prefNode_, prefKey_, val);
      }
   }

   /**
    * @return - displayed value as a float
    */
   public float getFloat() {
      String txt = getText();
      float result = 0;
      try {
         result = (float) NumberUtils.displayStringToDouble(txt);
      } catch (ParseException pe) {
         // do nothing
      }
      return result;
   }
   
}
