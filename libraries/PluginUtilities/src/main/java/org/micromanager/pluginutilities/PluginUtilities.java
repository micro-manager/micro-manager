package org.micromanager.pluginutilities;

import java.util.Vector;
import javax.swing.JComboBox;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.micromanager.Studio;
import org.micromanager.display.DisplayWindow;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Utility functions for plugins.
 * To avoid code duplication, put common code that does not
 * belong in Studio itself here.
 */
public class PluginUtilities {
   private final Studio studio_;

   /**
    * Utility functions for plugins.
    * These will invariably need the Studio object.
    *
    * @param studio The main MM class.
    */
   public PluginUtilities(Studio studio)  {
      studio_ = studio;
   }

   /**
    * Creates a JCombo with entries from the current ChannelGroup.
    *
    * @param settings settings of the caller
    * @param prefKey Key under which the selected entry to store
    * @return JCombobox ready to be shown.
    */
   public JComboBox<String> createChannelCombo(
         MutablePropertyMapView settings, String prefKey) {

      final JComboBox<String> cBox = new JComboBox<>();
      String ch = "";
      if (settings.containsString(prefKey)) {
         ch = settings.getString(prefKey, "");
      }
      populateWithChannels(cBox);
      for (int i = 0; i < cBox.getItemCount(); i++) {
         if (ch.equals(cBox.getItemAt(i))) {
            cBox.setSelectedItem(ch);
         }
      }
      cBox.addActionListener(e -> settings.putString(
            prefKey, (String) cBox.getSelectedItem()));
      settings.putString(prefKey, (String) cBox.getSelectedItem());
      return cBox;
   }

   /**
    * Get the channel names from the core.
    * If there is more than one camera, add those as "channel-camera", however, try
    * to avoid the MultiCamera device by checking the library it comes from (Utilities).
    * Quite a bit of heuristics here...
    *
    * @param cBox The comboBox to be populated.
    */
   public void populateWithChannels(JComboBox<String> cBox) {
      final String selectedItem = (String) cBox.getSelectedItem();
      cBox.removeAllItems();
      String channelGroup = studio_.core().getChannelGroup();
      StrVector channels = studio_.core().getAvailableConfigs(channelGroup);
      StrVector camerasStrV = studio_.core().getLoadedDevicesOfType(DeviceType.CameraDevice);
      Vector<String> cameras = new Vector<>();
      for (String camera : camerasStrV) {
         try {
            if (!studio_.core().getDeviceLibrary(camera).equals("Utilities")) {
               cameras.add(camera);
            }
         } catch (Exception ex) {
            studio_.logs().logError(ex);
         }
      }
      for (int i = 0; i < channels.size(); i++) {
         cBox.addItem(channels.get(i));
         if (cameras.size() > 1) {
            for (String camera : cameras) {
               cBox.addItem(channels.get(i) + "-" + camera);
            }
         }
      }
      if (channels.size() == 0 && cameras.size() > 1) {
         for (String camera : cameras) {
            cBox.addItem(camera);
         }
      }
      // also add channels from images that are open.  This allows the user to
      // select channels that are not in the channel group.
      for (DisplayWindow dw : studio_.displays().getAllImageWindows()) {
         for (String ch : dw.getDataProvider().getSummaryMetadata().getChannelNameList()) {
            cBox.addItem(ch);
         }
      }
      cBox.setSelectedItem(selectedItem);
   }
}
