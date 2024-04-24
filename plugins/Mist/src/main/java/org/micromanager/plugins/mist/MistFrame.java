/**
 * MistFrame.java
 *
 * <p>This module shows an example of creating a GUI (Graphical User Interface).
 * There are many ways to do this in Java; this particular example uses the
 * MigLayout layout manager, which has extensive documentation online.
 *
 * <p>Nico Stuurman, copyright UCSF, 2012, 2015
 *
 * <p>LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * <p>This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.plugins.mist;

import com.google.common.eventbus.Subscribe;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.text.DefaultFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.alerts.UpdatableAlert;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImageJConverter;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * Code for the GUI of the Mist plugin.
 * Also contains the code assembling the data.
 */
public class MistFrame extends JFrame {

   private final Studio studio_;
   private final DisplayWindow ourWindow_;
   private final DataProvider ourProvider_;
   private final Font arialSmallFont_;
   private final MutablePropertyMapView profileSettings_;
   private static final String DIRNAME = "DirName";
   private final Dimension buttonSize_;
   private final JComboBox<String> saveFormat_;
   private final List<JCheckBox> channelCheckBoxes_;
   private final JCheckBox shouldDisplay_;
   private final JTextField savePath_;
   private final JButton browseButton_;
   private final JButton assembleButton_;
   private static final String DATAVIEWER = "DataViewer";
   private static final String SINGLEPLANE_TIFF_SERIES = "Separate Image Files";
   private static final String MULTIPAGE_TIFF = "Image Stack File";
   private static final String RAM = "RAM only";
   private static final String UNSELECTED_CHANNELS = "UnselectedChannels";


