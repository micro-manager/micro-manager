///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
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

package org.micromanager.internal.dialogs;

import ij.IJ;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.IntroPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.StartupSettings;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.UserProfileAdmin;

/**
 * Splash screen and introduction dialog. 
 * Opens up at startup and allows selection of the configuration file.
 */
public final class IntroDlg extends JDialog {
   private static final Font DEFAULT_FONT = new Font("Arial", Font.PLAIN, 10);

   private IntroPlugin plugin_ = null;

   private final JTextArea welcomeTextArea_;
   private boolean okFlag_ = true;

   private ProfileSelectionUIController profileController_;
   private ConfigSelectionUIController configController_;
   private UserProfileAdmin admin_;
   private boolean skipProfileSelection_ = false;

   public static String DISCLAIMER_TEXT =
      "This software is distributed free of charge in the hope that it will be useful, " +
      "but WITHOUT ANY WARRANTY; without even the implied warranty " +
      "of merchantability or fitness for a particular purpose. In no event shall the copyright owner or contributors " +
      "be liable for any direct, indirect, incidental, special, examplary, or consequential damages.\n\n" +
      
      "Copyright University of California San Francisco, 2005-2020. All rights reserved.";

   public static String SUPPORT_TEXT =
      "Micro-Manager was funded by grants from the Sandler Foundation and NIH, and is now supported by the CZI.";

   public static String CITATION_TEXT =
      "If you have found this software useful, please cite Micro-Manager in your publications.";


