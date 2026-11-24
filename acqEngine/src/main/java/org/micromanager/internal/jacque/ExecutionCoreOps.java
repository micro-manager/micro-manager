package org.micromanager.internal.jacque;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DoubleVector;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

interface ExecutionCoreOps extends CoreOps {
   // Device names
   String getCameraDevice();
   String getShutterDevice();
   // getFocusDevice() inherited from CoreOps
   String getAutoFocusDevice();
   String getXYStageDevice();

   // Queries
   boolean getAutoShutter();
   double getExposure() throws Exception;
   double getPosition(String stage) throws Exception;
   void getXYPosition(String stage, double[] x, double[] y) throws Exception;
   long getNumberOfCameraChannels();
   String getCameraChannelName(int index) throws Exception;
   boolean getShutterOpen() throws Exception;
   boolean isContinuousFocusEnabled() throws Exception;
   boolean isContinuousFocusDrive(String stage) throws Exception;
   boolean isBufferOverflowed() throws Exception;
   boolean isSequenceRunning() throws Exception;
   boolean hasProperty(String dev, String prop) throws Exception;
   String getProperty(String dev, String prop) throws Exception;
   long getImageWidth();
   long getImageHeight();
   long getImageBitDepth();
   long getBytesPerPixel();
   double getPixelSizeUm();
   String getPixelSizeAffineAsString() throws Exception;
   long getNumberOfComponents();

   // State modification
   void setAutoShutter(boolean auto) throws Exception;
   void setExposure(double exp) throws Exception;
   void setPosition(String stage, double pos) throws Exception;
   void setXYPosition(String stage, double x, double y) throws Exception;
   void setShutterOpen(boolean open) throws Exception;
   void setProperty(String dev, String prop, String val) throws Exception;
   void enableContinuousFocus(boolean on) throws Exception;
   void waitForDevice(String dev) throws Exception;

   // Image acquisition
   void snapImage() throws Exception;
   TaggedImage getTaggedImage(int channelIndex) throws Exception;
   TaggedImage popNextTaggedImage() throws Exception;

   // Sequencing
   void loadPropertySequence(String dev, String prop, StrVector vals)
         throws Exception;
   void loadStageSequence(String stage, DoubleVector positions)
         throws Exception;
   void startPropertySequence(String dev, String prop) throws Exception;
   void startStageSequence(String stage) throws Exception;
   void startSequenceAcquisition(long numImages, double intervalMs,
         boolean stopOnOverflow) throws Exception;
   void stopSequenceAcquisition() throws Exception;
   void stopPropertySequence(String dev, String prop) throws Exception;
   void stopStageSequence(String stage) throws Exception;

   // System state
   Configuration getSystemStateCache() throws Exception;
   Configuration getConfigData(String group, String config) throws Exception;

   // ROI (for summary metadata)
   void getROI(int[] x, int[] y, int[] w, int[] h) throws Exception;

   // Logging
   void logMessage(String msg, boolean debug);

