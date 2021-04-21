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
// CVS:          $Id: FinishPage.java 7241 2011-05-17 23:38:43Z karlh $
//
package org.micromanager.internal.hcwizard;

import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/** The last wizard page. */
public final class FinishPage extends PagePanel {

  private static final long serialVersionUID = 1L;

  private final JButton browseButton_;
  private final JTextField fileNameField_;
  private boolean overwrite_ = false;
  JCheckBox sendCheck_;

  /** Create the panel */
  public FinishPage() {
    super();
    title_ = "Save configuration and exit";
    setLayout(new MigLayout("fillx"));

    JTextArea help = createHelpText("All done! Choose where to save your config file.");
    add(help, "spanx, growx, wrap");

    final JLabel configurationWillBeLabel = new JLabel("Filename:");
    add(configurationWillBeLabel, "span, wrap");

    fileNameField_ = new JTextField();
    add(fileNameField_, "split, width 400");

    browseButton_ = new JButton("Browse...");
    browseButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(final ActionEvent e) {
            browseConfigurationFile();
          }
        });
    add(browseButton_, "wrap");

    sendCheck_ = new JCheckBox("Send configuration to micro-manager.org");
    sendCheck_.setFont(new Font("", Font.PLAIN, 12));
    sendCheck_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent arg0) {
            model_.setSendConfiguration(sendCheck_.isSelected());
          }
        });

    add(sendCheck_, "wrap");

    final JLabel sendConfigExplain =
        new JLabel(
            "Sending us your configuration file will help us study how \u00b5Manager is used.");
    sendConfigExplain.setAutoscrolls(true);
    sendConfigExplain.setFont(sendCheck_.getFont());
    add(sendConfigExplain, "wrap");
  }

  @Override
  public boolean enterPage(boolean next) {
    sendCheck_.setSelected(model_.getSendConfiguration());
    if (model_.creatingNew_) {
      fileNameField_.setText("");
    } else {
      fileNameField_.setText(model_.getFileName());
    }
    return true;
  }

  @Override
  public boolean exitPage(boolean toNext) {
    if (toNext) saveConfiguration();

    return true;
  }

  @Override
  public void refresh() {}

  @Override
  public void loadSettings() {
    // TODO Auto-generated method stub
  }

  @Override
  public void saveSettings() {
    // TODO Auto-generated method stub
  }

  private void browseConfigurationFile() {
    File f =
        FileDialogs.save(
            this.parent_, "Select a configuration file name", FileDialogs.MM_CONFIG_FILE);
    if (f != null) {
      setFilePath(f);
      overwrite_ = true;
    }
  }

  private void setFilePath(File f) {
    String absolutePath = f.getAbsolutePath();
    if (!absolutePath.endsWith(".cfg")) {
      absolutePath += ".cfg";
    }
    fileNameField_.setText(absolutePath);
  }

  private void saveConfiguration() {
    Container ancestor = getTopLevelAncestor();
    Cursor oldc = null;
    if (null != ancestor) {
      oldc = ancestor.getCursor();
      Cursor waitc = new Cursor(Cursor.WAIT_CURSOR);
      ancestor.setCursor(waitc);
    }
    try {
      core_.unloadAllDevices();
      GUIUtils.preventDisplayAdapterChangeExceptions();

      File f = new File(fileNameField_.getText());
      if (f.exists() && !overwrite_) {
        int sel =
            JOptionPane.showConfirmDialog(
                this, "Overwrite " + f.getName() + "?", "File Save", JOptionPane.YES_NO_OPTION);
        if (sel == JOptionPane.NO_OPTION) {
          ReportingUtils.logMessage("All changes are going to be lost!");
          return;
        }
      }
      setFilePath(f);
      model_.removeInvalidConfigurations();
      model_.saveToFile(fileNameField_.getText());
    } catch (MMConfigFileException e) {
      ReportingUtils.showError(e);
    } catch (Exception e) {
      ReportingUtils.showError(e);
    } finally {
      if (null != ancestor) {
        if (null != oldc) ancestor.setCursor(oldc);
      }
    }
  }
}
