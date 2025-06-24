/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.panels.tabs;

import com.asiimaging.tirf.model.TIRFControlModel;
import com.asiimaging.tirf.ui.components.Button;
import com.asiimaging.tirf.ui.components.CheckBox;
import com.asiimaging.tirf.ui.components.Label;
import com.asiimaging.tirf.ui.components.Panel;
import com.asiimaging.tirf.ui.components.RadioButton;
import com.asiimaging.tirf.ui.components.Spinner;
import com.asiimaging.tirf.ui.components.TextField;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.Objects;
import javax.swing.JTextField;
import com.asiimaging.tirf.ui.utils.BrowserUtils;
import com.asiimaging.tirf.ui.utils.DialogUtils;
import org.micromanager.data.Datastore;
import org.micromanager.internal.utils.FileDialogs;

public class DataTab extends Panel {

   private Spinner spnScriptDelay;
   private Spinner spnNumImages;
   private Button btnBrowse;
   private TextField txtSaveFileName;
   private JTextField txtSaveDirectory;
   private RadioButton radSaveType;

   private Button btnBrowseStartup;
   private Button btnBrowseShutdown;
   private CheckBox chkUseStartupScript;
   private CheckBox chkUseShutdownScript;
   private JTextField txtStartupScript;
   private JTextField txtShutdownScript;

   private Button btnManual;

   private final TIRFControlModel model;
   private final FileDialogs.FileType fileSelect;
   private final FileDialogs.FileType directorySelect;

   public DataTab(final TIRFControlModel model) {
      this.model = Objects.requireNonNull(model);
      directorySelect = new FileDialogs.FileType(
            "SAVE_DIRECTORY",
            "All Directories",
            "",
            false,
            ""
      );
      fileSelect = new FileDialogs.FileType(
            "BEANSHELL_SCRIPTS",
            "Beanshell Scripts (.bsh)",
            "",
            false,
            "bsh"
      );
      setMigLayout(
            "",
            "[]10[]",
            "[]10[]"
      );
      createUserInterface();
   }

   private void createUserInterface() {
      final Label lblTitle = new Label("Datastore", Font.BOLD, 20);
      final Label lblScripts = new Label("Acquisition Scripts", Font.BOLD, 20);

      final Label lblNumImages = new Label("Number of Images:");
      spnNumImages = Spinner.createIntegerSpinner(model.getNumImages(), 1, Integer.MAX_VALUE, 1);

      final Label lblScriptDelay = new Label("Script Delay [ms]:");
      spnScriptDelay =
            Spinner.createIntegerSpinner(model.getScriptDelay(), 0, Integer.MAX_VALUE, 100);

      final Label lblSaveDirectory = new Label("Save Directory:");
      btnBrowse = new Button("Browse", 80, 20);

      final Label lblSaveType = new Label("Save Format:");
      final String[] buttonNames = {"Single Plane TIFF", "Multi-Page TIFF", "NDTiff"};
      final String selected = getSaveModeText(model.getDatastoreSaveMode());
      radSaveType = new RadioButton(buttonNames, selected);

      txtSaveDirectory = new JTextField();
      txtSaveDirectory.setEditable(false);
      txtSaveDirectory.setColumns(35);
      txtSaveDirectory.setForeground(Color.BLACK);
      txtSaveDirectory.setText(model.getDatastoreSavePath());

      final Label lblSaveFileName = new Label("Save File Name:");
      txtSaveFileName = new TextField(15, model.getDatastoreSaveFileName());
      txtSaveFileName.setForeground(Color.WHITE);

      // script panel
      final Panel scriptPanel = new Panel();
      scriptPanel.setMigLayout(
            "",
            "[]10[]",
            "[]5[]"
      );

      final Label lblStartupScript = new Label("Startup Script:");
      final Label lblShutdownScript = new Label("Shutdown Script:");
      btnBrowseStartup = new Button("Browse", 80, 20);
      btnBrowseShutdown = new Button("Browse", 80, 20);

      txtStartupScript = new JTextField();
      txtStartupScript.setEditable(false);
      txtStartupScript.setColumns(35);
      txtStartupScript.setForeground(Color.BLACK);
      txtStartupScript.setText(model.getStartupScriptPath());

      txtShutdownScript = new JTextField();
      txtShutdownScript.setEditable(false);
      txtShutdownScript.setColumns(35);
      txtShutdownScript.setForeground(Color.BLACK);
      txtShutdownScript.setText(model.getShutdownScriptPath());

      chkUseStartupScript = new CheckBox("", 12, model.getUseStartupScript());
      chkUseShutdownScript = new CheckBox("", 12, model.getUseShutdownScript());

      btnManual = new Button("Manual", 120, 30);

      // add elements to scriptPanel
      scriptPanel.add(lblStartupScript, "");
      scriptPanel.add(txtStartupScript, "");
      scriptPanel.add(btnBrowseStartup, "");
      scriptPanel.add(chkUseStartupScript, "wrap");
      scriptPanel.add(lblShutdownScript, "");
      scriptPanel.add(txtShutdownScript, "");
      scriptPanel.add(btnBrowseShutdown, "");
      scriptPanel.add(chkUseShutdownScript, "wrap");
      scriptPanel.add(lblScriptDelay, "");
      scriptPanel.add(spnScriptDelay, "");

      createEventHandlers();

      // add ui elements to the panel
      add(lblTitle, "wrap");
      add(lblNumImages, "");
      add(spnNumImages, "wrap");
      add(lblSaveType, "");
      add(radSaveType, "wrap");
      add(lblSaveDirectory, "");
      add(txtSaveDirectory, "split 2");
      add(btnBrowse, "wrap, gapleft 8px");
      add(lblSaveFileName, "");
      add(txtSaveFileName, "wrap, gapbottom 60px");
      add(lblScripts, "span 2, wrap");
      add(scriptPanel, "span 4, wrap, gapbottom 20px");
      add(btnManual, "");
   }