   /**
    * Shows the Splash screen
    * @param studio Instance of MMStudio.  Can not be null.
    * @param versionStr 
    */
   public IntroDlg(Studio studio, String versionStr) {
      super((Window) null); // Passing null here causes the dialog to have an invisible parent frame that shows up in the task bar.
      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      // Select a plugin to use to customize the dialog.
      Map<String, IntroPlugin> plugins = studio == null ?
            Collections.<String, IntroPlugin>emptyMap() :
            studio.plugins().getIntroPlugins();
      if (plugins.size() > 0) {
         // Take the alphabetically first intro plugin we see.
         ArrayList<String> names = new ArrayList<>(plugins.keySet());
         Collections.sort(names);
         plugin_ = plugins.get(names.get(0));
         if (plugins.size() > 1) {
            ReportingUtils.logError("Multiple IntroPlugins found; using " + names.get(0));
         }
      }

      super.setFont(DEFAULT_FONT);
      super.setTitle("Micro-Manager Startup");
      super.setName("Intro");
      super.setResizable(false);
      super.setModal(true);
      super.setUndecorated(true);
      if (!IJ.isMacOSX()) {
        ((JPanel) super.getContentPane()).setBorder(BorderFactory.createLineBorder(Color.GRAY));
      }

      JPanel contentsPanel = new JPanel(new MigLayout(
            new LC().insets("0").gridGap("0", "0")));
      JLabel introImage = new JLabel();
      if (plugin_ == null || plugin_.getSplashImage() == null) {
         introImage.setIcon(new ImageIcon(getClass().getResource(
                 "/org/micromanager/icons/splash.gif")));
      }
      else {
         introImage.setIcon(plugin_.getSplashImage());
      }
      introImage.setBorder(new LineBorder(Color.black, 1, false));
      contentsPanel.add(introImage, new CC().width("pref:pref:pref").pushX().wrap());

      final JLabel microscopeManagerLabel = new JLabel();
      microscopeManagerLabel.setFont(new Font("", Font.BOLD, 12));
      microscopeManagerLabel.setText("Micro-Manager Startup Configuration");
      contentsPanel.add(microscopeManagerLabel, new CC().gapLeft("5").wrap());

      final JLabel versionLabel = new JLabel();
      versionLabel.setFont(DEFAULT_FONT);
      versionLabel.setText("MMStudio Version " + versionStr);
      contentsPanel.add(versionLabel, new CC().gapLeft("5").wrap());

      try {
         admin_ = ((MMStudio) studio).profileAdmin();
         profileController_ = ProfileSelectionUIController.create(studio.app(), admin_);
         StartupSettings startupSettings = StartupSettings.create(
                 admin_.getNonSavingProfile(admin_.getUUIDOfCurrentProfile()));
         skipProfileSelection_ = startupSettings.shouldSkipProfileSelectionAtStartup();
         if (!skipProfileSelection_) {
            JLabel userProfileLabel = new JLabel("User Profile:");
            userProfileLabel.setFont(DEFAULT_FONT);
            contentsPanel.add(userProfileLabel, new CC().gapTop("5").gapLeft("5").wrap());
            contentsPanel.add(profileController_.getUI(), new CC().growX().gapRight("5").wrap());
         } 
         final JLabel loadConfigurationLabel = new JLabel();
         loadConfigurationLabel.setFont(DEFAULT_FONT);
         loadConfigurationLabel.setText("Hardware Configuration File:");
         contentsPanel.add(loadConfigurationLabel, new CC().gapLeft("5").wrap());

         configController_ = ConfigSelectionUIController.create(admin_);
         contentsPanel.add(configController_.getUI(), new CC().growX().gapRight("5").wrap());
      }
      catch (IOException e) {
         ReportingUtils.showError(e,
               "There was an error accessing the user profiles.");
      }

      welcomeTextArea_ = new JTextArea() {
         @Override
         public Insets getInsets() {
            return new Insets(10,10,10,10);
         }
      };
      welcomeTextArea_.setBorder(BorderFactory.createEtchedBorder());
      welcomeTextArea_.setWrapStyleWord(true);
      welcomeTextArea_.setText(DISCLAIMER_TEXT + "\n\n" +
            SUPPORT_TEXT + "\n\n" + CITATION_TEXT);

      welcomeTextArea_.setLineWrap(true);
      welcomeTextArea_.setFont(DEFAULT_FONT);
      welcomeTextArea_.setFocusable(false);
      welcomeTextArea_.setEditable(false);
      contentsPanel.add(welcomeTextArea_, new CC().gapLeft("5").growX().gapRight("5").wrap());

      final JButton okButton = new JButton();
      okButton.setFont(DEFAULT_FONT);
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            okFlag_ = true;
            setVisible(false);
         }
      });
      okButton.setText("OK");
      super.getRootPane().setDefaultButton(okButton);
      okButton.requestFocusInWindow();

      final JButton cancelButton = new JButton();
      cancelButton.setFont(DEFAULT_FONT);
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            okFlag_ = false;
            setVisible(false);
         }
      });
      cancelButton.setText("Cancel");

      contentsPanel.add(okButton,
            new CC().split().alignX("right").sizeGroup("btns").tag("ok"));
      contentsPanel.add(cancelButton,
            new CC().alignX("right").sizeGroup("btns").tag("cancel"));

      super.setLayout(new MigLayout(new LC().fill().insets("0").gridGap("0", "0")));
      super.getContentPane().add(contentsPanel, new CC().grow().push());
      super.pack();

      Dimension winSize = contentsPanel.getPreferredSize();
      GraphicsConfiguration config = GUIUtils.getGraphicsConfigurationContaining(0, 0);
      Rectangle bounds = config.getBounds();
      super.setLocation(bounds.x + bounds.width / 2 - winSize.width / 2,
            bounds.y + bounds.height / 2 - winSize.height / 2);

      super.toFront();
      super.setVisible(true);
   }

   public boolean okChosen() {
      return okFlag_;
   }

   public UUID getSelectedProfileUUID() {
      if (skipProfileSelection_) {
         return admin_.getUUIDOfDefaultProfile();
      }
      return profileController_.getSelectedProfileUUID();
   }

   public String getSelectedConfigFilePath() {
      return configController_.getSelectedConfigFilePath();
   }

   public static void main(String[] args) {
      IntroDlg d = new IntroDlg(null, "VERSION HERE");
      System.out.println("OK = " + d.okChosen());
      System.out.println("Profile = " + d.getSelectedProfileUUID());
      System.out.println("Config = " + d.getSelectedConfigFilePath());
      System.exit(0);
   }
}