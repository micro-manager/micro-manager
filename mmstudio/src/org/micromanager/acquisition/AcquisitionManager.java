package org.micromanager.acquisition;

import ij.CompositeImage;
import ij.gui.ImageWindow;

import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;
import java.util.prefs.Preferences;

import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class AcquisitionManager {
   private static final String ALBUM_WIN_X = "album_x";
   private static final String ALBUM_WIN_Y = "album_y";
   
   Hashtable<String, MMAcquisition> acqs_;
   private String album_ = null;
   
   public AcquisitionManager() {
      acqs_ = new Hashtable<String, MMAcquisition>();
   }
   
   public void openAcquisition(String name, String rootDir) throws MMScriptException {
      if (acquisitionExists(name))
         throw new MMScriptException("The name is in use");
      else {
         MMAcquisition acq = new MMAcquisition(name, rootDir);
         acqs_.put(name, acq);
      }
   }
   
   public void openAcquisition(String name, String rootDir, boolean show) throws MMScriptException {
      this.openAcquisition(name, rootDir, show, false);
   }

   public void openAcquisition(String name, String rootDir, boolean show, boolean diskCached) throws MMScriptException {
      this.openAcquisition(name, rootDir, show, diskCached, false);
   }

   public void openAcquisition(String name, String rootDir, boolean show,
           boolean diskCached, boolean existing) throws MMScriptException {
      if (acquisitionExists(name)) {
         throw new MMScriptException("The name is in use");
      } else {
         acqs_.put(name, new MMAcquisition(name, rootDir, show, diskCached, existing));
      }
   }
   
  
   public void closeAcquisition(final String name) throws MMScriptException {
      if (name == null)
         return;
      final MMScriptException ex[] = {null};
      try {
         GUIUtils.invokeAndWait(new Runnable() {
            public void run() {
               if (!acqs_.containsKey(name)) {
                 ex[0] = new MMScriptException(
                    "The acquisition named \"" + name + "\" does not exist");
               } else {
                  acqs_.get(name).close();
                  acqs_.remove(name);
               }
            }
         });
         if (ex[0] != null)
            throw ex[0];
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }
   
   /**
    * Closes display window associated with an acquisition
    * @param name of acquisition
    * @return false if canceled by user, true otherwise
    * @throws MMScriptException 
    */
   public boolean closeImageWindow(String name) throws MMScriptException {
      if (!acquisitionExists(name))
         throw new MMScriptException("The name does not exist");
      
      return acqs_.get(name).closeImageWindow();
   }
   
   /**
    * Closes all windows associated with acquisitions
    * Can be interrupted by the user (by pressing cancel)
    * 
    * @return false is saving was canceled, true otherwise
    * @throws MMScriptException 
    */
   public boolean closeAllImageWindows() throws MMScriptException {
      String[] acqNames = getAcquisitionNames();
      for (String acqName : acqNames) {
         if (!closeImageWindow(acqName)) {
            return false;
         }
      }       
      return true;
   }
   
   public boolean acquisitionExists(String name) {
      if (acqs_.containsKey(name)) {
         MMAcquisition acq = acqs_.get(name);
         if (acq.getShow() && acq.windowClosed()) {
            acq.close();
            acqs_.remove(name);
            return false;
         }
          
         return true;
      }
      return false;
   }
      
   public MMAcquisition getAcquisition(String name) throws MMScriptException {
      if (acquisitionExists(name))
         return acqs_.get(name);
      else
         throw new MMScriptException("Undefined acquisition name: " + name);
   }

   public void closeAll() {
      for (Enumeration<MMAcquisition> e=acqs_.elements(); e.hasMoreElements(); ) {
         e.nextElement().close();
      }
      acqs_.clear();
   }

   public String getUniqueAcquisitionName(String name) {
      char separator = '_';
      while (acquisitionExists(name)) {
         int lastSeparator = name.lastIndexOf(separator);
         if (lastSeparator == -1)
            name += separator + "1";
         else {
            Integer i = Integer.parseInt(name.substring(lastSeparator + 1));
            i++;
            name = name.substring(0, lastSeparator) + separator + i;
         }
      }
      return name;
   }

   public String getCurrentAlbum() {
      if (album_ == null) {
         return createNewAlbum();
      } else {
         return album_;
      }
   }

   public String createNewAlbum() {
      album_ = getUniqueAcquisitionName("Album");
      return album_;
   }

   public String addToAlbum(TaggedImage image, JSONObject displaySettings) throws MMScriptException {
      boolean newNeeded = true;
      MMAcquisition acq = null;
      String album = getCurrentAlbum();
      JSONObject tags = image.tags;
      int imageWidth, imageHeight, imageDepth, imageBitDepth, numChannels;

      try {
         imageWidth = MDUtils.getWidth(tags);
         imageHeight = MDUtils.getHeight(tags);
         imageDepth = MDUtils.getDepth(tags);
         imageBitDepth = MDUtils.getBitDepth(tags);
         // need to check number of channels so that multi cam and single cam
         // acquistions of same size and depth are differentiated
         numChannels = MDUtils.getNumChannels(tags);
      } catch (Exception e) {
         throw new MMScriptException("Something wrong with image tags.");
      }

      if (acquisitionExists(album)) {
         acq = acqs_.get(album);
         try {
         if (acq.getWidth() == imageWidth &&
             acq.getHeight() == imageHeight &&
             acq.getByteDepth() == imageDepth  &&
             acq.getMultiCameraNumChannels() == numChannels &&
                ! acq.getImageCache().isFinished() )
             newNeeded = false;
         } catch (Exception e) {
            ReportingUtils.logError(e, "Couldn't check if we need a new album");
         }
      }

      if (newNeeded) {
         album = createNewAlbum();
         openAcquisition(album, "", true, false);
         acq = getAcquisition(album);
         // HACK: adjust number of frames based on number of
         // channels/components.  If we don't do this, then multi-channel
         // images don't display properly (we just get repeat images), because
         // the ImageJ object refuses to change which frame it displays. I have
         // *no idea* why this happens, but instead of debugging and patching
         // ImageJ itself, we're resorting to this workaround.
         boolean mustHackDims = false;
         try {
            mustHackDims = (numChannels > 1 ||
                  MDUtils.getNumberOfComponents(image.tags) > 1);
         }
         catch (JSONException ex) {
            ReportingUtils.logError(ex, "Unable to determine number of components of image");
         }
         acq.setDimensions(mustHackDims ? 2 : 1, numChannels, 1, 1);
         acq.setImagePhysicalDimensions(imageWidth, imageHeight, imageDepth, imageBitDepth, numChannels);

         try {
            JSONObject summary = new JSONObject();
            MDUtils.setPixelTypeFromString(summary, MDUtils.getPixelType(tags));
            acq.setSummaryProperties(summary);
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
         
         acq.initialize();
         
         //Store album window position
         final ImageWindow win = acq.getAcquisitionWindow().getImagePlus().getWindow();
         final Preferences prefs = Preferences.userNodeForPackage(this.getClass());
         win.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
               Point loc = win.getLocation();
               prefs.putInt(ALBUM_WIN_X, loc.x);
               prefs.putInt(ALBUM_WIN_Y, loc.y);
            }
         });
         
         //Laod window position
         win.setLocation(prefs.getInt(ALBUM_WIN_X, 0), prefs.getInt(ALBUM_WIN_Y, 0));
      }

      // This image goes, by default, into the next frame.
      int newImageFrame = acq.getLastAcquiredFrame() + 1;

      // This makes sure that the second multicamera image has the correct
      // frame index.
      // Assumes that multi channel additions add channel 0 first.
      if (numChannels > 1) {
         try {
            JSONObject lastTags = acq.getImageCache().getLastImageTags();
            int lastCh = -1;
            if (lastTags != null) {
               lastCh = MDUtils.getChannelIndex(lastTags);
            }
            if (lastCh == 0) {
               newImageFrame = acq.getLastAcquiredFrame();
            }
         } catch (JSONException ex) {
           ReportingUtils.logError(ex);
         }
      }
      try {
         MDUtils.setFrameIndex(tags, newImageFrame);
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }
      try {
         // update summary metadata
         acq.getSummaryMetadata().put("Frames", newImageFrame + 1);
      } catch (JSONException ex) {
         ReportingUtils.logError("Couldn't update number of frames in album summary metadata");
      }
      acq.insertImage(image);

      //Apply appropriate contrast
      if (numChannels == 1) { //monochrome
         try {
           if (MDUtils.getFrameIndex(tags) == 0) {
              if (displaySettings != null) 
                 copyDisplaySettings(acq, displaySettings);                
            }
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }

      } else {//multi camera
         try {
            if (numChannels > 1 && MDUtils.getChannelIndex(tags) == numChannels - 1 && acq.getLastAcquiredFrame() == 0) {                     
               if (displaySettings != null) 
                  copyDisplaySettings(acq, displaySettings);
            }
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
      
      return album;
   }
  

   private void copyDisplaySettings(MMAcquisition acq, JSONObject displaySettings) {
      if (displaySettings == null) 
         return;
      ImageCache ic = acq.getImageCache();
      for (int i = 0; i < ic.getNumDisplayChannels(); i++) {
         try {
            JSONObject channelSetting = (JSONObject) ((JSONArray) displaySettings.get("Channels")).get(i);
            int color = channelSetting.getInt("Color");
            int min = channelSetting.getInt("Min");
            int max = channelSetting.getInt("Max");
            double gamma = channelSetting.getDouble("Gamma");
            String name = channelSetting.getString("Name");
            int histMax;
            if (channelSetting.has("HistogramMax")) {
               histMax = channelSetting.getInt("HistogramMax");
            }
            else {
               histMax = -1;
            }
            int displayMode = CompositeImage.COMPOSITE;
            if (channelSetting.has("DisplayMode")) {
               displayMode = channelSetting.getInt("DisplayMode");
            }
            
            ic.storeChannelDisplaySettings(i, min, max, gamma, histMax, displayMode);
            acq.getAcquisitionWindow().setChannelHistogramDisplayMax(i,histMax);
            acq.getAcquisitionWindow().setChannelContrast(i, min, max, gamma);
            acq.getAcquisitionWindow().setDisplayMode(displayMode);
            acq.setChannelColor(i, color);
            acq.setChannelName(i, name);

         } catch (JSONException ex) {
            ReportingUtils.logError("Something wrong with Display and Comments");
         } catch (MMScriptException e) {
            ReportingUtils.logError(e);
         }
      }
   }
 
   public String[] getAcquisitionNames() {
      Set<String> keySet = acqs_.keySet();
      String keys[] = new String[keySet.size()];
      return keySet.toArray(keys);
   }

   public String createAcquisition(JSONObject summaryMetadata, boolean diskCached, AcquisitionEngine engine, boolean displayOff) {
      String name = this.getUniqueAcquisitionName("Acq");
      acqs_.put(name, new MMAcquisition(name, summaryMetadata, diskCached, engine, !displayOff));
      return name;
   }
}
