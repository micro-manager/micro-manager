///////////////////////////////////////////////////////////////////////////////
//FILE:          RatioAcqManager.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California, 2018
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

package org.micromanager.ratioimaging;

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.RememberedDisplaySettings;

/**
 * Creates and tracks the datastore and display window that hold the float
 * ratio images.
 *
 * <p>The ratio is 32-bit float, whereas the source channels are usually 8 or
 * 16-bit integer.  Micro-Manager datastores can not hold images of differing
 * pixel type (see ImageSizeChecker), so the ratio images go into their own
 * datastore rather than being added as an extra channel to the acquisition.
 *
 * <p>This class is owned by the RatioImagingFactory, so that it outlives the
 * individual Processors (which are created anew for each acquisition).
 */
public class RatioAcqManager {
   public static final String RATIO_DISPLAYSETTINGS = "Ratio_Display";

   private final Studio studio_;
   // One entry per ratio datastore created by this plugin instance.  A single
   // field would not do: createStoreAndDisplay is called once per acquisition,
   // and the windows of earlier acquisitions may still be open.
   private final Map<DataProvider, DisplayWindow> windows_ =
            new HashMap<DataProvider, DisplayWindow>();

   public RatioAcqManager(Studio studio) {
      studio_ = studio;
   }

   /**
    * Creates a RAM datastore for the float ratio images, along with a display
    * window showing it.
    *
    * @param summaryMetadata SummaryMetadata of the input data, used as the base
    *                        for the ratio datastore's SummaryMetadata.
    * @param channelName Name of the (single) ratio channel.
    * @param prefix Name of the datastore, shown in the display window's title.
    * @param width Width of the ratio images in pixels.
    * @param height Height of the ratio images in pixels.
    * @return The newly created Datastore, or null if creation failed.
    * @throws IOException If setting the SummaryMetadata fails.
    */
   public Datastore createStoreAndDisplay(SummaryMetadata summaryMetadata,
                                          String channelName,
                                          String prefix,
                                          int width,
                                          int height) throws IOException {
      Datastore store = studio_.data().createRAMDatastore();
      if (store == null) {
         studio_.logs().showError("Failed to create datastore for Ratio Imaging.");
         return null;
      }
      store.setName(prefix);

      // The ratio datastore holds a single channel.  Channel names reach the
      // display through the SummaryMetadata, so this must be set before the
      // display is created.
      Coords.Builder cb = summaryMetadata.getIntendedDimensions().copyBuilder().c(1);
      SummaryMetadata outputSummaryMetadata = summaryMetadata.copyBuilder()
               .channelNames(new String[] {channelName})
               .intendedDimensions(cb.build())
               .imageWidth(width)
               .imageHeight(height)
               .prefix(prefix)
               .build();
      store.setSummaryMetadata(outputSummaryMetadata);

      DisplaySettings displaySettings =
               studio_.displays().displaySettingsFromProfile(RATIO_DISPLAYSETTINGS);
      if (displaySettings == null) {
         displaySettings = studio_.displays().displaySettingsFromProfile(
                  PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());
      }
      DisplaySettings.Builder displaySettingsBuilder;
      if (displaySettings == null) {
         displaySettingsBuilder = studio_.displays().displaySettingsBuilder();
      } else {
         displaySettingsBuilder = displaySettings.copyBuilder();
      }
      displaySettingsBuilder.colorModeGrayscale();
      displaySettingsBuilder.channel(0,
               RememberedDisplaySettings.loadChannel(studio_,
                        outputSummaryMetadata.getChannelGroup(),
                        channelName,
                        displaySettings != null
                                 ? displaySettings.getChannelColor(0)
                                 : Color.WHITE));

      DisplayWindow window =
               studio_.displays().createDisplay(store, null, displaySettingsBuilder.build());
      window.setWindowPositionKey(RATIO_DISPLAYSETTINGS);
      window.setDisplaySettingsProfileKey(RATIO_DISPLAYSETTINGS);
      forgetClosedWindows();
      windows_.put(store, window);
      window.show();

      return store;
   }

   /**
    * Drops entries whose window the user has already closed.
    *
    * <p>closeViewerFor() only runs for datastores that ended up empty, so
    * without this the map would retain an entry for every ratio window ever
    * opened.
    */
   private void forgetClosedWindows() {
      Iterator<Map.Entry<DataProvider, DisplayWindow>> it = windows_.entrySet().iterator();
      while (it.hasNext()) {
         if (it.next().getValue().isClosed()) {
            it.remove();
         }
      }
   }

   /**
    * Closes the display window belonging to the given data provider, if any.
    *
    * <p>The entry is removed whether or not a window was found, so that this
    * map does not keep growing as acquisitions come and go.
    *
    * @param provider DataProvider whose viewer should be closed.
    */
   public void closeViewerFor(DataProvider provider) {
      DisplayWindow window = windows_.remove(provider);
      if (window != null) {
         window.close();
      }
   }
}
