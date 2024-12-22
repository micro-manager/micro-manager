package org.micromanager.aidpc;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

class AIDPCConfigurator implements ProcessorConfigurator {
   private final Studio studio_;
   private final MutablePropertyMapView settings_;

   public AIDPCConfigurator(PropertyMap settings, Studio studio) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(this.getClass());
      copySettings(settings_, settings);

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


      JTextField channel1Field = new JTextField(settings_.getString(
            AIDPCProcessorPlugin.CHANNEL1, ""), 20);
      JTextField channel2Field = new JTextField(settings_.getString(
            AIDPCProcessorPlugin.CHANNEL2, ""), 20);
      JCheckBox includeAvgBox = new JCheckBox("Include Average Channel",
            settings_.getBoolean(AIDPCProcessorPlugin.INCLUDE_AVG, true));

      panel.add(new JLabel("Channel 1 (I_R):"), "split 2");
      panel.add(channel1Field, "wrap");
      panel.add(new JLabel("Channel 2 (I_L):"), "split 2");
      panel.add(channel2Field, "wrap");
      panel.add(includeAvgBox, "wrap");

      channel1Field.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            settings_.putString(AIDPCProcessorPlugin.CHANNEL1, channel1Field.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            settings_.putString(AIDPCProcessorPlugin.CHANNEL1, channel1Field.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            settings_.putString(AIDPCProcessorPlugin.CHANNEL1, channel1Field.getText());
         }
      });

      channel2Field.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            settings_.putString(AIDPCProcessorPlugin.CHANNEL2, channel2Field.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            settings_.putString(AIDPCProcessorPlugin.CHANNEL2, channel2Field.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            settings_.putString(AIDPCProcessorPlugin.CHANNEL2, channel2Field.getText());
         }
      });

      includeAvgBox.addActionListener(e ->
            settings_.putBoolean(AIDPCProcessorPlugin.INCLUDE_AVG,
                  includeAvgBox.isSelected()));

      JDialog dialog = new JDialog(studio_.app().getMainWindow(), "DPC Processor Settings", false);
      dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      dialog.getContentPane().add(panel);
      dialog.pack();
      WindowPositioning.setUpLocationMemory(dialog, this.getClass(), null);
      dialog.setVisible(true);
   }

   @Override
   public void cleanup() {
      studio_.events().unregisterForEvents(this);
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
}