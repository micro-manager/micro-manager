package org.micromanager.internal.jacque;

import ij.process.ImageProcessor;
import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.PropertyItem;

final class HelperFakeAutofocus implements AutofocusPlugin {

   private final String name;
   private final Double resultZ;
   private final HelperRecordingMockCore mockCore;
   private final String zDrive;

   HelperFakeAutofocus(String name, Double resultZ,
         HelperRecordingMockCore mockCore, String zDrive) {
      this.name = name;
      this.resultZ = resultZ;
      this.mockCore = mockCore;
      this.zDrive = zDrive;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public double fullFocus() throws Exception {
      if (resultZ == null) {
         throw new Exception("Autofocus failed");
      }
      mockCore.updatePositionSilently(zDrive, resultZ);
      return resultZ;
   }

   @Override
   public void setContext(Studio studio) {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getHelpText() {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getVersion() {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getCopyright() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void initialize() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void applySettings() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void saveSettings() {
      throw new UnsupportedOperationException();
   }

   @Override
   public double incrementalFocus() throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public int getNumberOfImages() {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getVerboseStatus() {
      throw new UnsupportedOperationException();
   }

   @Override
   public PropertyItem[] getProperties() {
      throw new UnsupportedOperationException();
   }

   @Override
   public String[] getPropertyNames() {
      throw new UnsupportedOperationException();
   }

   @Override
   public PropertyItem getProperty(String key) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setProperty(PropertyItem p) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public String getPropertyValue(String name) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setPropertyValue(String name, String value)
         throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public double getCurrentFocusScore() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void enableContinuousFocus(boolean enable) throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean isContinuousFocusEnabled() throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean isContinuousFocusLocked() throws Exception {
      throw new UnsupportedOperationException();
   }

   @Override
   public double computeScore(ImageProcessor impro) {
      throw new UnsupportedOperationException();
   }
}
