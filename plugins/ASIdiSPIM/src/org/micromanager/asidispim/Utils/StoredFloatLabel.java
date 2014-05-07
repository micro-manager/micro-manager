
package org.micromanager.asidispim.Utils;

import javax.swing.JLabel;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.utils.NumberUtils;


  /**
    * Utility class that ties a JLabel to a value stored in the preferences
    * @author Nico
    */
   public class StoredFloatLabel extends JLabel {
      private final String prefNode_;
      private final String prefKey_;
      private final Prefs prefs_;
      private final ScriptInterface gui_;
      
      /**
       * Creates a JLabel and overrider the setText methods so that 
       * changed in the Label will be written to the preferences
       * Adds a setFloat method for convenience
       * 
       * @param prefNode - Node used to store the value in preferences
       * @param prefKey - Key used to store the value in preferences
       * @param defaultValue - default value in case nothing is found in prefs
       */
      public StoredFloatLabel(String prefNode, String prefKey, float defaultValue, 
              Prefs prefs, ScriptInterface gui) {               
          super();
          prefNode_ = prefNode;
          prefKey_ = prefKey;
          prefs_ = prefs;
          gui_ = gui;
          super.setText("" + (prefs_.getFloat(prefNode, prefKey, (float)defaultValue)));
      }
      
      @Override
      public void setText(String txt) {
         super.setText(txt);
         if (prefNode_ != null && prefKey_ != null) {
            try {
               prefs_.putFloat(prefNode_, prefKey_,
                       ((Double) NumberUtils.displayStringToDouble(txt)).floatValue());
            } catch (Exception e) {
               gui_.showError(e);
            }
         }
      }
      
      public void setFloat(float val) {
         super.setText(NumberUtils.doubleToDisplayString(val));
         prefs_.putFloat(prefNode_, prefKey_, val);
      }
   }
     