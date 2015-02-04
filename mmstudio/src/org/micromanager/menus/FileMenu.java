package org.micromanager.menus;


import com.google.common.collect.EvictingQueue;
import java.io.File;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

import org.micromanager.MMStudio;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;



/**
 * Handles setting up the File menu and its actions.
 */
public class FileMenu {
   private final MMStudio studio_;
   private final JMenu openRecentRamMenu_;
   private final EvictingQueue<File> recentFiles_;
   private final Preferences prefs_;
   private final int RECENTFILESSIZE = 8;
   private final String PREFKEYBASE = "file";

   public FileMenu(MMStudio studio) {
      studio_ = studio;
      openRecentRamMenu_ = new JMenu("Open Recent (RAM)...");
      recentFiles_ =  EvictingQueue.create(RECENTFILESSIZE);
      prefs_ = Preferences.userNodeForPackage(getClass());
      restoreFilesFromPref(prefs_, PREFKEYBASE, recentFiles_);
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
                     File f = studio_.promptForAcquisitionToOpen(true);
                     recentFiles_.add(f);
                     writeFilesToPref(prefs_, PREFKEYBASE, recentFiles_);
                     makeRecentFileRamMenu(openRecentRamMenu_, recentFiles_);
                  }
               }.start();
            }
         }
      );
      
      makeRecentFileRamMenu(openRecentRamMenu_, recentFiles_);
      
      fileMenu.add(openRecentRamMenu_);

      fileMenu.addSeparator();

      GUIUtils.addMenuItem(fileMenu, "Exit", null,
         new Runnable() {
            @Override
            public void run() {
               studio_.closeSequence(false);
            }
         }
      );
   }
   
   
   private void restoreFilesFromPref(Preferences prefs, String keyBase, 
           EvictingQueue<File> fileQueue) {
      final String notFound = "";
      boolean hasKey = true;
      int i = 0;
      while (hasKey) {
         String key = keyBase + i;
         String filePath = prefs.get(key, notFound);
         if (filePath.equals(notFound)) {
            hasKey = false;
         } else {
            fileQueue.add(new File (filePath));
            i++;
         }
      }
   }
   
   private static void writeFilesToPref(Preferences prefs, String keyBase, 
           EvictingQueue<File> fileQueue) {
      try {
         prefs.clear();
      } catch (BackingStoreException ex) {
         // TODO: Complain?
      }
      int i = 0;
      for (File f : fileQueue.toArray(new File[fileQueue.size()])) {
         String path = f.getParent();
         if (f.isDirectory()) {
            path = f.getAbsolutePath();
         }
         prefs.put(keyBase + i, path);
         i++;
      }
   }
   
   private void makeRecentFileRamMenu(JMenu recentFileMenu, 
           EvictingQueue<File> fileQueue) {
      recentFileMenu.removeAll();
      for (final File f : fileQueue.toArray(new File[fileQueue.size()])) {
         String p = f.getParent();
         if (f.isDirectory()) {
            p = f.getAbsolutePath();
         }
         final String path = p;
         f.getName();
         GUIUtils.addMenuItem(recentFileMenu, f.getName(), null,
         new Runnable() {
            @Override
            public void run() {
               new Thread() {
                  @Override
                  public void run() {
                     try {
                        studio_.openAcquisitionData(path, true, true);
                     } catch (MMScriptException ex) {
                        ReportingUtils.showError("Failed to open file: " + 
                                f.getName()); 
                     }
                  }
               }.start();
            }
         }
      );
      }
   }
   
   
   
}
