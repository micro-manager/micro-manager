package org.micromanager.menus;

import java.util.prefs.Preferences;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

import mmcorej.CMMCore;

import org.micromanager.dialogs.MMAboutDlg;
import org.micromanager.dialogs.RegistrationDlg;
import org.micromanager.MMOptions;
import org.micromanager.MMStudio;
import org.micromanager.MMVersion;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;


/*
 * Responsible for handling the Help Menu and its associated logic.
 */
public class HelpMenu {
   private MMStudio studio_;
   private CMMCore core_;
   private MMOptions options_;

   public HelpMenu(MMStudio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
   }
   
   public void initializeHelpMenu(JMenuBar menuBar, final Preferences prefs) {
      final JMenu helpMenu = GUIUtils.createMenuInMenuBar(menuBar, "Help");
        
      GUIUtils.addMenuItem(helpMenu, "User's Guide", null,
         GUIUtils.makeURLRunnable("http://micro-manager.org/wiki/Micro-Manager_User%27s_Guide")
      );
        
      GUIUtils.addMenuItem(helpMenu, "Configuration Guide", null,
         GUIUtils.makeURLRunnable("http://micro-manager.org/wiki/Micro-Manager_Configuration_Guide")
      );
        
      if (!prefs.getBoolean(RegistrationDlg.REGISTRATION, false)) {
         GUIUtils.addMenuItem(helpMenu, 
            "Register your copy of Micro-Manager...", null,
            new Runnable() {
               @Override
               public void run() {
                  try {
                     RegistrationDlg regDlg = new RegistrationDlg(prefs);
                     regDlg.setVisible(true);
                  } catch (Exception e1) {
                     ReportingUtils.showError(e1);
                  }
               }
            }
         );
      }

      GUIUtils.addMenuItem(helpMenu, "Report Problem...", null,
         new Runnable() {
            @Override
            public void run() {
               org.micromanager.diagnostics.gui.ProblemReportController.start(core_);
            }
         }
      );

      GUIUtils.addMenuItem(helpMenu, "About Micromanager", null,
         new Runnable() {
            @Override
            public void run() {
               MMAboutDlg dlg = new MMAboutDlg();
               String versionInfo = "MM Studio version: " + MMVersion.VERSION_STRING;
               versionInfo += "\n" + core_.getVersionInfo();
               versionInfo += "\n" + core_.getAPIVersionInfo();
               versionInfo += "\nUser: " + core_.getUserId();
               versionInfo += "\nHost: " + core_.getHostName();
               dlg.setVersionInfo(versionInfo);
               dlg.setVisible(true);
            }
         }
      );
      
      menuBar.validate();
   }
}
