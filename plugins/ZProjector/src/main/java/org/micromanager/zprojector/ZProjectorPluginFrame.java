///////////////////////////////////////////////////////////////////////////////
//FILE:          ZProjectorPluginFrame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Projection plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2019
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

package org.micromanager.zprojector;

import ij.plugin.ZProjector;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.text.DefaultFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * UI of the ZProjector plugin.
 *
 * @author nico
 */
public class ZProjectorPluginFrame extends JDialog {
   private final Studio studio_;
   private final DisplayWindow ourWindow_;
   private final MutablePropertyMapView settings_;
   
   public ZProjectorPluginFrame(Studio studio, DisplayWindow window) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(ZProjectorPlugin.class);
      final ZProjectorPluginFrame cpFrame = this;
      
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
      super.setTitle(ZProjectorPlugin.MENUNAME + shortName);
      
      List<String> axes = ourProvider.getAxes();
      ButtonGroup bg = new ButtonGroup();
      
      // Note: MM uses 0-based indices in the code, but 1-based indices
      // for the UI.  To avoid confusion, this storage of the desired
      // limits for each axis is 0-based, and translation to 1-based is made
      // in the UI code
      final Map<String, Integer> mins = new HashMap<>();
      final Map<String, Integer> maxes = new HashMap<>();

      if (!axes.isEmpty()) {
         super.add(new JLabel(" "));
         super.add(new JLabel("min"));
         super.add(new JLabel("max"), "wrap");

         for (final String axis : axes) {
            if (ourProvider.getNextIndex(axis) > 1) {
               
               // add radio buttons
               JRadioButton axisRB = new JRadioButton(axis);
               axisRB.setActionCommand(axis);
               bg.add(axisRB);               
               super.add(axisRB);
               
               mins.put(axis, 1);
               maxes.put(axis, ourProvider.getNextIndex(axis));

               SpinnerNumberModel model = new SpinnerNumberModel(1, 1,
                       (int) ourProvider.getNextIndex(axis), 1);
               mins.put(axis, 0);
               final JSpinner minSpinner = new JSpinner(model);
               JFormattedTextField field = (JFormattedTextField) minSpinner.getEditor()
                     .getComponent(0);
               DefaultFormatter formatter = (DefaultFormatter) field.getFormatter();
               formatter.setCommitsOnValidEdit(true);
               minSpinner.addChangeListener((ChangeEvent ce) -> {
                  // check to stay below max, this could be annoying at times
                  if ((Integer) minSpinner.getValue() > maxes.get(axis) + 1) {
                     minSpinner.setValue(maxes.get(axis) + 1);
                  }
                  mins.put(axis, (Integer) minSpinner.getValue() - 1);
                  try {
                     Coords coord = ourWindow_.getDisplayedImages().get(0).getCoords();
                     coord = coord.copyBuilder().index(axis, mins.get(axis)).build();
                     ourWindow_.setDisplayPosition(coord);
                  } catch (IOException ioe) {
                     ReportingUtils.logError(ioe, "IOException in DuplicatorPlugin");
                  }
               });
               super.add(minSpinner, "wmin 60");

               model = new SpinnerNumberModel((int) ourProvider.getNextIndex(axis),
                       1, (int) ourProvider.getNextIndex(axis), 1);
               maxes.put(axis, ourProvider.getNextIndex(axis) - 1);
               final JSpinner maxSpinner = new JSpinner(model);
               field = (JFormattedTextField) maxSpinner.getEditor().getComponent(0);
               formatter = (DefaultFormatter) field.getFormatter();
               formatter.setCommitsOnValidEdit(true);
               maxSpinner.addChangeListener((ChangeEvent ce) -> {
                  // check to stay above min
                  if ((Integer) maxSpinner.getValue() < mins.get(axis) + 1) {
                     maxSpinner.setValue(mins.get(axis) + 1);
                  }
                  maxes.put(axis, (Integer) maxSpinner.getValue() - 1);
                  try {
                     Coords coord = ourWindow_.getDisplayedImages().get(0).getCoords();
                     coord = coord.copyBuilder().index(axis, maxes.get(axis)).build();
                     ourWindow_.setDisplayPosition(coord);
                  } catch (IOException ioe) {
                     ReportingUtils.logError(ioe, "IOException in DuplicatorPlugin");
                  }
               });
               super.add(maxSpinner, "wmin 60, wrap");
               
               minSpinner.setEnabled(false);
               maxSpinner.setEnabled(false);
               
               axisRB.addChangeListener((ChangeEvent ce) -> {
                  if (axisRB.isSelected()) {
                     minSpinner.setEnabled(true);
                     maxSpinner.setEnabled(true);
                     settings_.putString(ZProjectorPlugin.AXISKEY, axis);
                  } else {
                     minSpinner.setEnabled(false);
                     maxSpinner.setEnabled(false);
                  }
               });
               
               axisRB.setSelected(axis.equals(settings_.getString(ZProjectorPlugin.AXISKEY, null)));
            }
         }
      }

      if (bg.getSelection() == null) {
         bg.getElements().nextElement().setSelected(true);
      }
      
      // Note: Median and Std.Dev. yield 32-bit images
      // Those would need to be converted to 16-bit to be shown...
      final String[] projectionMethods = new String[] {"Max", "Min", "Avg", "Median",
            "Std.Dev", "Sharpness"};
      final JComboBox<String> methodBox = new JComboBox<>(projectionMethods);
      methodBox.setSelectedItem(settings_.getString(
                                    ZProjectorPlugin.PROJECTION_METHOD, "Max"));
      methodBox.addActionListener((ActionEvent e) -> {
         settings_.putString(ZProjectorPlugin.PROJECTION_METHOD,
                 (String) methodBox.getSelectedItem());
      });
      super.add(new JLabel("method"));
      super.add(methodBox, "span2, grow, wrap");

      super.add(new JLabel("name"));
      final JTextField nameField = new JTextField(shortName);
      super.add(nameField, "span2, grow, wrap");
     
      final JCheckBox saveBox = new JCheckBox("save result");
      saveBox.setSelected(settings_.getBoolean(ZProjectorPlugin.SAVE, false));
      saveBox.addActionListener((ActionEvent e) -> {
         settings_.putBoolean(ZProjectorPlugin.SAVE, saveBox.isSelected());
      });
      super.add(saveBox, "span3, grow, wrap");
      
      JButton okButton = new JButton("OK");
      okButton.addActionListener((ActionEvent ae) -> {
         String axis = bg.getSelection().getActionCommand();
         ZProjectorPluginExecutor zp = new ZProjectorPluginExecutor(studio_, ourWindow_);
         int projectionMethod = ZProjector.MAX_METHOD;
         if (null !=  methodBox.getSelectedItem()) {
            switch ((String) methodBox.getSelectedItem()) {
               case "Max":
                  projectionMethod = ZProjector.MAX_METHOD;
                  break;
               case "Min":
                  projectionMethod = ZProjector.MIN_METHOD;
                  break;
               case "Avg":
                  projectionMethod = ZProjector.AVG_METHOD;
                  break;
               case "Median":
                  projectionMethod = ZProjector.MEDIAN_METHOD;
                  break;
               case "Std.Dev":
                  projectionMethod = ZProjector.SD_METHOD;
                  break;
               case "Sharpness":
                  projectionMethod = ZProjectorPlugin.SHARPNESS_METHOD;
                  break;
               default:
                  break;
            }
         }
         zp.project(saveBox.isSelected(),
                 nameField.getText(), 
                 axis, 
                 mins.get(axis), 
                 maxes.get(axis), 
                 projectionMethod);
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