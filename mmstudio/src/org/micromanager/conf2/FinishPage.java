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
// CVS:          $Id: FinishPage.java 7241 2011-05-17 23:38:43Z karlh $
//
package org.micromanager.conf2;

import java.awt.Container;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
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

    private JButton browseButton_;
    private JTextField fileNameField_;
    private boolean overwrite_ = false;
    JCheckBox sendCheck_;

    /**
     * Create the panel
     */
    public FinishPage(Preferences prefs) {
        super();
        title_ = "Save configuration and exit";
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
        browseButton_.setBounds(450, 31, 100, 23);
        add(browseButton_);

        sendCheck_ = new JCheckBox();
        sendCheck_.setBounds(10, 100, 360, 33);
        sendCheck_.setFont(new Font("", Font.PLAIN, 12));
        sendCheck_.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent arg0) {
              model_.setSendConfiguration(sendCheck_.isSelected());
            }
        });

        sendCheck_.setText("Send configuration to Micro-manager.org");
        add(sendCheck_);

        final JLabel sendConfigExplain = new JLabel();
        sendConfigExplain.setAutoscrolls(true);
        sendConfigExplain.setText("Providing the configuration data will assist securing further project funding.");
        sendConfigExplain.setBounds(14, 127, 500, 21);
        sendConfigExplain.setFont(sendCheck_.getFont());
        add(sendConfigExplain);
        
        //
    }

    public boolean enterPage(boolean next) {
        sendCheck_.setSelected(model_.getSendConfiguration());
        fileNameField_.setText(model_.getFileName());
        return true;
    }


    public boolean exitPage(boolean toNext) {
        if( toNext)
            saveConfiguration();
        
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
                "Select a configuration file name",
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

    private void saveConfiguration() {
         Container ancestor = getTopLevelAncestor();
         Cursor oldc = null;
         if (null != ancestor){
            oldc = ancestor.getCursor();
            Cursor waitc = new Cursor(Cursor.WAIT_CURSOR);
            ancestor.setCursor(waitc);
         }
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
        } finally{
            if (null != ancestor){
               if( null != oldc)
                  ancestor.setCursor(oldc);
            }
        }
    }
}
