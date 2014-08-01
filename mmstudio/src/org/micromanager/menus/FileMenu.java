package org.micromanager.menus;

import java.io.File;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

import org.micromanager.MMStudio;
import org.micromanager.utils.GUIUtils;



/**
 * Handles setting up the File menu and its actions.
 */
public class FileMenu {
   private MMStudio studio_;

   public FileMenu(MMStudio studio) {
      studio_ = studio;
   }

   public void initializeFileMenu(JMenuBar menuBar) {
      JMenu fileMenu = GUIUtils.createMenuInMenuBar(menuBar, "File");

      GUIUtils.addMenuItem(fileMenu, "Open (Virtual)...", null,
         new Runnable() {
            @Override
            public void run() {
               new Thread() {
                  @Override
                  public void run() {
                     studio_.promptForAcquisitionToOpen(false);
                  }
               }.start();
            }
         }
      );

      GUIUtils.addMenuItem(fileMenu, "Open (RAM)...", null,
         new Runnable() {
            @Override
            public void run() {
               new Thread() {
                  @Override
                  public void run() {
                     studio_.promptForAcquisitionToOpen(true);
                  }
               }.start();
            }
         }
      );

      fileMenu.addSeparator();

      GUIUtils.addMenuItem(fileMenu, "Exit", null,
         new Runnable() {
            public void run() {
               studio_.closeSequence(false);
            }
         }
      );
   }
}