   static ExecutionCoreOps fromCMMCore(CMMCore mmc) {
      return new ExecutionCoreOps() {
         // CoreOps methods
         @Override
         public boolean isPropertySequenceable(String device, String property)
               throws Exception {
            return mmc.isPropertySequenceable(device, property);
         }

         @Override
         public int getPropertySequenceMaxLength(String device,
               String property) throws Exception {
            return mmc.getPropertySequenceMaxLength(device, property);
         }

         @Override
         public String getFocusDevice() {
            return mmc.getFocusDevice();
         }

         @Override
         public boolean isStageSequenceable(String device) throws Exception {
            return mmc.isStageSequenceable(device);
         }

         @Override
         public int getStageSequenceMaxLength(String device)
               throws Exception {
            return mmc.getStageSequenceMaxLength(device);
         }

         // Device names
         @Override
         public String getCameraDevice() {
            return mmc.getCameraDevice();
         }

         @Override
         public String getShutterDevice() {
            return mmc.getShutterDevice();
         }

         @Override
         public String getAutoFocusDevice() {
            return mmc.getAutoFocusDevice();
         }

         @Override
         public String getXYStageDevice() {
            return mmc.getXYStageDevice();
         }

         // Queries
         @Override
         public boolean getAutoShutter() {
            return mmc.getAutoShutter();
         }

         @Override
         public double getExposure() throws Exception {
            return mmc.getExposure();
         }

         @Override
         public double getPosition(String stage) throws Exception {
            return mmc.getPosition(stage);
         }

         @Override
         public void getXYPosition(String stage, double[] x, double[] y)
               throws Exception {
            mmc.getXYPosition(stage, x, y);
         }

         @Override
         public long getNumberOfCameraChannels() {
            return mmc.getNumberOfCameraChannels();
         }

         @Override
         public String getCameraChannelName(int index) throws Exception {
            return mmc.getCameraChannelName(index);
         }

         @Override
         public boolean getShutterOpen() throws Exception {
            return mmc.getShutterOpen();
         }

         @Override
         public boolean isContinuousFocusEnabled() throws Exception {
            return mmc.isContinuousFocusEnabled();
         }

         @Override
         public boolean isContinuousFocusDrive(String stage)
               throws Exception {
            return mmc.isContinuousFocusDrive(stage);
         }

         @Override
         public boolean isBufferOverflowed() throws Exception {
            return mmc.isBufferOverflowed();
         }

         @Override
         public boolean isSequenceRunning() throws Exception {
            return mmc.isSequenceRunning();
         }

         @Override
         public boolean hasProperty(String dev, String prop)
               throws Exception {
            return mmc.hasProperty(dev, prop);
         }

         @Override
         public String getProperty(String dev, String prop)
               throws Exception {
            return mmc.getProperty(dev, prop);
         }

         @Override
         public long getImageWidth() {
            return mmc.getImageWidth();
         }

         @Override
         public long getImageHeight() {
            return mmc.getImageHeight();
         }

         @Override
         public long getImageBitDepth() {
            return mmc.getImageBitDepth();
         }

         @Override
         public long getBytesPerPixel() {
            return mmc.getBytesPerPixel();
         }

         @Override
         public double getPixelSizeUm() {
            return mmc.getPixelSizeUm();
         }

         @Override
         public String getPixelSizeAffineAsString() throws Exception {
            return mmc.getPixelSizeAffineAsString();
         }

         @Override
         public long getNumberOfComponents() {
            return mmc.getNumberOfComponents();
         }

         // State modification
         @Override
         public void setAutoShutter(boolean auto) throws Exception {
            mmc.setAutoShutter(auto);
         }

         @Override
         public void setExposure(double exp) throws Exception {
            mmc.setExposure(exp);
         }

         @Override
         public void setPosition(String stage, double pos) throws Exception {
            mmc.setPosition(stage, pos);
         }

         @Override
         public void setXYPosition(String stage, double x, double y)
               throws Exception {
            mmc.setXYPosition(stage, x, y);
         }

         @Override
         public void setShutterOpen(boolean open) throws Exception {
            mmc.setShutterOpen(open);
         }

         @Override
         public void setProperty(String dev, String prop, String val)
               throws Exception {
            mmc.setProperty(dev, prop, val);
         }

         @Override
         public void enableContinuousFocus(boolean on) throws Exception {
            mmc.enableContinuousFocus(on);
         }

         @Override
         public void waitForDevice(String dev) throws Exception {
            mmc.waitForDevice(dev);
         }

         // Image acquisition
         @Override
         public void snapImage() throws Exception {
            mmc.snapImage();
         }

         @Override
         public TaggedImage getTaggedImage(int channelIndex)
               throws Exception {
            return mmc.getTaggedImage(channelIndex);
         }

         @Override
         public TaggedImage popNextTaggedImage() throws Exception {
            return mmc.popNextTaggedImage();
         }

         // Sequencing
         @Override
         public void loadPropertySequence(String dev, String prop,
               StrVector vals) throws Exception {
            mmc.loadPropertySequence(dev, prop, vals);
         }

         @Override
         public void loadStageSequence(String stage,
               DoubleVector positions) throws Exception {
            mmc.loadStageSequence(stage, positions);
         }

         @Override
         public void startPropertySequence(String dev, String prop)
               throws Exception {
            mmc.startPropertySequence(dev, prop);
         }

         @Override
         public void startStageSequence(String stage) throws Exception {
            mmc.startStageSequence(stage);
         }

         @Override
         public void startSequenceAcquisition(long numImages,
               double intervalMs, boolean stopOnOverflow) throws Exception {
            mmc.startSequenceAcquisition((int) numImages, intervalMs,
                  stopOnOverflow);
         }

         @Override
         public void stopSequenceAcquisition() throws Exception {
            mmc.stopSequenceAcquisition();
         }

         @Override
         public void stopPropertySequence(String dev, String prop)
               throws Exception {
            mmc.stopPropertySequence(dev, prop);
         }

         @Override
         public void stopStageSequence(String stage) throws Exception {
            mmc.stopStageSequence(stage);
         }

         // System state
         @Override
         public Configuration getSystemStateCache() throws Exception {
            return mmc.getSystemStateCache();
         }

         @Override
         public Configuration getConfigData(String group, String config)
               throws Exception {
            return mmc.getConfigData(group, config);
         }

         // ROI
         @Override
         public void getROI(int[] x, int[] y, int[] w, int[] h)
               throws Exception {
            mmc.getROI(x, y, w, h);
         }

         // Logging
         @Override
         public void logMessage(String msg, boolean debug) {
            mmc.logMessage(msg, debug);
         }
      };
   }
}
