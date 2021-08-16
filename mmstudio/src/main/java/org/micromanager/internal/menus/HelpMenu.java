package org.micromanager.internal.menus;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.internal.MMVersion;
import org.micromanager.internal.dialogs.AboutDlg;
import org.micromanager.internal.dialogs.RegistrationDlg;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;


/*
 * Responsible for handling the Help Menu and its associated logic.
 */
public final class HelpMenu {
   private final Studio studio_;
   private final CMMCore core_;

   public HelpMenu(Studio studio, JMenuBar menuBar) {
      studio_ = studio;
      core_ = studio_.core();

      final JMenu helpMenu = GUIUtils.createMenuInMenuBar(menuBar, "Help");

      GUIUtils.addMenuItem(helpMenu, "User's Guide", null,
         GUIUtils.makeURLRunnable("https://micro-manager.org/wiki/Version_2.0_Users_Guide")
      );

      GUIUtils.addMenuItem(helpMenu, "Configuration Guide", null,
         GUIUtils.makeURLRunnable("http://micro-manager.org/wiki/Micro-Manager_Configuration_Guide")
      );

      if (!RegistrationDlg.getHaveRegistered(studio_)) {
         GUIUtils.addMenuItem(helpMenu,
            "Register your copy of Micro-Manager...", null, () -> {
               try {
                  RegistrationDlg regDlg = new RegistrationDlg(studio_);
                  regDlg.setVisible(true);
               } catch (Exception e1) {
                  ReportingUtils.showError(e1);
               }
         });
      }

      GUIUtils.addMenuItem(helpMenu, "Create Problem Report...", null, () -> {
         org.micromanager.internal.diagnostics.gui.ProblemReportController.start(core_);
      });

      GUIUtils.addMenuItem(helpMenu, "About Micromanager", null, () -> {
         AboutDlg dlg = new AboutDlg();
         String versionInfo = "MM Studio version: " + MMVersion.VERSION_STRING;
         versionInfo += "\n" + core_.getVersionInfo();
         versionInfo += "\n" + core_.getAPIVersionInfo();
         versionInfo += "\nUser: " + core_.getUserId();
         versionInfo += "\nHost: " + core_.getHostName();
         dlg.setVersionInfo(versionInfo);
         dlg.setVisible(true);
      });

      menuBar.validate();
   }
}
