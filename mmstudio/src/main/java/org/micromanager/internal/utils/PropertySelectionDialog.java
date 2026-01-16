///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:        Nico Stuurman
//
// COPYRIGHT:     University of California, San Francisco, 2025
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;

/**
 * Modal dialog for selecting properties from a PropertyMap.
 *
 * <p>Properties are displayed grouped by device with hierarchical checkboxes:
 * <ul>
 *   <li>A master "Select All" checkbox at the top controls all properties</li>
 *   <li>Each device has a checkbox that controls all properties for that device</li>
 *   <li>Individual property checkboxes are indented under their device</li>
 * </ul>
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PropertyMap properties = image.getMetadata().getScopeData();
 * PropertyMap selected = PropertySelectionDialog.showDialog(
 *       frame,
 *       "Select Properties to Apply",
 *       properties);
 *
 * if (selected != null) {
 *    // User clicked OK - process selected properties
 * } else {
 *    // User cancelled
 * }
 * }</pre>
 */
public final class PropertySelectionDialog extends JDialog {

   private static final long serialVersionUID = 1L;

   private PropertyMap result_ = null;
   private boolean cancelled_ = true;

   // Master checkbox
   private JCheckBox selectAllCheckBox_;

   // Device checkboxes (device name -> checkbox)
   private final Map<String, JCheckBox> deviceCheckBoxes_ = new LinkedHashMap<>();

   // Property checkboxes (full key -> checkbox)
   private final Map<String, JCheckBox> propertyCheckBoxes_ = new LinkedHashMap<>();

   // Device to property keys mapping
   private final Map<String, List<String>> deviceToKeys_ = new LinkedHashMap<>();

   // Original properties for building result
   private final PropertyMap originalProperties_;

   /**
    * Shows a property selection dialog and returns the selected properties.
    *
    * @param parent     Parent frame (can be null)
    * @param title      Dialog title
    * @param properties PropertyMap with properties to select from
    *                   (keys in "DeviceLabel-PropertyName" format)
    * @return PropertyMap with selected properties, or null if cancelled
    */
   public static PropertyMap showDialog(Frame parent, String title,
                                         PropertyMap properties) {
      if (properties == null || properties.isEmpty()) {
         return properties;
      }

      PropertySelectionDialog dialog = new PropertySelectionDialog(parent, title, properties);
      dialog.setVisible(true);  // Blocks until closed
      return dialog.cancelled_ ? null : dialog.result_;
   }

   private PropertySelectionDialog(Frame parent, String title, PropertyMap properties) {
      super(parent, title, true);  // Modal
      this.originalProperties_ = properties;

      // Group properties by device
      groupPropertiesByDevice(properties);

      // Build UI
      initComponents();

      pack();
      setLocationRelativeTo(parent);
   }

   private void groupPropertiesByDevice(PropertyMap properties) {
      for (String key : properties.keySet()) {
         String[] parts = ScopeDataUtils.parseKey(key);
         if (parts == null) {
            continue;
         }

         String device = parts[0];
         deviceToKeys_.computeIfAbsent(device, k -> new ArrayList<>()).add(key);
      }
   }