   /**
    * Constructor for the MistFrame class.
    *
    * @param studio The Studio object.
    */
   public MistFrame(Studio studio, DisplayWindow ourWindow) {
      super("Mist Plugin for " + ourWindow.getName());
      studio_ = studio;
      ourWindow_ = ourWindow;
      ourProvider_ = ourWindow.getDataProvider();
      arialSmallFont_ = new Font("Arial", Font.PLAIN, 12);
      buttonSize_ = new Dimension(70, 21);
      profileSettings_ =
              studio_.profile().getSettings(MistFrame.class);
      channelCheckBoxes_ = new ArrayList<>();

      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));

      JLabel title = new JLabel("Stitch data based on Mist plugin locations");
      title.setFont(new Font("Arial", Font.BOLD, 14));
      super.add(title, "span, alignx center, wrap");

      final JLabel mistFileLabel = new JLabel("Mist file: ");
      super.add(mistFileLabel, "span3, split");

      final JTextField locationsField = new JTextField(35);
      locationsField.setFont(arialSmallFont_);
      locationsField.setText(profileSettings_.getString(DIRNAME,
              profileSettings_.getString(DIRNAME,
                      System.getProperty("user.home") + "/img-global-positions-0.txt")));
      locationsField.setHorizontalAlignment(JTextField.LEFT);
      super.add(locationsField);

      DragDropListener dragDropListener = new DragDropListener(locationsField);
      new DropTarget(locationsField, dragDropListener);

      final JButton locationsFieldButton =  makeButton(buttonSize_, arialSmallFont_);
      locationsFieldButton.setText("...");
      locationsFieldButton.addActionListener((ActionEvent evt) -> {
         File f = FileDialogs.openFile(this, "Mist Global Positions File",
                 new FileDialogs.FileType(
                 "Mist Global Positions File",
                 "Mist Global Positions File",
                 locationsField.getText(),
                 false,
                 "txt"));
         if (f != null) {
            locationsField.setText(f.getAbsolutePath());
            profileSettings_.putString(DIRNAME, f.getAbsolutePath());
         }
      });
      super.add(locationsFieldButton, "wrap");

      //dataSetBox_ = new JComboBox<>();
      //setupDataViewerBox(dataSetBox_, DATAVIEWER);
      //super.add(new JLabel("Input Data Set:"));
      //super.add(dataSetBox_, "wrap");

      List<String> axes = ourProvider_.getAxes();
      // Note: MM uses 0-based indices in the code, but 1-based indices
      // for the UI.  To avoid confusion, this storage of the desired
      // limits for each axis is 0-based, and translation to 1-based is made
      // in the UI code
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
         }
         for (int i = 0; i < channelNameList.size(); i++) {
            String channelName = channelNameList.get(i);
            JCheckBox checkBox = new JCheckBox(channelName);
            if (!profileSettings_.getStringList(UNSELECTED_CHANNELS, "").contains(channelName)) {
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


      final Map<String, Integer> mins = new HashMap<>();
      final Map<String, Integer> maxes = new HashMap<>();
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

      super.add(new JSeparator(), "span, growx, wrap");

      super.add(new JLabel("Output Save format: "));
      String[] formats = new String[] {RAM, MULTIPAGE_TIFF, SINGLEPLANE_TIFF_SERIES};
      saveFormat_ = new JComboBox<>(formats);
      saveFormat_.setSelectedItem(
              profileSettings_.getString("format", RAM));
      saveFormat_.addActionListener((ActionEvent e) -> {
         profileSettings_.putString("format", (String) saveFormat_.getSelectedItem());
         updateControls();
      });
      super.add(saveFormat_, "wrap");

      shouldDisplay_ = new JCheckBox("Display saved images in new window");
      shouldDisplay_.setSelected(
              profileSettings_.getBoolean("shouldDisplay", true));
      shouldDisplay_.addActionListener((ActionEvent e) ->
              profileSettings_.putBoolean("shouldDisplay", shouldDisplay_.isSelected()));
      super.add(shouldDisplay_, "span 2, wrap");

      super.add(new JLabel("Save path: "), "span 3, split");
      savePath_ = new JTextField(35);
      savePath_.setText(profileSettings_.getString("savePath", System.getProperty("user.home")));
      savePath_.setEnabled(saveFormat_.getSelectedIndex() != 0);
      savePath_.setHorizontalAlignment(JTextField.LEFT);
      super.add(savePath_);
      browseButton_ = makeButton(buttonSize_, arialSmallFont_);
      browseButton_.setText("...");
      browseButton_.addActionListener((ActionEvent e) -> {
         // Pop up a browse dialog.
         File path = FileDialogs.save(this,
                 "Please choose a name to save to assembled data to",
                 FileDialogs.MM_DATA_SET);
         if (path != null) {
            savePath_.setText(path.getAbsolutePath());
            profileSettings_.putString("savePath", path.getAbsolutePath());
         }
      });
      browseButton_.setEnabled(saveFormat_.getSelectedIndex() != 0);
      super.add(browseButton_, "wrap");

      JButton helpButton = new JButton("Help");
      helpButton.addActionListener((ActionEvent e) ->
            new Thread(org.micromanager.internal.utils.GUIUtils.makeURLRunnable(
                 "https://micro-manager.org/wiki/MistData")).start());
      super.add(helpButton, "span 2, split 2, center");

      assembleButton_ =  new JButton("Assemble");
      assembleButton_.addActionListener((ActionEvent e) -> {
         try {
            profileSettings_.putString(DIRNAME, locationsField.getText());
            profileSettings_.putString("savePath", savePath_.getText());
            Datastore store = null;
            if (saveFormat_.getSelectedItem().equals(RAM)) {
               store = studio_.data().createRAMDatastore();
            } else if (saveFormat_.getSelectedItem().equals(MULTIPAGE_TIFF)) {
               // TODO: read booleans from options
               store = studio_.data().createMultipageTIFFDatastore(savePath_.getText(), true, true);
            } else if (saveFormat_.getSelectedItem().equals(SINGLEPLANE_TIFF_SERIES)) {
               store = studio_.data().createSinglePlaneTIFFSeriesDatastore(savePath_.getText());
            }
            List<String> channelList = new ArrayList<>();
            for (JCheckBox checkBox : channelCheckBoxes) {
               if (checkBox.isSelected()) {
                  channelList.add(checkBox.getText());
               }
            }
            final Datastore finalStore = store;
            Runnable runnable =
                    () -> assembleData(locationsField.getText(),
                            ourWindow_,
                            finalStore,
                            channelList,
                            mins,
                            maxes);
            new Thread(runnable).start();
         } catch (IOException ioe) {
            studio_.logs().showError("Error creating new data store: " + ioe.getMessage());
         }
      });
      super.add(assembleButton_);

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      super.pack();

      studio_.events().registerForEvents(this);
      studio_.displays().registerForEvents(this);
   }

   private JButton makeButton(Dimension buttonSize, Font font) {
      JButton button = new JButton();
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(font);
      button.setMargin(new Insets(0, 0, 0, 0));

      return button;
   }


   private void updateControls() {
      // Toggle availability of the save path controls.
      boolean isRAM = saveFormat_.getSelectedIndex() == 0;
      if (isRAM) {
         // Can't not display RAM data.
         shouldDisplay_.setSelected(true);
      }
      shouldDisplay_.setEnabled(!isRAM);
      savePath_.setEnabled(!isRAM);
      browseButton_.setEnabled(!isRAM);
   }

   @Subscribe
   public void onDataViewerAddedEvent(DataViewerAddedEvent event) {
      //setupDataViewerBox(dataSetBox_, DATAVIEWER);
   }

   @Subscribe
   public void onDataViewerClosing(DataViewerWillCloseEvent event) {
      if (event.getDataViewer().equals(ourWindow_)) {
         dispose();
      }
   }


   /**
    * THis function can take a long, long time to execute.  Make sure not to call it on the EDT.
    *
    * @param locationsFile Output file from the Mist stitching plugin.  "img-global-positions-0"
    * @param dataViewer Micro-Manager dataViewer containing the input data/
    * @param newStore Datastore to write the stitched images to.
    */
   private void assembleData(String locationsFile, final DataViewer dataViewer, Datastore newStore,
                             List<String> channelList, Map<String, Integer> mins, Map<String, Integer> maxes) {
      List<MistGlobalData> mistEntries = new ArrayList<>();

      File mistFile = new File(locationsFile);
      if (!mistFile.exists()) {
         studio_.logs().showError("Mist global positions file not found: "
                 + mistFile.getAbsolutePath());
         return;
      }
      UpdatableAlert updatableAlert = studio_.alerts().postUpdatableAlert("Mist", "Started processing");
      try {
         // parse global position file into MistGlobalData objects
         BufferedReader br
                 = new BufferedReader(new FileReader(mistFile));
         String line;
         while ((line = br.readLine()) != null) {
            if (!line.startsWith("file: ")) {
               continue;
            }
            int fileNameEnd = line.indexOf(';');
            String fileName = line.substring(6, fileNameEnd);
            int siteNr = Integer.parseInt(fileName.substring(fileName.lastIndexOf('_') + 1,
                    fileName.length() - 8));
            int index = fileName.indexOf("MMStack_");
            int end = fileName.substring(index).indexOf("-") + index;
            String well = fileName.substring(index + 8, end);
            // x, y
            int posStart = line.indexOf("position: ") + 11;
            String lineEnd = line.substring(posStart);
            String xy = lineEnd.substring(0, lineEnd.indexOf(')'));
            String[] xySplit = xy.split(",");
            int positionX = Integer.parseInt(xySplit[0]);
            int positionY = Integer.parseInt(xySplit[1].trim());
            // row, column
            int gridStart = line.indexOf("grid: ") + 7;
            lineEnd = line.substring(gridStart);
            String rowCol = lineEnd.substring(0, lineEnd.indexOf(')'));
            String[] rowColSplit = rowCol.split(",");
            int row = Integer.parseInt(rowColSplit[0]);
            int col = Integer.parseInt(rowColSplit[1].trim());
            mistEntries.add(new MistGlobalData(
                    fileName, siteNr, well, positionX, positionY, row, col));
         }
      } catch (IOException e) {
         studio_.logs().showError("Error reading Mist global positions file: " + e.getMessage());
         return;
      } catch (NumberFormatException e) {
         studio_.logs().showError("Error parsing Mist global positions file: " + e.getMessage());
         return;
      }

      if (dataViewer == null) {
         studio_.logs().showError("No Micro-Manager data set selected");
         return;
      }

      SwingUtilities.invokeLater(() -> {
         assembleButton_.setEnabled(false);
      });

      // calculate new image dimensions
      DataProvider dp = dataViewer.getDataProvider();
      int imWidth = dp.getSummaryMetadata().getImageWidth();
      int imHeight = dp.getSummaryMetadata().getImageHeight();
      int maxX = 0;
      int maxY = 0;
      for (MistGlobalData entry : mistEntries) {
         if (entry.getPositionX() > maxX) {
            maxX = entry.getPositionX();
         }
         if (entry.getPositionY() > maxY) {
            maxY = entry.getPositionY();
         }
      }

      int newWidth = maxX + imWidth;
      int newHeight = maxY + imHeight;

      final int newNrC = channelList.size();
      final int newNrT = (maxes.getOrDefault(Coords.T, 0) - mins.getOrDefault(Coords.T, 0)
              + 1);
      final int newNrZ = (maxes.getOrDefault(Coords.Z, 0) - mins.getOrDefault(Coords.Z, 0)
              + 1);
      final int newNrP = dp.getSummaryMetadata().getIntendedDimensions().getP()
              / mistEntries.size();
      int maxNumImages = newNrC * newNrT * newNrZ * newNrP;
      ProgressMonitor monitor = new ProgressMonitor(this,
              "Stitching images...", null, 0, maxNumImages);
      DataViewer newDataViewer = null;
      long startTime = System.currentTimeMillis();
      try {
         // create datastore to hold the result
         Coords dims = dp.getSummaryMetadata().getIntendedDimensions();
         Coords.Builder cb = dims.copyBuilder().c(newNrC).t(newNrT).z(newNrZ).p(newNrP);
         newStore.setSummaryMetadata(dp.getSummaryMetadata().copyBuilder().imageHeight(newHeight)
                 .imageWidth(newWidth).intendedDimensions(cb.build())
                 .build());
         if (profileSettings_.getBoolean("shouldDisplay", true)) {
            newDataViewer = studio_.displays().createDisplay(newStore);
         }
         Coords id = dp.getSummaryMetadata().getIntendedDimensions();
         Coords.Builder intendedDimensionsB = dp.getSummaryMetadata().getIntendedDimensions().copyBuilder();
         for (String axis : new String[] {Coords.C, Coords.P, Coords.T, Coords.C}) {
            if (!id.hasAxis(axis)) {
               intendedDimensionsB.index(axis, 1);
            }
         }
         Coords intendedDimensions = intendedDimensionsB.build();
         Coords.Builder imgCb = studio_.data().coordsBuilder();
         int nrImages = 0;
         int tmpC = -1;
         for (int c = 0; c < intendedDimensions.getC(); c++) {
            if (!channelList.contains(dp.getSummaryMetadata().getChannelNameList().get(c))) {
               break;
            }
            tmpC++;
            for (int t = mins.getOrDefault(Coords.T, 0);
                     t <= maxes.getOrDefault(Coords.T, 0); t++) {
               for (int z = mins.getOrDefault(Coords.Z, 0); z <= maxes.getOrDefault(Coords.Z, 0);
                     z++) {
                  for (int newP = 0; newP < newNrP; newP++) {
                     if (monitor.isCanceled()) {
                        newStore.freeze();
                        if (newDataViewer == null) {
                           newStore.close();
                        }
                        SwingUtilities.invokeLater(() -> {
                           assembleButton_.setEnabled(true);
                        });
                        return;
                     }
                     ImagePlus newImgPlus = IJ.createImage(
                             "Stitched image-" + newP, "16-bit black", newWidth, newHeight, 2);
                     boolean imgAdded = false;
                     for (int p = 0; p < mistEntries.size(); p++) {
                        if (monitor.isCanceled()) {
                           newStore.freeze();
                           if (newDataViewer == null) {
                              newStore.close();
                           }
                           SwingUtilities.invokeLater(() -> {
                              assembleButton_.setEnabled(true);
                           });
                           return;
                        }
                        Image img = null;
                        Coords coords = imgCb.c(c).t(t).z(z)
                                .p(newP * mistEntries.size() + p)
                                .build();
                        if (dp.hasImage(coords)) {
                           img = dp.getImage(coords);
                        }
                        if (img != null) {
                           imgAdded = true;
                           String posName = img.getMetadata().getPositionName("");
                           int siteNr = Integer.parseInt(posName.substring(posName.lastIndexOf('_')
                                   + 1));
                           for (MistGlobalData entry : mistEntries) {
                              if (entry.getSiteNr() == siteNr) {
                                 int x = entry.getPositionX();
                                 int y = entry.getPositionY();
                                 ImageProcessor ip = DefaultImageJConverter.createProcessor(img, false);
                                         //.createProcessor(img);
                                 newImgPlus.getProcessor().insert(ip, x, y);
                              }
                           }
                        }
                     }
                     if (imgAdded) {
                        Image newImg = studio_.data().ij().createImage(newImgPlus.getProcessor(),
                                imgCb.c(tmpC).t(t - mins.getOrDefault(Coords.T, 0))
                                        .z(z - mins.getOrDefault(Coords.Z, 0))
                                        .p(newP).build(),
                                dp.getImage(imgCb.c(c).t(t).z(z).p(newP * mistEntries.size())
                                        .build()).getMetadata().copyBuilderWithNewUUID().build());
                        newStore.putImage(newImg);
                        nrImages++;
                        final int count = nrImages;
                        SwingUtilities.invokeLater(() -> monitor.setProgress(count));
                        int processTime = (int) ((System.currentTimeMillis() - startTime) / 1000);
                        updatableAlert.setText("Processed " + nrImages + " images of " + maxNumImages
                                + " in " + processTime + " seconds");
                     }
                  }
               }
            }
         }
      } catch (IOException e) {
         studio_.logs().showError("Error creating new data store: " + e.getMessage());
      } catch (NullPointerException npe) {
         studio_.logs().showError("Coding error in Mist plugin: " + npe.getMessage());
      } finally {
         try {
            newStore.freeze();
            if (newDataViewer == null) {
               newStore.close();
            }
         } catch (IOException ioe) {
            studio_.logs().logError(ioe, "IO Error while freezing DataProvider");
         }

         SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> monitor.setProgress(maxNumImages));
            assembleButton_.setEnabled(true);
         });
      }
   }
}
