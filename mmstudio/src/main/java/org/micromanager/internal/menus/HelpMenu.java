package org.micromanager.internal.menus;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

import mmcorej.CMMCore;

import org.micromanager.internal.dialogs.AboutDlg;
import org.micromanager.internal.dialogs.RegistrationDlg;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.MMVersion;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;


/*
 * Responsible for handling the Help Menu and its associated logic.
 */
public class HelpMenu {
   private MMStudio studio_;
   private CMMCore core_;

   public HelpMenu(MMStudio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
   }
   
   public void initializeHelpMenu(JMenuBar menuBar) {
      final JMenu helpMenu = GUIUtils.createMenuInMenuBar(menuBar, "Help");
        
      GUIUtils.addMenuItem(helpMenu, "User's Guide", null,
         GUIUtils.makeURLRunnable("http://micro-manager.org/wiki/Micro-Manager_User%27s_Guide")
      );
        
      GUIUtils.addMenuItem(helpMenu, "Configuration Guide", null,
         GUIUtils.makeURLRunnable("http://micro-manager.org/wiki/Micro-Manager_Configuration_Guide")
      );
        
      if (!RegistrationDlg.getHaveRegistered()) {
         GUIUtils.addMenuItem(helpMenu, 
            "Register your copy of Micro-Manager...", null,
            new Runnable() {
               @Override
               public void run() {
                  try {
                     RegistrationDlg regDlg = new RegistrationDlg();
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
               org.micromanager.internal.diagnostics.gui.ProblemReportController.start(core_);
            }
         }
      );

      GUIUtils.addMenuItem(helpMenu, "About Micromanager", null,
         new Runnable() {
            @Override
            public void run() {
               AboutDlg dlg = new AboutDlg();
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
