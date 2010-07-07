/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import mmcorej.Metadata;
import org.json.JSONObject;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.MMScriptException;

/**
 *
 * @author arthur
 */
public class MMVirtualAcquisition implements AcquisitionInterface {
   private final String dir_;
   private final String name_;
   MMImageCache imageCache_;
   
   public MMVirtualAcquisition(String name, String dir) {
      name_ = name;
      dir_ = dir;
   }

   public void close() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void closeImage5D() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public AcquisitionData getAcqData() {
      throw new UnsupportedOperationException("Not supported.");
   }

   public int getChannels() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getDepth() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getFrames() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getHeight() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getProperty(String propertyName) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getProperty(int frame, int channel, int slice, String propName) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getSlices() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getWidth() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public boolean hasActiveImage5D() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void initialize() throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void insertImage(Object pixels, int frame, int channel, int slice) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void insertImage(MMImageBuffer imgBuf) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public boolean isInitialized() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setChannelColor(int channel, int rgb) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setChannelContrast(int channel, int min, int max) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setChannelName(int channel, String name) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setComment(String comment) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setDimensions(int frames, int channels, int slices) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setProperty(String propertyName, String value) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setProperty(int frame, int channel, int slice, String propName, String value) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setRootDirectory(String dir) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setSummaryProperties(Metadata md) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException {
      throw new UnsupportedOperationException("Not supported.");
   }

   public boolean windowClosed() {
      return false;
   }

}
