package org.micromanager.aidpc;

import com.google.common.eventbus.Subscribe;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.events.ChannelGroupChangedEvent;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.pluginutilities.PluginUtilities;
import org.micromanager.propertymap.MutablePropertyMapView;

class AIDPCConfigurator implements ProcessorConfigurator {
   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private final PluginUtilities pluginUtilities_;
   private JComboBox<String> ch1Combo_;
   private JComboBox<String> ch2Combo_;
   private JDialog dialog_;


   public AIDPCConfigurator(PropertyMap settings, Studio studio) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(this.getClass());
      copySettings(settings_, settings);
      pluginUtilities_ = new PluginUtilities(studio_);

      // Initialize with default values if not set
      if (!settings_.containsKey(AIDPCProcessorPlugin.CHANNEL1)) {
         settings_.putString(AIDPCProcessorPlugin.CHANNEL1, "");
      }
      if (!settings_.containsKey(AIDPCProcessorPlugin.CHANNEL2)) {
         settings_.putString(AIDPCProcessorPlugin.CHANNEL2, "");
      }
      if (!settings_.containsKey(AIDPCProcessorPlugin.INCLUDE_AVG)) {
         settings_.putBoolean(AIDPCProcessorPlugin.INCLUDE_AVG, true);
      }
   }

   @Override
   public void showGUI() {
      // Create configuration dialog
      JPanel panel = new JPanel(new MigLayout("fillx"));

      final JCheckBox includeAvgBox = new JCheckBox("Include Average Channel",
            settings_.getBoolean(AIDPCProcessorPlugin.INCLUDE_AVG, true));

      ch1Combo_ = pluginUtilities_.createChannelCombo(settings_, AIDPCProcessorPlugin.CHANNEL1);
      ch2Combo_ = pluginUtilities_.createChannelCombo(settings_, AIDPCProcessorPlugin.CHANNEL2);

      panel.add(new JLabel("Channel 1 (I_R):"), "split 2");
      panel.add(ch1Combo_, "wrap");
      panel.add(new JLabel("Channel 2 (I_L):"), "split 2");
      panel.add(ch2Combo_, "wrap");
      panel.add(includeAvgBox, "wrap");

      includeAvgBox.addActionListener(e ->
            settings_.putBoolean(AIDPCProcessorPlugin.INCLUDE_AVG,
                  includeAvgBox.isSelected()));

      dialog_ = new JDialog(studio_.app().getMainWindow(), "DPC Processor Settings", false);
      dialog_.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      dialog_.getContentPane().add(panel);
      dialog_.pack();
      WindowPositioning.setUpLocationMemory(dialog_, this.getClass(), null);
      dialog_.setVisible(true);
   }

   @Override
   public PropertyMap getSettings() {
      return settings_.toPropertyMap();
   }

   private void copySettings(MutablePropertyMapView settings, PropertyMap configuratorSettings) {
      settings.putString(AIDPCProcessorPlugin.CHANNEL1, configuratorSettings.getString(
            AIDPCProcessorPlugin.CHANNEL1, ""));
      settings.putString(AIDPCProcessorPlugin.CHANNEL2, configuratorSettings.getString(
            AIDPCProcessorPlugin.CHANNEL2, ""));
   }

   @Override
   public void cleanup() {
      studio_.events().unregisterForEvents(this);
   }

   /**
    * Updates the UI when the ChannelGroup changes.
    *
    * @param event Event signalling the Channel group changed
    */
   @Subscribe
   public void onChannelGroupChanged(ChannelGroupChangedEvent event) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> onChannelGroupChanged(event));
         return;
      }
      pluginUtilities_.populateWithChannels(ch1Combo_);
      pluginUtilities_.populateWithChannels(ch2Combo_);
      dialog_.pack();
   }

   /**
    * Called when a new display opens.  Use this event to update the channel
    * list in the UI.
    *
    * @param dae Event signalling a new display was added.
    */
   @Subscribe
   @MustCallOnEDT
   public void onDisplayAdded(DataViewerAddedEvent dae) {
      pluginUtilities_.populateWithChannels(ch1Combo_);
      pluginUtilities_.populateWithChannels(ch2Combo_);
      dialog_.pack();
   }

}