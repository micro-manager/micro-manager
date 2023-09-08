//////////////////////////////////////////////////////////////////////////////
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

import static org.micromanager.data.internal.DefaultDatastore.getPreferredSaveMode;
import static org.micromanager.data.internal.DefaultDatastore.setPreferredSaveMode;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.DefaultFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ProgressBar;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * User interface of the Duplicator plugin.
 *
 * @author nico
 */
public class DuplicatorPluginFrame extends JDialog {
   private final Studio studio_;
   private final DisplayWindow ourWindow_;
   private final DataProvider ourProvider_;

   // Simple customization of the FileFilter class for choosing the save
   // file format.
   private static class SaveFileFilter extends FileFilter {
      private final String desc_;

      public SaveFileFilter(String desc) {
         desc_ = desc;
      }

      @Override
      public boolean accept(File f) {
         return true;
      }

      @Override
      public String getDescription() {
         return desc_;
      }
   }

   private static final String SINGLEPLANE_TIFF_SERIES = "Separate Image Files";
   private static final String MULTIPAGE_TIFF = "Image Stack File";
   private static final String ND_TIFF = "NDTiff stack";

   // FileFilters for saving.
   private static final FileFilter SINGLEPLANEFILTER = new SaveFileFilter(
         SINGLEPLANE_TIFF_SERIES);
   private static final FileFilter MULTIPAGEFILTER = new SaveFileFilter(
         MULTIPAGE_TIFF);
   private static final FileFilter NDTIFFFILTER = new SaveFileFilter(
         ND_TIFF);

   // Keys for profile settings
   private static final String UNSELECTED_CHANNELS = "UnSelectedChannels";

