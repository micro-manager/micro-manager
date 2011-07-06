package org.micromanager.api;

import mmcorej.CMMCore;

import org.micromanager.acquisition.AcquisitionData;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PropertyItem;

public interface Autofocus {
   public void applySettings();
   public void saveSettings();
   //public void setMMCore(CMMCore core); // TODO: remove this one
   public void setApp(ScriptInterface app);
   public double fullFocus() throws MMException;
   public double incrementalFocus() throws MMException;
   public int getNumberOfImages();
   public AcquisitionData getFocusingSequence() throws MMException;
   public String getVerboseStatus();
   
   public PropertyItem[] getProperties();
   public String[] getPropertyNames();
   public PropertyItem getProperty(String key) throws MMException;
   public void setProperty(PropertyItem p) throws MMException;
   public String getPropertyValue(String name) throws MMException;
   public void setPropertyValue(String name, String value) throws MMException;

   public double getCurrentFocusScore();
   public String getDeviceName();
   public void enableContinuousFocus(boolean enable) throws MMException;
   public boolean isContinuousFocusEnabled() throws MMException;
   public boolean isContinuousFocusLocked() throws MMException;

   /**
    * OBSOLETE - do not use this method.
    */
   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) throws MMException;
   
}
