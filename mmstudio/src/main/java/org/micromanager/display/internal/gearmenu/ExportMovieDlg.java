///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, cweisiger@msg.ucsf.edu, June 2015
//               Nico Stuuran, De. 2022
//
// COPYRIGHT:    University of California, San Francisco, 2006
//               Altos Labs, 2022
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
//
// CVS:          $Id$

package org.micromanager.display.internal.gearmenu;

import com.bulenkov.iconloader.IconLoader;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.ApplicationSkin;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.ImageExporter;
import org.micromanager.internal.utils.FileDialogs;


/**
 * This dialog provides an interface for exporting (a portion of) a dataset
 * to an image sequence, complete with all MicroManager overlays.
 */
public final class ExportMovieDlg extends JDialog {
   private static final Icon ADD_ICON =
         IconLoader.getIcon("/org/micromanager/icons/plus_green.png");
   private static final Icon DELETE_ICON =
         IconLoader.getIcon("/org/micromanager/icons/minus.png");
   private static final String DEFAULT_EXPORT_FORMAT =
         "default format to use for exporting image sequences";
   private static final String DEFAULT_FILENAME_PREFIX =
         "default prefix to use for files when exporting image sequences";
   private static final String JPEG_QUALITY = "JPEG quality";
   private static final String FORMAT_PNG = "PNG";
   private static final String FORMAT_JPEG = "JPEG";
   private static final String FORMAT_AVI = "AVI";
   private static final String FORMAT_IMAGEJ = "ImageJ stack window";
   private static final String FORMAT_SYSTEM_CLIPBOARD = "System Clipboard";
   private static final String[] OUTPUT_FORMATS = {
         FORMAT_PNG, FORMAT_JPEG, FORMAT_AVI, FORMAT_IMAGEJ, FORMAT_SYSTEM_CLIPBOARD
   };
   private static final String DEFAULT_USE_LABEL = "Use Label";
   private static final Boolean USE_LABEL = true;
   private static final String EXPORT_LOCATION = "Export Location";

   /**
    * A set of controls for selecting a range of values for a single axis of
    * the dataset. A recursive structure; each panel can contain a child
    * panel, to represent the nested-loop nature of the export process.
    */
   public static class AxisPanel extends JPanel {
      private final DataProvider store_;
      private JComboBox axisSelector_;
      private JSpinner minSpinner_;
      private SpinnerNumberModel minModel_ = null;
      private JSpinner maxSpinner_;
      private SpinnerNumberModel maxModel_ = null;
      private JButton addButton_;
      private AxisPanel child_;
      private String oldAxis_;
      // Hacky method of coping with action events we don't care about.
      private boolean amInSetAxis_ = false;


