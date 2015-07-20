/*
 * An "empty" autofocus that pretends to always be in focus.
 *
 * Author: Mark Tsuchida, July 2015.
 * Copyright (c) 2015 University of California, San Francisco.
 *
 * License: This file is distributed under the BSD license.
 *          License text is included with the source distribution.
 *
 *          This file is distributed in the hope that it will be useful,
 *          but WITHOUT ANY WARRANTY; without even the implied warranty
 *          of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *          IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *          CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *          INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

import ij.process.ImageProcessor;
import org.micromanager.api.Autofocus;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PropertyItem;


public class NullAutofocus implements Autofocus {
   private boolean enabled_ = false;

   private void errorNoProperty(String key) throws MMException {
      throw new MMException("Unknown property: " + key);
   }

   public NullAutofocus() {
   }

   @Override public void applySettings() {}
   @Override public void saveSettings() {}
   @Override public void setApp(ScriptInterface app) {}
   @Override public double fullFocus() { return 0.0; }
   @Override public double incrementalFocus() { return 0.0; }
   @Override public int getNumberOfImages() { return 0; }
   @Override public String getVerboseStatus() { return "Null autofocus has no status"; }
   @Override public PropertyItem[] getProperties() { return new PropertyItem[0]; }
   @Override public String[] getPropertyNames() { return new String[0]; }

   @Override
   public PropertyItem getProperty(String key) throws MMException {
      errorNoProperty(key);
      return null;
   }

   @Override
   public void setProperty(PropertyItem item) throws MMException {
      errorNoProperty(item.name);
   }

   @Override
   public String getPropertyValue(String name) throws MMException {
      errorNoProperty(name);
      return null;
   }

   @Override
   public void setPropertyValue(String name, String value) throws MMException {
      errorNoProperty(name);
   }

   @Override public double getCurrentFocusScore() { return 0.0; }
   @Override public String getDeviceName() { return "NullAutofocus"; }
   @Override public void enableContinuousFocus(boolean f) { enabled_ = f; }
   @Override public boolean isContinuousFocusEnabled() { return enabled_; }
   @Override public boolean isContinuousFocusLocked() { return true; }
   @Override public double computeScore(ImageProcessor proc) { return 0.0; }
   @Override public void focus(double c, int nc, double f, int nf) {}
}
