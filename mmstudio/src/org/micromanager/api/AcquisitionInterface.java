/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import java.util.Map;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.acquisition.AcquisitionData;
import org.micromanager.utils.MMScriptException;

/**
 *
 * @author arthur
 */
public interface AcquisitionInterface {

   void close();

   void closeImage5D();

   AcquisitionData getAcqData();

   int getChannels();

   int getDepth();

   int getFrames();

   int getHeight();

   String getProperty(String propertyName) throws MMScriptException;

   String getProperty(int frame, int channel, int slice, String propName) throws MMScriptException;

   int getSlices();

   int getWidth();

   boolean hasActiveImage5D();

   void initialize() throws MMScriptException;

   void insertImage(Object pixels, int frame, int channel, int slice) throws MMScriptException;

   void insertImage(Object pixels, int frame, int channel, int slice, int position) throws MMScriptException;

   void insertTaggedImage(TaggedImage taggedImg, int frame, int channel, int slice) throws MMScriptException;

   void insertImage(TaggedImage taggedImg) throws MMScriptException;

   void insertImage(TaggedImage taggedImg, boolean updateDisplay) throws MMScriptException;

   boolean isInitialized();

   void setChannelColor(int channel, int rgb) throws MMScriptException;

   void setChannelContrast(int channel, int min, int max) throws MMScriptException;

   void setChannelName(int channel, String name) throws MMScriptException;

   void setComment(String comment) throws MMScriptException;

   void setContrastBasedOnFrame(int frame, int slice) throws MMScriptException;

   void setDimensions(int frames, int channels, int slices, int positions) throws MMScriptException;

   void setImagePhysicalDimensions(int width, int height, int depth) throws MMScriptException;

   void setProperty(String propertyName, String value) throws MMScriptException;

   void setProperty(int frame, int channel, int slice, String propName, String value) throws MMScriptException;

   void setRootDirectory(String dir) throws MMScriptException;

   void setSummaryProperties(JSONObject md) throws MMScriptException;

   void setSystemState(JSONObject md) throws MMScriptException;

   void setSystemState(int frame, int channel, int slice, JSONObject state) throws MMScriptException;

   boolean windowClosed();

   public void setEngine(AcquisitionEngine eng);

   public void setCache(MMImageCache imageCache);

}
