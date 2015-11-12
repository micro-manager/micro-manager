///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.quickaccess.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ScreenImage;
import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.MMPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

/**
 * This class is responsible for managing the different Quick Access Windows.
 */
public class QuickAccessManager {
   private static final String SAVED_CONFIG = "Saved configuration for quick access windows";
   private static QuickAccessManager staticInstance_;
   /**
    * Create the manager, a static singleton.
    */
   public static void createManager(Studio studio) {
      staticInstance_ = new QuickAccessManager(studio);
      studio.events().registerForEvents(staticInstance_);
   }

   private Studio studio_;
   private ArrayList<QuickAccessFrame> knownFrames_;

   private QuickAccessManager(Studio studio) {
      studio_ = studio;
      knownFrames_ = new ArrayList<QuickAccessFrame>();
   }

   /**
    * Check the user's profile to see if we have any saved settings there.
    */
   @Subscribe
   public void onStartupComplete(StartupCompleteEvent event) {
      try {
         boolean hasContents = false;
         String configStr = studio_.profile().getString(
               QuickAccessManager.class, SAVED_CONFIG, null);
         if (configStr == null) {
            // Nothing saved.
            return;
         }
         try {
            JSONObject config = new JSONObject(configStr);
            JSONArray frames = config.getJSONArray("frames");
            for (int i = 0; i < frames.length(); ++i) {
               JSONObject frameConfig = frames.getJSONObject(i);
               knownFrames_.add(new QuickAccessFrame(studio_, frameConfig));
            }
         }
         catch (JSONException e) {
            studio_.logs().logError(e, "Unable to reconstruct Quick Access Window from config.");
         }
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Unable to reload Quick Access config");
      }
   }

   /**
    * Save the current setup to the user's profile.
    */
   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      try {
         // We store the array in a JSONObject, instead of storing the array
         // directly, to give us room to expand in future.
         JSONObject config = new JSONObject();
         JSONArray frameConfigs = new JSONArray();
         for (QuickAccessFrame frame : knownFrames_) {
            frameConfigs.put(frame.getConfig());
         }
         config.put("frames", frameConfigs);
         studio_.profile().setString(QuickAccessManager.class, SAVED_CONFIG,
               config.toString());
      }
      catch (JSONException e) {
         studio_.logs().logError(e, "Error saving Quick Access Window config");
      }
   }

   /**
    * Display all frames. If we don't have any, create a new one.
    */
   public static void showFrames() {
      if (staticInstance_.knownFrames_.size() == 0) {
         staticInstance_.knownFrames_.add(
               new QuickAccessFrame(staticInstance_.studio_,
                  new JSONObject()));
      }
      for (QuickAccessFrame frame : staticInstance_.knownFrames_) {
         frame.setVisible(true);
      }
   }

   /**
    * Create a new, empty frame.
    */
   public static void createNewFrame() {
      staticInstance_.knownFrames_.add(
            new QuickAccessFrame(staticInstance_.studio_, new JSONObject()));
   }

   /**
    * Remove an existing frame.
    */
   public static void deleteFrame(QuickAccessFrame frame) {
      staticInstance_.knownFrames_.remove(frame);
   }
}
