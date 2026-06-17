package org.micromanager.acqenginetests;

import ij.process.ImageProcessor;
import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.PropertyItem;

/**
 * Minimal AutofocusPlugin for tests. Its {@link #fullFocus()} simply drives the
 * Core's currently selected autofocus device (the SequenceTester TAutofocus),
 * which fires a "FullFocus" one-shot recorded in the image InfoPacket. It also
 * counts invocations so tests can assert how often autofocus ran.
 *
 * <p>Injected via {@code studio.getAutofocusManager().setAutofocusMethod(...)};
 * it is not discovered as a real plugin.
 */
public class TestAutofocusPlugin implements AutofocusPlugin {
   private Studio studio_;
   private int fullFocusCount_ = 0;

   public int getFullFocusCount() {
      return fullFocusCount_;
   }

   public void resetCount() {
      fullFocusCount_ = 0;
   }

   @Override
   public double fullFocus() throws Exception {
      fullFocusCount_++;
      // Drives TesterAutofocus::FullFocus -> recorded as device "TAutofocus"
      // key "FullFocus" in the InfoPacket history.
      studio_.core().fullFocus();
      return 0.0;
   }

   @Override
   public double incrementalFocus() throws Exception {
      studio_.core().incrementalFocus();
      return 0.0;
   }

   // --- everything below is an inert stub ---

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "TestAutofocus";
   }

   @Override
   public String getHelpText() {
      return "Test autofocus plugin";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "none";
   }

   @Override
   public void initialize() { }

   @Override
   public void applySettings() { }

   @Override
   public void saveSettings() { }

   @Override
   public int getNumberOfImages() {
      return 0;
   }

   @Override
   public String getVerboseStatus() {
      return "";
   }

   @Override
   public PropertyItem[] getProperties() {
      return new PropertyItem[0];
   }

   @Override
   public String[] getPropertyNames() {
      return new String[0];
   }

   @Override
   public PropertyItem getProperty(String key) {
      return new PropertyItem();
   }

   @Override
   public void setProperty(PropertyItem p) { }

   @Override
   public String getPropertyValue(String name) {
      return "";
   }

   @Override
   public void setPropertyValue(String name, String value) { }

   @Override
   public double getCurrentFocusScore() {
      return 0.0;
   }

   @Override
   public void enableContinuousFocus(boolean enable) { }

   @Override
   public boolean isContinuousFocusEnabled() {
      return false;
   }

   @Override
   public boolean isContinuousFocusLocked() {
      return false;
   }

   @Override
   public double computeScore(final ImageProcessor impro) {
      return 0.0;
   }
}
