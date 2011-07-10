package org.micromanager.utils;

import java.util.Vector;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import org.micromanager.api.Autofocus;
import org.micromanager.api.ScriptInterface;
import org.micromanager.acquisition.AcquisitionData;

public class CoreAutofocus implements Autofocus {

   private CMMCore core_;
   private String devName_;
   
   public CoreAutofocus() {
   }

   public void focus(double coarseStep, int numCoarse, double fineStep,
         int numFine) throws MMException {
      throw new MMException(
            "Obsolete command. Use setProperty() to specify parameters.");
   }

   public double fullFocus() throws MMException {
      if (core_ == null)
         return 0.0;

      try {
         core_.setAutoFocusDevice(devName_);
         core_.fullFocus();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }

      try {
         return core_.getLastFocusScore();
      } catch (Exception e) {
         ReportingUtils.showError(e);
         return 0.0;
      }
   }

   public String getVerboseStatus() {
      return new String("No message at this time!");
   }

   public double incrementalFocus() throws MMException {
      if (core_ == null)
         return 0.0;

      try {
         core_.setAutoFocusDevice(devName_);
         core_.incrementalFocus();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }

      try {
         return core_.getLastFocusScore();
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return 0.0;
      }

   }

   public String[] getPropertyNames(){
      Vector<String> propNames = new Vector<String>();
      try {
         core_.setAutoFocusDevice(devName_);
         StrVector propNamesVect = core_.getDevicePropertyNames(devName_);
         for (int i = 0; i < propNamesVect.size(); i++)
            if (!core_.isPropertyReadOnly(devName_, propNamesVect.get(i))
                  && !core_.isPropertyPreInit(devName_,
                        propNamesVect.get(i)))
               propNames.add(propNamesVect.get(i));
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      return (String[]) propNames.toArray();
   }

   public PropertyItem[] getProperties() {
      StrVector propNamesVect;
      Vector<PropertyItem> props = new Vector<PropertyItem>();
      try {
         core_.setAutoFocusDevice(devName_);
         propNamesVect = core_.getDevicePropertyNames(devName_);
         for (int i = 0; i < propNamesVect.size(); i++) {
            PropertyItem p = new PropertyItem();
            p.device = devName_;
            p.name = propNamesVect.get(i);
            p.value = core_.getProperty(devName_, p.name);
            p.readOnly = core_.isPropertyReadOnly(devName_, p.name);
            if (core_.hasPropertyLimits(devName_, p.name)) {
               p.lowerLimit = core_.getPropertyLowerLimit(devName_, p.name);
               p.upperLimit = core_.getPropertyUpperLimit(devName_, p.name);
            }

            StrVector vals = core_.getAllowedPropertyValues(devName_, p.name);
            p.allowed = new String[(int) vals.size()];
            for (int j = 0; j < vals.size(); j++)
               p.allowed[j] = vals.get(j);

            props.add(p);
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      return props.toArray(new PropertyItem[0]);
   }

   public String getPropertyValue(String name) throws MMException {
      try {
         return core_.getProperty(devName_, name);
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }

   public PropertyItem getProperty(String name) throws MMException {
      try {
         if (core_.hasProperty(devName_, name)) {
            PropertyItem p = new PropertyItem();
            p.device = devName_;
            p.name = name;
            p.value = core_.getProperty(devName_, p.name);
            p.readOnly = core_.isPropertyReadOnly(devName_, p.name);
            if (core_.hasPropertyLimits(devName_, p.name)) {
               p.lowerLimit = core_.getPropertyLowerLimit(devName_, p.name);
               p.upperLimit = core_.getPropertyUpperLimit(devName_, p.name);
            }

            StrVector vals = core_.getAllowedPropertyValues(devName_, p.name);
            p.allowed = new String[(int) vals.size()];
            for (int j = 0; j < vals.size(); j++)
               p.allowed[j] = vals.get(j);
            return p;
         } else {
            throw new MMException("Unknown property: " + name);
         }
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }

   public void setPropertyValue(String name, String value) throws MMException {
      try {
         core_.setProperty(devName_, name, value);
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }

   public double getCurrentFocusScore() {
      try {
         core_.setAutoFocusDevice(devName_);
      } catch (Exception e) {
          ReportingUtils.logError(e);
         return 0;
      }
      return core_.getCurrentFocusScore();
   }

   public void applySettings() {
   }

   public void saveSettings() {
      // we could save current property settings in prefs.  Not sure if that is a good idea
   }

   public int getNumberOfImages() {
      return core_.getRemainingImageCount();
   }

   public String getDeviceName() {
      return devName_;
   }

   public void setProperty(PropertyItem p) throws MMException {
      try {
         core_.setProperty(devName_, p.name, p.value);
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }

   public void enableContinuousFocus(boolean enable) throws MMException {
      try {
         core_.setAutoFocusDevice(devName_);
         core_.enableContinuousFocus(enable);
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }

   public boolean isContinuousFocusEnabled() throws MMException {
      try {
         core_.setAutoFocusDevice(devName_);
         return core_.isContinuousFocusEnabled();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }

   public boolean isContinuousFocusLocked() throws MMException {
      try {
         core_.setAutoFocusDevice(devName_);
         return core_.isContinuousFocusLocked();
      } catch (Exception e) {
         throw new MMException(e.getMessage());
      }
   }

   public AcquisitionData getFocusingSequence() throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setApp(ScriptInterface app) {
      core_ = app.getMMCore();
      devName_ = core_.getAutoFocusDevice();      
   }

}
