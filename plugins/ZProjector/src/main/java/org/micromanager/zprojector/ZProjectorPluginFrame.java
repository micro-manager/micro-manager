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
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DefaultFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author nico
 */
public class ZProjectorPluginFrame extends MMDialog {
   private final Studio studio_;
   private final DisplayWindow ourWindow_;
   private final DataProvider ourProvider_;
   private final MutablePropertyMapView settings_;
   
   public ZProjectorPluginFrame (Studio studio, DisplayWindow window) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(ZProjectorPlugin.class);
      final ZProjectorPluginFrame cpFrame = this;
      
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      
      ourWindow_ = window;
      ourProvider_ = ourWindow_.getDataProvider();

      
      super.setLayout(new MigLayout("flowx, fill, insets 8"));
      File file = new File(window.getName());
      String shortName = file.getName();
      super.setTitle(ZProjectorPlugin.MENUNAME + shortName);
      
      List<String> axes = ourProvider_.getAxes();
      ButtonGroup bg = new ButtonGroup();
      // Note: MM uses 0-based indices in the code, but 1-based indices
      // for the UI.  To avoid confusion, this storage of the desired
      // limits for each axis is 0-based, and translation to 1-based is made
      // in the UI code
      final Map<String, Integer> mins = new HashMap<String, Integer>();
      final Map<String, Integer> maxes = new HashMap<String, Integer>();

      if (axes.size() > 0) {
         super.add(new JLabel(" "));
         super.add(new JLabel("min"));
         super.add(new JLabel("max"), "wrap");

         for (final String axis : axes) {
            if (ourProvider_.getAxisLength(axis) > 1) {
               
               // add radio buttons
               JRadioButton axisRB = new JRadioButton(axis);
               axisRB.setActionCommand(axis);
               bg.add(axisRB);               
               super.add(axisRB);
               
               mins.put(axis, 1);
               maxes.put(axis, ourProvider_.getAxisLength(axis));

               SpinnerNumberModel model = new SpinnerNumberModel(1, 1,
                       (int) ourProvider_.getAxisLength(axis), 1);
               mins.put(axis, 0);
               final JSpinner minSpinner = new JSpinner(model);
               JFormattedTextField field = (JFormattedTextField) minSpinner.getEditor().getComponent(0);
               DefaultFormatter formatter = (DefaultFormatter) field.getFormatter();
               formatter.setCommitsOnValidEdit(true);
               minSpinner.addChangeListener(new ChangeListener() {
                  @Override
                  public void stateChanged(ChangeEvent ce) {
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
               maxSpinner.addChangeListener(new ChangeListener() {
                  @Override
                  public void stateChanged(ChangeEvent ce) {
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
                        ReportingUtils.logError(ioe, "IOException in DuplcatorPlugin");
                     }
                  }
               });
               super.add(maxSpinner, "wmin 60, wrap");
               
               minSpinner.setEnabled(false);
               maxSpinner.setEnabled(false);
               
               axisRB.addChangeListener(new ChangeListener() {
                  @Override
                  public void stateChanged(ChangeEvent ce) {
                     if (axisRB.isSelected()) {
                        minSpinner.setEnabled(true);
                        maxSpinner.setEnabled(true);
                        settings_.putString(ZProjectorPlugin.AXISKEY, axis);
                     } else {                        
                        minSpinner.setEnabled(false);
                        maxSpinner.setEnabled(false);
                     }
                  }
               
               });
               
               axisRB.setSelected(axis.equals(settings_.getString(ZProjectorPlugin.AXISKEY, null)));
            }
         }
      }
      
      if (settings_.getString(ZProjectorPlugin.AXISKEY, null) == null) {
         bg.getElements().nextElement().setSelected(true);
      }    

      super.add(new JLabel("name"));
      final JTextField nameField = new JTextField(shortName);
      super.add(nameField, "span2, grow, wrap");
      
      JButton OKButton = new JButton("OK");
      OKButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            String axis = bg.getSelection().getActionCommand();
            ZProjectorPluginExecutor zp = new ZProjectorPluginExecutor(studio_, ourWindow_);
            zp.project(nameField.getText(), axis, mins.get(axis), maxes.get(axis), ZProjector.MAX_METHOD);
            cpFrame.dispose();
         }
      });
      super.add(OKButton, "span 3, split 2, tag ok, wmin button");
      
      JButton CancelButton = new JButton("Cancel");
      CancelButton.addActionListener(new ActionListener(){
         @Override
         public void actionPerformed(ActionEvent ae) {
            cpFrame.dispose();
         }
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