      public AxisPanel(DisplayWindow display, final ExportMovieDlg parent) {
         super(new MigLayout("flowx"));
         super.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
         store_ = display.getDataProvider();
         List<String> axes = new ArrayList<>(parent.getNonZeroAxes());
         Collections.sort(axes);
         axisSelector_ = new JComboBox<>(axes.toArray(new String[] {}));
         axisSelector_.addActionListener(e -> {
            String newAxis = (String) axisSelector_.getSelectedItem();
            if (!amInSetAxis_) {
               // this event was directly caused by the user.
               parent.changeAxis(oldAxis_, newAxis);
               setAxis(newAxis);
            }
         });
         minSpinner_ = new JSpinner();
         int maxDim = 1;
         Coords intendedDimensions = store_.getSummaryMetadata().getIntendedDimensions();
         for (String axis : intendedDimensions.getAxes()) {
            if (intendedDimensions.getIndex(axis) > maxDim) {
               maxDim = intendedDimensions.getIndex(axis);
            }
         }
         int om = (int) Math.round(Math.log10(maxDim));
         int minWidth = 30 + (10 * om);
         Dimension minSize = new Dimension(minWidth, 12);
         minSpinner_.setMinimumSize(minSize);
         minSpinner_.addChangeListener(e -> {
            // Ensure that the end point can't go below the start point.
            int newMin = (Integer) minSpinner_.getValue();
            maxModel_.setMinimum(newMin);
         });
         maxSpinner_ = new JSpinner();
         maxSpinner_.setMinimumSize(minSize);
         maxSpinner_.addChangeListener(e -> {
            // Ensure that the start point can't come after the end point.
            int newMax = (Integer) maxSpinner_.getValue();
            minModel_.setMaximum(newMax);
         });

         final AxisPanel localThis = this;
         addButton_ = new JButton("And at each of these...", ADD_ICON);
         addButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               if (addButton_.getText().equals("And at each of these...")) {
                  // Add a panel "under" us.
                  child_ = parent.createAxisPanel();
                  add(child_, "newline, span");
                  addButton_.setText("Delete inner loop");
                  addButton_.setIcon(DELETE_ICON);
                  parent.pack();
               } else {
                  remove(child_);
                  parent.deleteFollowing(localThis);
                  child_ = null;
                  addButton_.setText("And at each of these...");
                  addButton_.setIcon(ADD_ICON);
               }
            }
         });

         super.add(new JLabel("Export along "));
         super.add(axisSelector_);
         super.add(new JLabel(" from "));
         super.add(minSpinner_);
         super.add(new JLabel(" to "));
         super.add(maxSpinner_);
         // Only show the add button if there's an unused axis we can add.
         // HACK: the 1 remaining is us, because we're still in our
         // constructor.
         if (parent.getNumSpareAxes() > 1) {
            super.add(addButton_);
         }
      }

      public void setAxis(String axis) {
         int axisLen = store_.getNextIndex(axis);
         String curAxis = (String) axisSelector_.getSelectedItem();
         if (curAxis.equals(axis) && minModel_ != null) {
            // Already set properly and spinner models exist.
            return;
         }
         amInSetAxis_ = true;
         oldAxis_ = axis;
         if (minModel_ == null) {
            // Create the spinner models now.
            // Remember our indices here are 1-indexed.
            minModel_ = new SpinnerNumberModel(1, 1, axisLen, 1);
            maxModel_ = new SpinnerNumberModel(axisLen, 1, axisLen, 1);
            minSpinner_.setModel(minModel_);
            maxSpinner_.setModel(maxModel_);
         } else {
            // Update their maxima according to the new axis.
            minModel_.setMaximum(axisLen - 1);
            maxModel_.setMaximum(axisLen);
            minModel_.setValue(1);
            maxModel_.setValue(axisLen);
         }
         axisSelector_.setSelectedItem(axis);
         amInSetAxis_ = false;
      }

      public String getAxis() {
         return (String) axisSelector_.getSelectedItem();
      }

      /**
       * Apply our configuration to the provided ImageExporter, and recurse
       * if appropriate to any contained AxisPanel.
       *
       * @param exporter Exporter to be configured
       */
      public void configureExporter(ImageExporter exporter) {
         // Correct for the 1-indexed GUI values, since coords are 0-indexed.
         int minVal = (Integer) (minSpinner_.getValue()) - 1;
         int maxVal = (Integer) (maxSpinner_.getValue()) - 1;
         exporter.loop(getAxis(), minVal, maxVal);
         if (child_ != null) {
            child_.configureExporter(exporter);
         }
      }

      @Override
      public String toString() {
         return "<AxisPanel for axis " + getAxis() + ">";
      }
   }

   private final Studio studio_;
   private final ApplicationSkin skin_;
   private final DisplayWindow display_;
   private final DataProvider provider_;
   private final ArrayList<AxisPanel> axisPanels_;
   private final JPanel contentsPanel_;
   private JComboBox<String> outputFormatSelector_;
   private JLabel prefixLabel_;
   private JTextField prefixText_;
   private JPanel jpegPanel_;
   private JSpinner jpegQualitySpinner_;

   /**
    * Show the dialog.
    *
    * @param studio Usually singleton instance of Studio
    * @param display displayWindow showing the data to be exported
    */
   public ExportMovieDlg(Studio studio, DisplayWindow display) {
      super();
      studio_ = studio;
      skin_ = studio.app().getApplicationSkin();

      // position the export dialog over the center of the display:
      Window dw = display.getWindow();
      final int centerX = dw.getX() + dw.getWidth() / 2;
      final int centerY = dw.getY() + dw.getHeight() / 2;

      display_ = display;
      provider_ = display.getDataProvider();
      axisPanels_ = new ArrayList<>();
      AxisPanel ap = null;
      if (!getNonZeroAxes().isEmpty()) {
         ap = createAxisPanel();
      }
      final AxisPanel axisPanel = ap;

      File file = new File(display.getName());
      String shortName = file.getName();
      super.setTitle("Export Image Series: " + shortName);

      contentsPanel_ = new JPanel(new MigLayout("flowy"));

      JLabel help = new JLabel(
            "<html><body>Export a series of images from your dataset. The images will be "
                  + "exactly as currently<br>drawn on your display, including histogram scaling, "
                  + "zoom, overlays, etc. Note that<br>this does not preserve the raw data, "
                  + "nor any metadata.</body></html>");
      contentsPanel_.add(help, "align center");

      if (isComposite()) {
         contentsPanel_.add(new JLabel(
                     "<html><body>The \"channel\" axis is unavailable as the display "
                           + "is in composite mode.</body></html>"),
               "align center");
      }

      jpegQualitySpinner_ = new JSpinner();
      jpegQualitySpinner_.setModel(new SpinnerNumberModel(getJPEGQuality(), 1, 100, 1));
      final JCheckBox useLabel = new JCheckBox("Use label in filename");

      contentsPanel_.add(new JLabel("Output format: "),
            "split 4, flowx");
      outputFormatSelector_ = new JComboBox<>(OUTPUT_FORMATS);
      outputFormatSelector_.addActionListener(e -> {
         // Show/hide the JPEG quality controls.
         String selection = (String) outputFormatSelector_.getSelectedItem();
         if (selection.equals(FORMAT_JPEG) || selection.equals(FORMAT_AVI)) {
            jpegPanel_.removeAll();
            jpegPanel_.add(new JLabel("JPEG quality(%): "));
            jpegPanel_.add(jpegQualitySpinner_);
         } else {
            jpegPanel_.removeAll();
         }
         boolean usePrefix = selection.equals(FORMAT_PNG) || selection.equals(FORMAT_JPEG)
                  || selection.equals(FORMAT_IMAGEJ);
         prefixLabel_.setText(selection.equals(FORMAT_IMAGEJ)
                  ? "ImageJ Name: " : "Filename prefix: ");
         prefixLabel_.setEnabled(usePrefix);
         prefixLabel_.setVisible(usePrefix);
         prefixText_.setEnabled(usePrefix);
         prefixText_.setVisible(usePrefix);
         useLabel.setVisible(usePrefix);
         if (axisPanel != null) {
            axisPanel.setVisible(!selection.equals(FORMAT_SYSTEM_CLIPBOARD));
         }
         pack();
      });
      contentsPanel_.add(outputFormatSelector_);

      prefixLabel_ = new JLabel("Filename prefix: ");
      contentsPanel_.add(prefixLabel_);

      prefixText_ = new JTextField(getDefaultPrefix(), 20);
      contentsPanel_.add(prefixText_, "grow 0");

      // We want this panel to take up minimal space when it is not needed.
      jpegPanel_ = new JPanel(new MigLayout("flowx, gap 0", "0[]0[]0",
            "0[]0[]0"));
      contentsPanel_.add(jpegPanel_);

      if (getNonZeroAxes().isEmpty()) {
         // No iteration available.
         contentsPanel_.add(
               new JLabel("There is only one image available to export."),
               "align center");
      } else {
         contentsPanel_.add(axisPanel);
      }

      // Dropdown menu with all axes (except channel when in composite mode)
      // show channel note re: composite mode
      // show note about overlays
      // allow selecting range for each axis; "add axis" button which disables
      // when all axes are used
      // for single-axis datasets just auto-fill the one axis
      // Future req: add ability to export to ImageJ as RGB stack

      useLabel.setSelected(studio.profile().getSettings(ExportMovieDlg.class)
            .getBoolean(DEFAULT_USE_LABEL, USE_LABEL));
      ChangeListener changeListener = e -> studio.profile().getSettings(ExportMovieDlg.class)
            .putBoolean(DEFAULT_USE_LABEL, useLabel.isSelected());
      useLabel.addChangeListener(changeListener);

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(e -> dispose());
      JButton exportButton = new JButton("Export");
      exportButton.addActionListener(e -> {
         export(useLabel.isSelected());
         dispose();
      });
      contentsPanel_.add(useLabel, "split 3, flowx, align right");
      contentsPanel_.add(cancelButton);
      contentsPanel_.add(exportButton);

      super.getContentPane().add(contentsPanel_);
      outputFormatSelector_.setSelectedItem(getDefaultExportFormat());
      super.pack();
      super.setLocation(centerX - super.getWidth() / 2,
            centerY - super.getHeight() / 2);
      super.setVisible(true);
   }

   private void export(boolean useLabel) {
      ImageExporter exporter = new DefaultImageExporter(studio_.getLogManager());

      // Set output format.
      String mode = (String) outputFormatSelector_.getSelectedItem();
      ImageExporter.OutputFormat format = ImageExporter.OutputFormat.OUTPUT_PNG;
      String suffix = "png";
      if (mode.contentEquals(FORMAT_JPEG)) {
         format = ImageExporter.OutputFormat.OUTPUT_JPG;
         suffix = "jpg";
      }  else if (mode.contentEquals(FORMAT_AVI)) {
         format = ImageExporter.OutputFormat.OUTPUT_AVI;
         suffix = "avi";
      } else if (mode.contentEquals(FORMAT_IMAGEJ)) {
         format = ImageExporter.OutputFormat.OUTPUT_IMAGEJ;
      } else if (mode.contentEquals(FORMAT_SYSTEM_CLIPBOARD)) {
         format = ImageExporter.OutputFormat.OUTPUT_CLIPBOARD;
      }
      String[] fss = {suffix};
      exporter.setOutputFormat(format);
      exporter.setUseLabel(useLabel);

      // Get save path if relevant.
      String base = System.getProperty("user.home");
      if (provider_ instanceof Datastore) {
         Datastore store = (Datastore) provider_;
         if (store.getSavePath() != null) {
            // Default them to where their data was originally saved.
            base = (new File(store.getSavePath())).getParent();
         }
         base = studio_.profile().getSettings(ExportMovieDlg.class)
               .getString(EXPORT_LOCATION, base);
      }
      File path = new File(base);
      if (mode.equals(FORMAT_JPEG) || mode.equals(FORMAT_PNG)) {
         File outputDir = FileDialogs.promptForFile(this,
               "Export as",
               path,
               true,     // select directories
               false,
               "",
               fss,
               false,
               skin_);
         if (outputDir == null) {
            return;
         }
         try {
            studio_.profile().getSettings(ExportMovieDlg.class)
                  .putString(EXPORT_LOCATION, outputDir.getAbsolutePath());
            exporter.setSaveInfo(outputDir.getAbsolutePath(), prefixText_.getText());
         } catch (IOException e) {
            // This should be impossible -- it indicates the directory does not
            // exist.
            studio_.logs().showError(e, "Unable to save to that directory");
            return;
         }
      } else if (mode.equals(FORMAT_AVI)) {
         File output = FileDialogs.promptForFile(this,
                  "Save AVI as",
                  path,
                  false,     // select directories
                  false,   // load
                  "",      // file description
                  fss,
                  false,
                  skin_);
         if (output == null) {
            return;
         }
         try {
            String directory = output.getParent();
            studio_.profile().getSettings(ExportMovieDlg.class)
                     .putString(EXPORT_LOCATION, directory);
            String fileName = output.getName();
            if (fileName.endsWith(".avi")) {
               fileName = fileName.substring(0, fileName.length() - 4);
            }
            exporter.setSaveInfo(directory, fileName);
         } catch (IOException e) {
            // This should be impossible -- it indicates the directory does not
            // exist.
            studio_.logs().showError(e, "Unable to save to that directory");
            return;
         }
      } else if (mode.equals(FORMAT_IMAGEJ)) {
         exporter.setImageJName(prefixText_.getText());
      }

      exporter.setOutputQuality((Integer) jpegQualitySpinner_.getValue());
      exporter.setDisplay(display_);
      if (mode.contentEquals(FORMAT_SYSTEM_CLIPBOARD)) {
         // only copy the currently displayed image, do not loop
         Coords displayedImage = display_.getDisplayPosition();
         for (String axis : display_.getDataProvider().getAxes()) {
            exporter.loop(axis, displayedImage.getIndex(axis), displayedImage.getIndex(axis));
         }
      } else if (axisPanels_.size() > 0) {
         axisPanels_.get(0).configureExporter(exporter);
      } else {
         exporter.loop(provider_.getAxes().get(0), 0, 0);
      }

      // save defaults to preferences:
      setJPEGQuality((Integer) jpegQualitySpinner_.getValue());
      setDefaultExportFormat(mode);
      setDefaultPrefix(prefixText_.getText());

      try {
         exporter.export();
      } catch (IOException e) {
         studio_.logs().showError(e,
               "Can not export to the selected directory as it would overwrite an "
                     + "existing file. Please choose a different directory.", this);
         return;
      }
      dispose();
   }

   /**
    * Create a row of controls for iterating over an axis. Pick an axis from
    * those not yet being used.
    *
    * @return AxisPanel
    */
   public AxisPanel createAxisPanel() {
      HashSet<String> axes = new HashSet<>(getNonZeroAxes());
      for (AxisPanel panel : axisPanels_) {
         axes.remove(panel.getAxis());
      }
      if (axes.isEmpty()) {
         studio_.logs().logError("Asked to create axis control when no more valid axes remain.");
         return null;
      }
      String axis = (new ArrayList<>(axes)).get(0);

      AxisPanel panel = new AxisPanel(display_, this);
      panel.setAxis(axis);
      axisPanels_.add(panel);
      return panel;
   }

   /**
    * One of our panels is changing from the old axis to the new axis; if the
    * new axis is represented in any other panel, it must be swapped with the
    * old one.
    *
    * @param oldAxis
    * @param newAxis
    */
   public void changeAxis(String oldAxis, String newAxis) {
      for (AxisPanel panel : axisPanels_) {
         if (panel.getAxis().equals(newAxis)) {
            panel.setAxis(oldAxis);
         }
      }
   }

   /**
    * Remove all AxisPanels after the specified panel. Note that the AxisPanel
    * passed into this method is responsible for removing the following panels
    * from the GUI.
    *
    * @param last
    */
   public void deleteFollowing(AxisPanel last) {
      boolean shouldRemove = false;
      HashSet<AxisPanel> defuncts = new HashSet<>();
      for (AxisPanel panel : axisPanels_) {
         if (shouldRemove) {
            defuncts.add(panel);
         }
         if (panel == last) {
            shouldRemove = true;
         }
      }
      // Remove them from the listing.
      for (AxisPanel panel : defuncts) {
         axisPanels_.remove(panel);
      }
      pack();
   }

   /**
    * Returns true if the display mode is composite.
    */
   private boolean isComposite() {
      return display_.getDisplaySettings().getColorMode()
            == DisplaySettings.ColorMode.COMPOSITE;
   }

   /**
    * Return the available axes (that exist in the datastore and have nonzero
    * length).
    *
    * @return
    */
   public ArrayList<String> getNonZeroAxes() {
      ArrayList<String> result = new ArrayList<>();
      for (String axis : provider_.getAxes()) {
         // Channel axis is only available when in non-composite display modes.
         if (provider_.getNextIndex(axis) > 1
               && (!axis.equals(Coords.CHANNEL) || !isComposite())) {
            result.add(axis);
         }
      }
      return result;
   }

   /**
    * Return the number of axes that are not currently being used and that
    * have a nonzero length.
    *
    * @return
    */
   public int getNumSpareAxes() {
      return getNonZeroAxes().size() - axisPanels_.size();
   }

   /**
    * Get the default mode the user wants to use for exporting movies.
    */
   private String getDefaultExportFormat() {
      return studio_.profile()
            .getSettings(ExportMovieDlg.class)
            .getString(DEFAULT_EXPORT_FORMAT, FORMAT_PNG);
   }

   /**
    * Set the default mode to use for exporting movies.
    */
   private void setDefaultExportFormat(String format) {
      studio_.profile().getSettings(ExportMovieDlg.class)
            .putString(DEFAULT_EXPORT_FORMAT, format);
   }

   /**
    * Get the default filename prefix.
    */
   private String getDefaultPrefix() {
      return studio_.profile().getSettings(ExportMovieDlg.class)
            .getString(DEFAULT_FILENAME_PREFIX, "exported");
   }

   /**
    * Set a new default filename prefix.
    */
   private void setDefaultPrefix(String prefix) {
      studio_.profile().getSettings(ExportMovieDlg.class)
            .putString(DEFAULT_FILENAME_PREFIX, prefix);
   }

   private int getJPEGQuality() {
      return studio_.profile().getSettings(ExportMovieDlg.class)
            .getInteger(JPEG_QUALITY, 90);
   }

   private void setJPEGQuality(int quality) {
      studio_.profile().getSettings(ExportMovieDlg.class)
            .putInteger(JPEG_QUALITY, quality);
   }
}