   private void initComponents() {
      setLayout(new BorderLayout());

      // Main panel with scroll
      JPanel mainPanel = new JPanel(new MigLayout("fillx, insets 10", "[grow]"));

      // Master "Select All" checkbox
      selectAllCheckBox_ = new JCheckBox("Select All");
      selectAllCheckBox_.setSelected(true);
      selectAllCheckBox_.setFont(selectAllCheckBox_.getFont().deriveFont(Font.BOLD));
      selectAllCheckBox_.addActionListener(e -> onSelectAllChanged());
      mainPanel.add(selectAllCheckBox_, "wrap");
      mainPanel.add(new JSeparator(), "growx, wrap, gaptop 5, gapbottom 10");

      // Device groups
      for (Map.Entry<String, List<String>> entry : deviceToKeys_.entrySet()) {
         String device = entry.getKey();
         List<String> keys = entry.getValue();

         // Device checkbox (bold, acts as group header)
         JCheckBox deviceCB = new JCheckBox(device);
         deviceCB.setSelected(true);
         deviceCB.setFont(deviceCB.getFont().deriveFont(Font.BOLD));
         deviceCB.addActionListener(e -> onDeviceCheckBoxChanged(device));
         deviceCheckBoxes_.put(device, deviceCB);
         mainPanel.add(deviceCB, "wrap, gaptop 5");

         // Property checkboxes (indented)
         for (String key : keys) {
            String[] parts = ScopeDataUtils.parseKey(key);
            String propName = parts[1];
            String value = originalProperties_.getString(key, "");

            JCheckBox propCB = new JCheckBox(propName + " = " + value);
            propCB.setSelected(true);
            propCB.addActionListener(e -> onPropertyCheckBoxChanged(device));
            propertyCheckBoxes_.put(key, propCB);
            mainPanel.add(propCB, "wrap, gapleft 20");
         }
      }

      // Scroll pane for main content
      JScrollPane scrollPane = new JScrollPane(mainPanel);
      scrollPane.setPreferredSize(new Dimension(450, 400));
      scrollPane.getVerticalScrollBar().setUnitIncrement(16);
      add(scrollPane, BorderLayout.CENTER);

      // Button panel
      JPanel buttonPanel = new JPanel(new MigLayout("insets 10", "[grow][][][grow]"));

      JButton okButton = new JButton("OK");
      okButton.addActionListener(e -> onOK());
      buttonPanel.add(okButton, "skip 1, tag ok");

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(e -> onCancel());
      buttonPanel.add(cancelButton, "tag cancel");

      add(buttonPanel, BorderLayout.SOUTH);

      getRootPane().setDefaultButton(okButton);

      // ESC key closes dialog
      getRootPane().registerKeyboardAction(
            e -> onCancel(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
   }

   private void onSelectAllChanged() {
      boolean selected = selectAllCheckBox_.isSelected();

      // Update all device checkboxes
      for (JCheckBox cb : deviceCheckBoxes_.values()) {
         cb.setSelected(selected);
      }

      // Update all property checkboxes
      for (JCheckBox cb : propertyCheckBoxes_.values()) {
         cb.setSelected(selected);
      }
   }

   private void onDeviceCheckBoxChanged(String device) {
      boolean selected = deviceCheckBoxes_.get(device).isSelected();

      // Update all property checkboxes for this device
      for (String key : deviceToKeys_.get(device)) {
         propertyCheckBoxes_.get(key).setSelected(selected);
      }

      updateSelectAllState();
   }

   private void onPropertyCheckBoxChanged(String device) {
      // Update device checkbox based on property states
      List<String> keys = deviceToKeys_.get(device);
      boolean allSelected = true;

      for (String key : keys) {
         if (!propertyCheckBoxes_.get(key).isSelected()) {
            allSelected = false;
            break;
         }
      }

      // Update device checkbox (checked if all selected)
      deviceCheckBoxes_.get(device).setSelected(allSelected);

      updateSelectAllState();
   }

   private void updateSelectAllState() {
      // Check if all properties are selected
      boolean allSelected = true;
      for (JCheckBox cb : propertyCheckBoxes_.values()) {
         if (!cb.isSelected()) {
            allSelected = false;
            break;
         }
      }
      selectAllCheckBox_.setSelected(allSelected);
   }

   private void onOK() {
      // Build result with only selected properties
      PropertyMap.Builder builder = PropertyMaps.builder();

      for (Map.Entry<String, JCheckBox> entry : propertyCheckBoxes_.entrySet()) {
         if (entry.getValue().isSelected()) {
            String key = entry.getKey();
            builder.putString(key, originalProperties_.getString(key, ""));
         }
      }

      result_ = builder.build();
      cancelled_ = false;
      dispose();
   }

   private void onCancel() {
      cancelled_ = true;
      dispose();
   }
}
