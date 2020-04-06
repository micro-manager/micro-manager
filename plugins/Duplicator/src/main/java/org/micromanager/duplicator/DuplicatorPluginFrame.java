///////////////////////////////////////////////////////////////////////////////
//FILE:          DuplicatorPluginFrame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Cropper plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2016
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

package org.micromanager.duplicator;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
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
import org.micromanager.display.DisplayWindow;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.ProgressBar;

/**
 *
 * @author nico
 */
public class DuplicatorPluginFrame extends MMDialog {
   private final Studio studio_;
   private final DisplayWindow ourWindow_;
   private final DataProvider ourProvider_;
   
   public DuplicatorPluginFrame (Studio studio, DisplayWindow window) {
      studio_ = studio;
      final DuplicatorPluginFrame cpFrame = this;
      
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      
      ourWindow_ = window;
      ourProvider_ = ourWindow_.getDataProvider();

      
      super.setLayout(new MigLayout("flowx, fill, insets 8"));
      String shortName = ourProvider_.getName();
      super.setTitle(DuplicatorPlugin.MENUNAME + shortName);
      
      List<String> axes = ourProvider_.getAxes();
      // Note: MM uses 0-based indices in the code, but 1-based indices
      // for the UI.  To avoid confusion, this storage of the desired
      // limits for each axis is 0-based, and translation to 1-based is made
      // in the UI code
      final Map<String, Integer> mins = new HashMap<>();
      final Map<String, Integer> maxes = new HashMap<>();

      if (axes.size() > 0) {
         super.add(new JLabel(" "));
         super.add(new JLabel("min"));
         super.add(new JLabel("max"), "wrap");

         for (final String axis : axes) {
            if (ourProvider_.getAxisLength(axis) > 1) {
               mins.put(axis, 1);
               maxes.put(axis, ourProvider_.getAxisLength(axis));

               super.add(new JLabel(axis));
               SpinnerNumberModel model = new SpinnerNumberModel(1, 1,
                       (int) ourProvider_.getAxisLength(axis), 1);
               mins.put(axis, 0);
               final JSpinner minSpinner = new JSpinner(model);
               JFormattedTextField field = (JFormattedTextField) minSpinner.getEditor().getComponent(0);
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
                     studio_.logs().logError(ioe, "IOException in DuplicatorPlugin");
                  }
               });
               super.add(minSpinner, "wmin 60");

               model = new SpinnerNumberModel((int) ourProvider_.getAxisLength(axis),
                       1, (int) ourProvider_.getAxisLength(axis), 1);
               maxes.put(axis, ourProvider_.getAxisLength(axis) - 1);
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
                     studio_.logs().logError(ioe, "IOException in DuplcatorPlugin");
                  }
               });
               super.add(maxSpinner, "wmin 60, wrap");
            }
         }
      }

      super.add(new JLabel("name"));
      final JTextField nameField = new JTextField(shortName);
      super.add(nameField, "span2, grow, wrap");
      
      JButton OKButton = new JButton("OK");
      OKButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            cpFrame.dispose();
            DuplicatorExecutor de = new DuplicatorExecutor(
                    studio_, ourWindow_, nameField.getText(), mins, maxes);
            final ProgressBar pb = new ProgressBar (ourWindow_.getWindow(),
                    "Duplicating..", 0, 100);
            de.addPropertyChangeListener((PropertyChangeEvent evt) -> {
               if ("progress".equals(evt.getPropertyName())) {
                  pb.setProgress((Integer) evt.getNewValue());
                  if ((Integer) evt.getNewValue() == 100 ) {
                     pb.setVisible(false);
                  } 
               }
            });  
            de.execute();
         }
      });
      super.add(OKButton, "span 3, split 2, tag ok, wmin button");
      
      JButton CancelButton = new JButton("Cancel");
      CancelButton.addActionListener((ActionEvent ae) -> {
         cpFrame.dispose();
      });
      super.add(CancelButton, "tag cancel, wrap");     
      
      super.pack();
      
      Window w = ourWindow_.getWindow();
      int xCenter = w.getX() + w.getWidth() / 2;
      int yCenter = w.getY() + w.getHeight() / 2;
      super.setLocation(xCenter - super.getWidth() / 2, 
              yCenter - super.getHeight());
      
      super.setVisible(true);
      
      
   }
     
}