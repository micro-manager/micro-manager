package org.micromanager.internal.menus;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.internal.MMVersion;
import org.micromanager.internal.dialogs.AboutDlg;
import org.micromanager.internal.utils.GUIUtils;


/**
 * Responsible for handling the Help Menu and its associated logic.
 */
public final class HelpMenu {
   private final Studio studio_;
   private final CMMCore core_;

   /**
    * Creates the Help Menu and its associated logic.
    */
   public HelpMenu(Studio studio, JMenuBar menuBar) {
      studio_ = studio;
      core_ = studio_.core();

      final JMenu helpMenu = GUIUtils.createMenuInMenuBar(menuBar, "Help");

      GUIUtils.addMenuItem(helpMenu, "User's Guide", null,
            GUIUtils.makeURLRunnable("https://micro-manager.org/wiki/Version_2.0_Users_Guide")
      );

      GUIUtils.addMenuItem(helpMenu, "Configuration Guide", null,
            GUIUtils.makeURLRunnable(
                  "http://micro-manager.org/wiki/Micro-Manager_Configuration_Guide")
      );

      GUIUtils.addMenuItem(helpMenu, "Create Problem Report...", null,
            () -> org.micromanager.internal.diagnostics.gui.ProblemReportController.start(core_));

      GUIUtils.addMenuItem(helpMenu, "About Micromanager", null, () -> {
         final AboutDlg dlg = new AboutDlg();

         String hostName;
         try {
            hostName = InetAddress.getLocalHost().getHostName();
         } catch (UnknownHostException e) {
            hostName = "(unknown)";
         }

         String userName = System.getProperty("user.name");
         if (userName == null) {
            userName = "(unknown)";
         }

         String versionInfo = "MM Studio version: " + MMVersion.VERSION_STRING
               + "\n" + core_.getVersionInfo()
               + "\n" + core_.getAPIVersionInfo()
               + "\nUser: " + userName
               + "\nHost: " + hostName;
         dlg.setVersionInfo(versionInfo);
         dlg.setVisible(true);
      });

      menuBar.validate();
   }
}