   /**
    * Constructs the User Interface for data duplication.
    * Lets the user select channels, and ranges of the other axes to duplicate.
    * Will restrict duplication to ROIs set by the user.
    *
    * @param studio The always present studio API
    * @param window Viewer on the data we would like to duplicate
    */
   public DuplicatorPluginFrame(Studio studio, DisplayWindow window) {
      studio_ = studio;
      final DuplicatorPluginFrame ourFrame = this;
      final MutablePropertyMapView settings = studio_.profile().getSettings(this.getClass());
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
      final List<JCheckBox> channelCheckBoxes = new ArrayList<>();
      boolean usesChannels = false;
      for (final String axis : axes) {
         if (axis.equals(Coords.CHANNEL)) {
            usesChannels = true;
            break;
         }
      }
      int nrNoChannelAxes = axes.size();
      ;
      if (usesChannels) {
         nrNoChannelAxes = nrNoChannelAxes - 1;
         List<String> channelNameList = ourProvider_.getSummaryMetadata().getChannelNameList();
         if (channelNameList.size() > 0) {
            super.add(new JLabel(Coords.C));
            ;
         }
         for (int i = 0; i < channelNameList.size(); i++) {
            String channelName = channelNameList.get(i);
            JCheckBox checkBox = new JCheckBox(channelName);
            if (!settings.getStringList(UNSELECTED_CHANNELS, "").contains(channelName)) {
               checkBox.setSelected(true);
            }
            channelCheckBoxes.add(checkBox);
            if (i == 0) {
               if (channelNameList.size() > 1) {
                  super.add(checkBox, "span 3, split " + channelNameList.size());
               } else {
                  super.add(checkBox, "wrap");
               }
            } else if (i == channelNameList.size() - 1) {
               super.add(checkBox, "wrap");
            } else {
               super.add(checkBox);
            }
         }
      }


      if (nrNoChannelAxes > 0) {
         super.add(new JLabel(" "));
         super.add(new JLabel("min"));
         super.add(new JLabel("max"), "wrap");

         for (final String axis : axes) {
            if (axis.equals(Coords.CHANNEL)) {
               continue;
            }
            if (ourProvider_.getNextIndex(axis) > 1) {
               mins.put(axis, 1);
               maxes.put(axis, ourProvider_.getNextIndex(axis));

               super.add(new JLabel(axis));
               SpinnerNumberModel model = new SpinnerNumberModel(1, 1,
                     (int) ourProvider_.getNextIndex(axis), 1);
               mins.put(axis, 0);
               final JSpinner minSpinner = new JSpinner(model);
               JFormattedTextField field =
                     (JFormattedTextField) minSpinner.getEditor().getComponent(0);
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

               model = new SpinnerNumberModel((int) ourProvider_.getNextIndex(axis),
                     1, (int) ourProvider_.getNextIndex(axis), 1);
               maxes.put(axis, ourProvider_.getNextIndex(axis) - 1);
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

      final JCheckBox saveBox = new JCheckBox("Save");
      final JLabel fileField = new JLabel("");
      final JButton chooserButton = new JButton("...");
      final JLabel saveMethod = new JLabel("Memory");
      saveBox.addActionListener(e -> {
         fileField.setEnabled(saveBox.isSelected());
         chooserButton.setEnabled(saveBox.isSelected());
         if (saveBox.isSelected()) {
            chooseDataLocation(ourFrame, fileField, saveMethod);
         } else {
            saveMethod.setText("Memory");
         }
      });
      saveBox.setSelected(false);
      fileField.setEnabled(saveBox.isSelected());
      chooserButton.setEnabled(saveBox.isSelected());
      chooserButton.addActionListener(e -> {
         chooseDataLocation(ourFrame, fileField, saveMethod);
      });

      super.add(saveBox);
      super.add(fileField, "wmin 420, span 2, grow");
      super.add(chooserButton, "wrap");

      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            if (saveBox.isSelected() && fileField.getText().isEmpty()) {
               studio_.logs().showError("Asked to save, but no file path selected", ourFrame);
               return;
            }
            LinkedHashMap<String, Boolean> channels = new LinkedHashMap<>();
            List<String> unselectedChannels = new ArrayList<>();
            for (JCheckBox channelCheckBox : channelCheckBoxes) {
               channels.put(channelCheckBox.getText(), channelCheckBox.isSelected());
               if (!channelCheckBox.isSelected()) {
                  unselectedChannels.add(channelCheckBox.getText());
               }
            }
            settings.putStringList(UNSELECTED_CHANNELS, unselectedChannels);
            Datastore.SaveMode saveMode = null;
            if (saveBox.isSelected()) {
               saveMode = getPreferredSaveMode(studio);
            }
            DuplicatorExecutor de = new DuplicatorExecutor(
                  studio_, ourWindow_, nameField.getText(), mins, maxes, channels,
                  saveMode, fileField.getText());
            cpFrame.dispose();
            final ProgressBar pb = new ProgressBar(ourWindow_.getWindow(),
                  "Duplicating..", 0, 100);
            de.addPropertyChangeListener((PropertyChangeEvent evt) -> {
               if ("progress".equals(evt.getPropertyName())) {
                  pb.setProgress((Integer) evt.getNewValue());
               }
            });
            de.execute();
         }
      });
      super.add(saveMethod, "span 2");
      super.add(okButton, "split 2, tag ok, wmin button");

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

   private void chooseDataLocation(DuplicatorPluginFrame ourFrame, JLabel fileField,
                                   JLabel saveMethod) {
      // Almost verbatim copied from the DefaultDatastore.  It would be nice
      // to refactor, so that we could use that code directly.
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Select file location");
      chooser.setAcceptAllFileFilterUsed(false);
      chooser.addChoosableFileFilter(SINGLEPLANEFILTER);
      chooser.addChoosableFileFilter(MULTIPAGEFILTER);
      chooser.addChoosableFileFilter(NDTIFFFILTER);
      if (Objects.equals(getPreferredSaveMode(studio_),
            Datastore.SaveMode.MULTIPAGE_TIFF)) {
         chooser.setFileFilter(MULTIPAGEFILTER);
      } else if (Objects.equals(getPreferredSaveMode(studio_), Datastore.SaveMode.ND_TIFF)) {
         chooser.setFileFilter(NDTIFFFILTER);
      } else {
         chooser.setFileFilter(SINGLEPLANEFILTER);
      }
      chooser.setSelectedFile(new File(FileDialogs.getSuggestedFile(FileDialogs.MM_DATA_SET)));
      int option = chooser.showDialog(this, "Select");
      if (option != JFileChooser.APPROVE_OPTION) {
         // User cancelled.
         return;
      }

      File file = chooser.getSelectedFile();
      fileField.setText(file.getAbsolutePath());
      FileDialogs.storePath(FileDialogs.MM_DATA_SET, file);

      // Determine the mode the user selected.
      FileFilter filter = chooser.getFileFilter();
      Datastore.SaveMode mode;
      if (filter == SINGLEPLANEFILTER) {
         mode = Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
         saveMethod.setText("Separate Image Files");
      } else if (filter == MULTIPAGEFILTER) {
         mode = Datastore.SaveMode.MULTIPAGE_TIFF;
         saveMethod.setText("Image Stack File");
      } else if (filter == NDTIFFFILTER) {
         mode = Datastore.SaveMode.ND_TIFF;
         saveMethod.setText("NDTiff File");
      } else {
         studio_.logs().showError("Unrecognized file format filter "
               + filter.getDescription(), ourFrame);
         return;
      }
      setPreferredSaveMode(studio_, mode);
   }

}