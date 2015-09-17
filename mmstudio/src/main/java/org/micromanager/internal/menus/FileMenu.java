package org.micromanager.internal.menus;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.JOptionPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.micromanager.data.Datastore;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;



/**
 * Handles setting up the File menu and its actions.
 */
public class FileMenu {
   private static final String[] CLOSE_OPTIONS = new String[] {
      "Cancel", "Close all", "Close without save prompt"};
   private static final String FILE_HISTORY = "list of recently-viewed files";
   private static final int MAX_HISTORY_SIZE = 15;
   private MMStudio studio_;

   public FileMenu(MMStudio studio) {
      studio_ = studio;
   }

   public void initializeFileMenu(JMenuBar menuBar) {
      // We generate the menu contents on the fly, as the "open recent"
      // menu items are dynamically-generated.
      final JMenu fileMenu = GUIUtils.createMenuInMenuBar(menuBar, "File");
      fileMenu.addMenuListener(new MenuListener() {
         @Override
         public void menuSelected(MenuEvent e) {
            fileMenu.removeAll();
            populateMenu(fileMenu);
            fileMenu.revalidate();
            fileMenu.repaint();
         }

         public void menuDeselected(MenuEvent e) {
         }
         public void menuCanceled(MenuEvent e) {
         }
      });
   }

   /**
    * Fill in the contents of the menu, including the "open recent" dynamic
    * menus.
    */
   private void populateMenu(JMenu fileMenu) {
      GUIUtils.addMenuItem(fileMenu, "Open (Virtual)...", null,
         new Runnable() {
            @Override
            public void run() {
               promptToOpenFile(true);
            }
      });

      fileMenu.add(makeOpenRecentMenu(true));

      GUIUtils.addMenuItem(fileMenu, "Open (RAM)...", null,
         new Runnable() {
            @Override
            public void run() {
               promptToOpenFile(false);
            }
      });

      fileMenu.add(makeOpenRecentMenu(false));

      fileMenu.addSeparator();

      GUIUtils.addMenuItem(fileMenu, "Close all open windows...", null,
         new Runnable() {
            @Override
            public void run() {
               promptToCloseWindows();
            }
      });

      fileMenu.addSeparator();

      GUIUtils.addMenuItem(fileMenu, "Exit", null,
         new Runnable() {
            public void run() {
               studio_.closeSequence(false);
            }
         }
      );
   }

   private void promptToOpenFile(final boolean isVirtual) {
      try {
         Datastore store = studio_.data().promptForDataToLoad(
               MMStudio.getInstance().getFrame(), isVirtual);
         if (store != null) {
            studio_.displays().loadDisplays(store);
            updateFileHistory(store.getSavePath());
         }
      }
      catch (IOException e) {
         ReportingUtils.showError(e, "There was an error when opening data");
      }
   }

   private void promptToCloseWindows() {
      int result = JOptionPane.showOptionDialog(null,
            "Close all open image windows?", "Micro-Manager",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            CLOSE_OPTIONS, CLOSE_OPTIONS[0]);
      if (result == 0) { // cancel
         return;
      }
      if (result == 2 && JOptionPane.showConfirmDialog(null,
               "Are you sure you want to close all image windows without prompting to save?",
               "Micro-Manager", JOptionPane.YES_NO_OPTION) == 1) {
         // Close without prompting, but user backed out.
         return;
      }
      studio_.displays().closeAllDisplayWindows(result == 1);
   }

   private JMenu makeOpenRecentMenu(final boolean isVirtual) {
      JMenu result = new JMenu(String.format("Open Recent (%s)",
               isVirtual ? "Virtual" : "RAM"));

      String[] history = getRecentFiles();
      ArrayList<String> files = new ArrayList<String>(Arrays.asList(history));
      // History is from oldest to newest; we want newest to oldest so new
      // files are on top.
      Collections.reverse(files);
      for (final String path : files) {
         JMenuItem item = new JMenuItem(new File(path).getName());
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               new Thread(new Runnable() {
                  @Override
                  public void run() {
                     try {
                        Datastore store = studio_.data().loadData(path,
                           isVirtual);
                        if (store != null) {
                           studio_.displays().loadDisplays(store);
                        }
                        updateFileHistory(path);
                     }
                     catch (IOException ex) {
                        ReportingUtils.showError(ex, "There was an error when opening data");
                     }
                  }
               }).start();
            }
         });
         result.add(item);
      }
      return result;
   }

   /**
    * Whenever a file is opened, add it to our history of recently-opened
    * files.
    */
   public void updateFileHistory(String newFile) {
      String[] fileHistory = getRecentFiles();
      ArrayList<String> files = new ArrayList<String>(Arrays.asList(fileHistory));
      if (files.contains(newFile)) {
         // It needs to go on the end; remove it from the middle.
         files.remove(newFile);
      }
      // Max length is 15; oldest at the front. Truncate the array as needed.
      while (files.size() > MAX_HISTORY_SIZE - 1) {
         files.remove(files.get(0));
      }
      files.add(newFile);
      setRecentFiles(files.toArray(fileHistory));
   }

   private static String[] getRecentFiles() {
      return DefaultUserProfile.getInstance().getStringArray(
            FileMenu.class, FILE_HISTORY, new String[] {});
   }

   private static void setRecentFiles(String[] files) {
      DefaultUserProfile.getInstance().setStringArray(
            FileMenu.class, FILE_HISTORY, files);
   }
}