   private void createEventHandlers() {
      // the number of images to save during an acquisition
      spnNumImages.registerListener(event -> {
         model.setNumImages(spnNumImages.getInt());
      });

      // select datastore save type
      radSaveType.registerListener(e -> {
         final String text = radSaveType.getSelectedButtonText();
         switch (text) {
            case "Single Plane TIFF":
               model.setDatastoreSaveMode(Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
               break;
            case "Multi Page TIFF":
               model.setDatastoreSaveMode(Datastore.SaveMode.MULTIPAGE_TIFF);
               break;
            case "NDTiff":
               model.setDatastoreSaveMode(Datastore.SaveMode.ND_TIFF);
               break;
         }
      });

      // select datastore save directory
      btnBrowse.registerListener(e -> {
         final File file = FileDialogs.openDir(
               null,
               "Please select the save directory",
               directorySelect
         );
         if (file != null) {
            txtSaveDirectory.setText(file.toString());
            model.setDatastoreSavePath(file.toString());
         }
      });

      // set the save file name
      txtSaveFileName.registerDocumentListener(() -> {
         model.setDatastoreSaveFileName(txtSaveFileName.getText());
      });

      // select the startup script
      btnBrowseStartup.registerListener(event -> {
         final File file = FileDialogs.openFile(
               null,
               "Please select the Beanshell script",
               fileSelect
         );
         if (file != null) {
            txtStartupScript.setText(file.toString());
            model.setStartupScriptPath(file.toString());
         }
      });

      // select the shutdown script
      btnBrowseShutdown.registerListener(event -> {
         final File file = FileDialogs.openFile(
               null,
               "Please select the Beanshell script",
               fileSelect
         );
         if (file != null) {
            txtShutdownScript.setText(file.toString());
            model.setShutdownScriptPath(file.toString());
         }
      });

      // use the startup script
      chkUseStartupScript.registerListener(event -> {
         model.setUseStartupScript(chkUseStartupScript.isSelected());
      });

      // use the shutdown script
      chkUseShutdownScript.registerListener(event -> {
         model.setUseShutdownScript(chkUseShutdownScript.isSelected());
      });

      // startup script delay in milliseconds
      spnScriptDelay.registerListener(event -> {
         model.setScriptDelay(spnScriptDelay.getInt());
      });

      btnManual.registerListener(e -> {
         final boolean result = DialogUtils.showYesNoDialog(btnManual,
               "Open Browser", "Would you like to navigate to the plugin manual?");
         if (result) {
            BrowserUtils.openWebsite(model.getStudio(),
                  "https://www.asiimaging.com/docs/ringtirf");
         }
      });

   }

   private String getSaveModeText(final Datastore.SaveMode saveMode) {
      String result = "";
      switch (saveMode) {
         case SINGLEPLANE_TIFF_SERIES:
            result = "Single Plane TIFF";
            break;
         case MULTIPAGE_TIFF:
            result = "Multi-Page TIFF";
            break;
         case ND_TIFF:
            result = "NDTiff";
            break;
         default:
            break;
      }
      return result;
   }

}
