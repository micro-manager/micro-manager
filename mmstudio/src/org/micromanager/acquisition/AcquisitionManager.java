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

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.display.DisplayWindow;
import org.micromanager.imagedisplay.DefaultDisplaySettings;
import org.micromanager.MMStudio;
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

   public boolean closeAllImageWindows() {
      for (String name : getAcquisitionNames()) {
         MMAcquisition acq = acqs_.get(name);
         Datastore store = acq.getDatastore();
         for (DisplayWindow display : MMStudio.getInstance().display().getDisplays(store)) {
            if (!display.requestToClose()) {
               return false;
            }
         }
      }
      return true;
   }
}
