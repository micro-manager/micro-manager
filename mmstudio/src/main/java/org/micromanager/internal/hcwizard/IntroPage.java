///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id: IntroPage.java 6334 2011-01-24 23:07:39Z arthur $
//
package org.micromanager.internal.hcwizard;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;

/** The first page of the Configuration Wizard. */
public final class IntroPage extends PagePanel {
  private static final long serialVersionUID = 1L;
  private final ButtonGroup buttonGroup = new ButtonGroup();
  private JTextField filePathField_;
  private boolean initialized_ = false;
  private final JRadioButton modifyRadioButton_;
  private final JRadioButton createNewRadioButton_;
  private JButton browseButton_;

  /** Create the panel */
  public IntroPage() {
    super();
    title_ = "Select the configuration file";

    setLayout(new MigLayout("fillx, flowy"));

    JTextArea help =
        createHelpText(
            "This wizard will walk you through setting up \u00b5Manager to control the hardware in your system.");
    add(help, "growx");

    createNewRadioButton_ = new JRadioButton("Create new configuration");
    createNewRadioButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            model_.reset();
            model_.creatingNew_ = true;
            initialized_ = false;
            filePathField_.setEnabled(false);
            browseButton_.setEnabled(false);
          }
        });
    buttonGroup.add(createNewRadioButton_);
    add(createNewRadioButton_);

    modifyRadioButton_ = new JRadioButton("Modify or explore existing configuration");
    buttonGroup.add(modifyRadioButton_);
    modifyRadioButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            model_.creatingNew_ = false;
            filePathField_.setEnabled(true);
            browseButton_.setEnabled(true);
          }
        });
    add(modifyRadioButton_);

    filePathField_ = new JTextField();
    add(filePathField_, "growx");

    browseButton_ = new JButton("Browse...");
    browseButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            loadConfiguration();
          }
        });
    add(browseButton_, "gapleft push");

    createNewRadioButton_.setSelected(true);
    filePathField_.setEnabled(false);
    browseButton_.setEnabled(false);
  }

  @Override
  public void loadSettings() {
    // load settings
    if (model_ != null) filePathField_.setText(model_.getFileName());

    if (filePathField_.getText().length() > 0) {
      modifyRadioButton_.setSelected(true);
      filePathField_.setEnabled(true);
      browseButton_.setEnabled(true);
    }
  }

  @Override
  public void saveSettings() {
    // save settings
  }

  @Override
  public boolean enterPage(boolean fromNextPage) {
    if (fromNextPage) {
      // if we are returning from the previous page clear everything and start all over
      model_.reset();
      try {
        core_.unloadAllDevices();
      } catch (Exception e) {
        ReportingUtils.showError(e);
      }
      filePathField_.setText(model_.getFileName());
      initialized_ = false;
    }
    return true;
  }

  @Override
  public boolean exitPage(boolean toNextPage) {
    if (modifyRadioButton_.isSelected()
        && (!initialized_ || filePathField_.getText().compareTo(model_.getFileName()) != 0)) {
      Cursor oldCur = getCursor();
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      try {
        model_.loadFromFile(filePathField_.getText());
      } catch (MMConfigFileException e) {
        ReportingUtils.showError(e);
        model_.reset();
        return false;
      } finally {
        setCursor(oldCur);
      }
      initialized_ = true;
    }
    return true;
  }

  @Override
  public void refresh() {}

  private void loadConfiguration() {
    File f = FileDialogs.openFile(parent_, "Choose a config file", FileDialogs.MM_CONFIG_FILE);
    if (f == null) return;
    filePathField_.setText(f.getAbsolutePath());
  }
}
