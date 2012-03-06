/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import ij.ImagePlus;
import java.awt.Color;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;

/**
 *
 * @author arthur
 */
public interface ImageCache extends TaggedImageStorage {
   void addImageCacheListener(ImageCacheListener l);
   Set<String> getChangingKeys();
   String getComment();
   String getDiskLocation();
   JSONObject getDisplayAndComments();
   TaggedImage getImage(int channel, int slice, int frame, int position);
   JSONObject getImageTags(int channel, int slice, int frame, int position);
   ImageCacheListener[] getImageStorageListeners();
   JSONObject getLastImageTags();
   JSONObject getSummaryMetadata();
   boolean isFinished();
   int lastAcquiredFrame();
   void removeImageStorageListener(ImageCacheListener l);
   void saveAs(TaggedImageStorage newImageFileManager);
   void saveAs(TaggedImageStorage newImageFileManager, boolean moveToNewStorage);
   void setComment(String text);
   void setDisplayAndComments(JSONObject settings);
   void setSummaryMetadata(JSONObject tags);
   void setImageComment(String comment, JSONObject tags);
   String getImageComment(JSONObject tags);
   void setDisplay(VirtualAcquisitionDisplay disp);
   public void storeChannelDisplaySettings(int channelIndex, int min, int max, double gamma, int histMax);
   public JSONObject getChannelSetting(int channel);
   public int getBitDepth();
   //public int getChannelBitDepth(int channelIndex);
   public Color getChannelColor(int channelIndex);
   public void setChannelColor(int channel, int rgb);
   public String getChannelName(int channelIndex);
   public void setChannelName(int channel, String channelName) ;
   public void setChannelVisibility(int channelIndex, boolean visible);
   public int getChannelMin(int channelIndex);
   public int getChannelMax(int channelIndex) ;
   public double getChannelGamma(int channelIndex);
   public int getChannelHistogramMax(int channelIndex);
   public int getNumChannels();
   public ImagePlus getImagePlus();

   public String getPixelType();
  

}
