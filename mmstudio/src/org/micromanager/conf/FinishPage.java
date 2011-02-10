///////////////////////////////////////////////////////////////////////////////
//FILE:          FinishPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
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
// CVS:          $Id$
//
package org.micromanager.conf;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.FileDialogs;

import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * The last wizard page.
 *
 */
public class FinishPage extends PagePanel {

    private static final long serialVersionUID = 1L;
    private JTextArea logArea_;
    private JButton browseButton_;
    private JTextField fileNameField_;
    private boolean overwrite_ = false;
    JCheckBox sendCheck_;

    /**
     * Create the panel
     */
    public FinishPage(Preferences prefs) {
        super();
        title_ = "Test configuration, save and exit";
        setHelpFileName("conf_finish_page.html");
        prefs_ = prefs;
        setLayout(null);

        final JLabel configurationWillBeLabel = new JLabel();
        configurationWillBeLabel.setText("Configuration file:");
        configurationWillBeLabel.setBounds(14, 11, 123, 21);
        add(configurationWillBeLabel);

        fileNameField_ = new JTextField();
        fileNameField_.setBounds(12, 30, 429, 24);
        add(fileNameField_);

        browseButton_ = new JButton();
        browseButton_.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                browseConfigurationFile();
            }
        });
        browseButton_.setText("Browse...");
        browseButton_.setBounds(443, 31, 100, 23);
        add(browseButton_);

        final JButton saveAndTestButton = new JButton();
        saveAndTestButton.addActionListener(new ActionListener() {

            public void actionPerformed(final ActionEvent e) {
                saveAndTest();
            }
        });
        saveAndTestButton.setText("Save and test the new configuration");
        saveAndTestButton.setBounds(96, 59, 277, 30);
        add(saveAndTestButton);

        logArea_ = new JTextArea();
        logArea_.setLineWrap(true);
        logArea_.setWrapStyleWord(true);
        logArea_.setBorder(new LineBorder(Color.black, 1, false));
        logArea_.setBounds(10, 136, 480, 127);
        add(logArea_);

        sendCheck_ = new JCheckBox();
        sendCheck_.setBounds(10, 100, 280, 23);
        sendCheck_.setFont(new Font("", Font.PLAIN, 10));
        sendCheck_.setSelected(true);
        sendCheck_.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
              model_.setSendConfiguration(sendCheck_.isSelected());
            }
        });

        sendCheck_.setText("Send configuration to Micro-manager.org");


        add(sendCheck_);

        final JLabel sendConfigExplain = new JLabel();
        sendConfigExplain.setAutoscrolls(true);
        sendConfigExplain.setText("The configuration data will aid securing further project funding.");
        sendConfigExplain.setBounds(10, 84, 500, 23);
        sendConfigExplain.setFont(sendCheck_.getFont());
        add(sendConfigExplain);
        



        //
    }

    public boolean enterPage(boolean next) {
        model_.setSendConfiguration(sendCheck_.isSelected());
        fileNameField_.setText(model_.getFileName());
        return true;
    }

    public boolean exitPage(boolean next) {
        // TODO Auto-generated method stub
        return true;
    }

    public void refresh() {
    }

    public void loadSettings() {
        // TODO Auto-generated method stub
    }

    public void saveSettings() {
        // TODO Auto-generated method stub
    }

    private void browseConfigurationFile() {
        String suffixes[] = {".cfg"};
        File f = FileDialogs.save(this.parent_,
                "Create a configuration file",
                MMStudioMainFrame.MM_CONFIG_FILE);
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

    private void saveAndTest() {
        try {
            core_.unloadAllDevices();
            GUIUtils.preventDisplayAdapterChangeExceptions();

            File f = new File(fileNameField_.getText());
            if (f.exists() && !overwrite_) {
                int sel = JOptionPane.showConfirmDialog(this,
                        "Overwrite " + f.getName() + "?",
                        "File Save",
                        JOptionPane.YES_NO_OPTION);
                if (sel == JOptionPane.NO_OPTION) {
                    logArea_.setText("File must be saved in order to test the configuration!");
                    return;
                }
            }
            setFilePath(f);
            model_.removeInvalidConfigurations();
            model_.saveToFile(fileNameField_.getText());
            core_.loadSystemConfiguration(model_.getFileName());
            GUIUtils.preventDisplayAdapterChangeExceptions();
        } catch (MMConfigFileException e) {
            ReportingUtils.showError(e);
        } catch (Exception e) {
            ReportingUtils.showError(e);
            return;
        }

        logArea_.setText("Success!");
    }
}
