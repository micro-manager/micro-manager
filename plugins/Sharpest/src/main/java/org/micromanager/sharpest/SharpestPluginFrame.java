///////////////////////////////////////////////////////////////////////////////
//FILE:          SharpestPluginFrame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Sharpest plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Alsto Labs, 2024
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

package org.micromanager.sharpest;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.display.DisplayWindow;
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * UI of the Sharpest plugin.
 *
 * @author nico
 */
public class SharpestPluginFrame extends JDialog {
   private final Studio studio_;
   private final DisplayWindow ourWindow_;
   private final MutablePropertyMapView settings_;

   /**
    * Constructor. Draws the UI.
    *
    * @param studio The Micro-Manager Studio Object
    * @param window The DataViewer that we are working on
    */
   public SharpestPluginFrame(Studio studio, DisplayWindow window) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(SharpestPlugin.class);
      final SharpestPluginFrame cpFrame = this;
      
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      
      ourWindow_ = window;
      DataProvider ourProvider = ourWindow_.getDataProvider();

      // Not sure if this is needed, be safe for now
      if (!(ourProvider).isFrozen()) {
         studio_.logs().showMessage("Can not Project ongoing acquisitions",
                 window.getWindow());
         return;
      }

      super.setLayout(new MigLayout("flowx, fill, insets 8"));
      String shortName = ourProvider.getName();
      super.setTitle(SharpestPlugin.MENUNAME + shortName);
      
      final JLabel sharpnessMethodLabel = new JLabel("sharpness method");
      final JComboBox<String> sharpnessMethodBox =
              new JComboBox<>(ImgSharpnessAnalysis.Method.getNames());
      sharpnessMethodBox.addActionListener((ActionEvent e) ->
              settings_.putString(SharpestPlugin.SHARPNESS,
                     (String) sharpnessMethodBox.getSelectedItem()));
      sharpnessMethodBox.setSelectedItem(settings_.getString(
               SharpestPlugin.SHARPNESS, ImgSharpnessAnalysis.Method.getNames()[0]));

      final JLabel keepPlanesLabel = new JLabel("keep planes");
      final JComboBox<Integer> keepPlanesBox = new JComboBox<>();
      for (int z = 1; z < ourProvider.getNextIndex(Coords.Z) - 1; z = z + 2) {
         keepPlanesBox.addItem(z);
      }
      keepPlanesBox.setSelectedItem(settings_.getInteger(SharpestPlugin.KEEP_PLANES, 1));
      keepPlanesBox.addActionListener((ActionEvent e) ->
              settings_.putInteger(SharpestPlugin.KEEP_PLANES,
                      (Integer) keepPlanesBox.getSelectedItem()));

      boolean showChannelSelectors = false;
      List<String> channelNames = ourProvider.getSummaryMetadata().getChannelNameList();
      if (channelNames != null && channelNames.size() > 1) {
         showChannelSelectors = true;
      }
      final JCheckBox eachChannelBox = new JCheckBox("Select sharpest for each channel");
      final JLabel selectChannelLabel = new JLabel("Channel to sharpen");
      final JComboBox<String> channelBox = new JComboBox<>();
      if (channelNames != null) {
         for (String channelName : channelNames) {
            channelBox.addItem(channelName);
         }
      }
      channelBox.setSelectedItem(settings_.getString(SharpestPlugin.CHANNEL, ""));
      channelBox.addActionListener((ActionEvent e) ->
              settings_.putString(SharpestPlugin.CHANNEL,
                      (String) channelBox.getSelectedItem()));
      eachChannelBox.addActionListener((ActionEvent e) -> {
         channelBox.setEnabled(!eachChannelBox.isSelected());
         settings_.putBoolean(SharpestPlugin.EACH_CHANNEL, eachChannelBox.isSelected());
      });
      eachChannelBox.setSelected(settings_.getBoolean(SharpestPlugin.EACH_CHANNEL, false));
      channelBox.setEnabled(!eachChannelBox.isSelected());

      final JCheckBox showGraphBox = new JCheckBox("show graph");
      showGraphBox.setSelected(settings_.getBoolean(SharpestPlugin.SHOW_SHARPNESS_GRAPH, false));
      showGraphBox.addActionListener((ActionEvent e) ->
              settings_.putBoolean(SharpestPlugin.SHOW_SHARPNESS_GRAPH,
                      showGraphBox.isSelected()));

      super.add(sharpnessMethodLabel);
      super.add(sharpnessMethodBox, "span2, grow, wrap");
      super.add(keepPlanesLabel);
      super.add(keepPlanesBox, "span2, grow, wrap");
      if (showChannelSelectors) {
         super.add(eachChannelBox, "span3, grow, wrap");
         super.add(selectChannelLabel);
         super.add(channelBox, "span2, grow, wrap");
      }
      super.add(showGraphBox, "span3, grow, wrap");

      super.add(new JLabel("name"));
      final JTextField nameField = new JTextField(shortName);
      super.add(nameField, "span2, grow, wrap");
     
      final JCheckBox saveBox = new JCheckBox("save result");
      saveBox.setSelected(settings_.getBoolean(SharpestPlugin.SAVE, false));
      saveBox.addActionListener((ActionEvent e) -> {
         settings_.putBoolean(SharpestPlugin.SAVE, saveBox.isSelected());
      });
      super.add(saveBox, "span3, grow, wrap");
      
      JButton okButton = new JButton("OK");
      okButton.addActionListener((ActionEvent ae) -> {
         SharpestPluginExecutor zp = new SharpestPluginExecutor(studio_, ourWindow_);
         ImgSharpnessAnalysis.Method method = ImgSharpnessAnalysis.Method
                 .valueOf((String) sharpnessMethodBox.getSelectedItem());
         int nrPlanes = 1;
         Object selectedItem = keepPlanesBox.getSelectedItem();
         if (selectedItem instanceof Integer) {
            nrPlanes = (Integer) selectedItem;
         }
         SharpestData zpd = new SharpestData(method,  showGraphBox.isSelected(), nrPlanes,
                 eachChannelBox.isSelected(), (String) channelBox.getSelectedItem());
         zp.project(saveBox.isSelected(),
                 nameField.getText(),
                 zpd);
         cpFrame.dispose();
      });
      super.add(okButton, "span 3, split 2, tag ok, wmin button");
      
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener((ActionEvent ae) -> {
         cpFrame.dispose();
      });
      super.add(cancelButton, "tag cancel, wrap");
      
      super.pack();
      
      Window w = ourWindow_.getWindow();
      int xCenter = w.getX() + w.getWidth() / 2;
      int yCenter = w.getY() + w.getHeight() / 2;
      super.setLocation(xCenter - super.getWidth() / 2, 
              yCenter - super.getHeight());
      
      super.setVisible(true);
      
      
   }
   
}
