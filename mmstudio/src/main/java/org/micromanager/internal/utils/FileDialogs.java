///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com January 2011
//COPYRIGHT:     University of California, San Francisco, 2011
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils;

import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import javax.swing.JFileChooser;
import org.micromanager.ApplicationSkin;
import org.micromanager.ApplicationSkin.SkinMode;
import org.micromanager.UserProfile;
import org.micromanager.internal.MMStudio;

public final class FileDialogs {

   public static class FileType {
      final String name;
      final String[] suffixes;
      final String description;
      final boolean suggestFileOnSave;
      String defaultFileName;

      public FileType(String name, String description, String defaultFileName,
              boolean suggestFileOnSave, String... suffixes) {
         this.name = name;
         this.description = description;
         this.suffixes = suffixes;
         this.defaultFileName = defaultFileName;
         this.suggestFileOnSave = suggestFileOnSave;
      }
   }

   public static final FileType MM_CONFIG_FILE = new FileType("MM_CONFIG_FILE",
         "Micro-Manager Config File", "./MyScope.cfg", true, "cfg");

   public static final FileType MM_DATA_SET = new FileType("MM_DATA_SET",
         "Micro-Manager Image Location", System.getProperty("user.home") + "/Untitled",
         false, (String[]) null);
   
   public static final FileType SCIFIO_DATA = new FileType("SciFIO_Data_Set",
           "Image Location", System.getProperty("user.home") + "/Untitled.tif",
            false, "tif", "jpg", "avi", "png", "jpg");

   public static final FileType ACQ_SETTINGS_FILE = new FileType(
            "ACQ_SETTINGS_FILE",
         "Acquisition settings",
         System.getProperty("user.home") + "/AcqSettings.txt",
         true, "txt");

   private static class GeneralFileFilter
           extends javax.swing.filechooser.FileFilter
           implements java.io.FilenameFilter {
      private final String fileDescription_;
      private final String[] fileSuffixes_;

      public GeneralFileFilter(String fileDescription, final String [] fileSuffixes) {
         fileDescription_ = fileDescription;
         fileSuffixes_ = fileSuffixes;
      }

      @Override
      public boolean accept(File pathname) {
         String name = pathname.getName();
         int n = name.lastIndexOf(".");
         String suffix = name.substring(1 + n).toLowerCase();
         if (fileSuffixes_ == null || fileSuffixes_.length == 0) {
            return true;
         }
         if (!JavaUtils.isMac() && pathname.isDirectory()) {
            return true;
         }
         for (String s : fileSuffixes_) {
            if (s != null && s.toLowerCase().contentEquals(suffix)) {
               return true;
            }
         }
         return false;
      }

      @Override
      public boolean accept(File dir, String name) {
         return accept(new File(dir, name));
      }

      @Override
      public String getDescription() {
         return fileDescription_;
      }
   }

   public static File promptForFile(Window parent,
                    String title,
                    File startFile,
                    boolean selectDirectories, boolean load,
                    final String fileDescription,
                    final String[] fileSuffixes,
                    boolean suggestFileName,
                    ApplicationSkin skin) {
      File selectedFile = null;
      GeneralFileFilter filter = new GeneralFileFilter(fileDescription, fileSuffixes);

      if (JavaUtils.isMac()) {
         if (selectDirectories) {
            // For Mac we only select directories, unfortunately!
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
         }
         int mode = load ? FileDialog.LOAD : FileDialog.SAVE;
         FileDialog fd;
         if (parent instanceof Dialog) {
            fd = new FileDialog((Dialog) parent, title, mode);
         } else if (parent instanceof Frame) {
            fd = new FileDialog((Frame) parent, title, mode);
         } else {
            fd = new FileDialog((Dialog) null, title, mode);
         }
         if (startFile != null) {
            if (startFile.isDirectory()) {
               fd.setDirectory(startFile.getAbsolutePath());
            } else {
               fd.setDirectory(startFile.getParent());
            }
            if (!load && suggestFileName) {
               fd.setFile(startFile.getName());
            }
         }
         if (fileSuffixes != null) {
            fd.setFilenameFilter(filter);
         }
         fd.setVisible(true);
         if (selectDirectories) {
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
         }
         if (fd.getFile() != null) {
            selectedFile = new File(fd.getDirectory() + "/" + fd.getFile());
            if (mode == FileDialog.SAVE) {
               if (! filter.accept(selectedFile)) {
                  selectedFile = new File(selectedFile.getAbsolutePath()
                                          + "." + fileSuffixes[0]);
               }
            }
         }
         fd.dispose();
         
      } else {
         // HACK: we have very limited control over how file choosers are
         // rendered (they're highly platform-specific). Unfortunately on
         // Windows our look-and-feel overrides make choosers look awful in
         // the "night" UI. So we temporarily force the "Daytime" look and
         // feel, without redrawing the entire program UI, just for as long as
         // it takes us to create this chooser.
         skin.suspendToMode(SkinMode.DAY);
         JFileChooser fc = new JFileChooser();
         if (startFile != null) {
            fc.setSelectedFile(startFile);
         }
         skin.resume();
         fc.setDialogTitle(title);
         if (selectDirectories) {
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
         }
         if (fileSuffixes != null) {
            fc.setFileFilter(filter);
         }
         int returnVal;
         if (load) {
            returnVal = fc.showOpenDialog(parent);
         } else {
            returnVal = fc.showSaveDialog(parent);
         }
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
         }
      }
      return selectedFile;
   }

   private static File promptForFile(Window parent, String title,
         FileType type, boolean selectDirectories, boolean load, ApplicationSkin skin) {
      String startFile = getSuggestedFile(type);
      File startDir = null;
      if (startFile != null) {
         startDir = new File(startFile);
      }
      File result = promptForFile(parent, title, startDir, selectDirectories,
            load, type.description, type.suffixes, type.suggestFileOnSave, skin);
      if (result != null) {
         storePath(type, result);
      }
      return result;
   }

   public static void storePath(FileType type, File path) {
      UserProfile profile = MMStudio.getInstance().profile();
      type.defaultFileName = path.getAbsolutePath();
      profile.getSettings(FileDialogs.class).putString(type.name,
              type.defaultFileName);
   }

   public static File openFile(Window parent, String title, FileType type) {
      return promptForFile(parent, title, type, false, true, MMStudio.getInstance().app().skin());
   }

   public static File openDir(Window parent, String title, FileType type) {
      return promptForFile(parent, title, type, true, true, MMStudio.getInstance().app().skin());
   }

   public static File save(Window parent, String title, FileType type) {
      return promptForFile(parent, title, type, false, false, MMStudio.getInstance().app().skin());
   }

   public static String getSuggestedFile(FileType type) {
      return MMStudio.getInstance().profile().getSettings(
            FileDialogs.class).getString(type.name, type.defaultFileName);
   }
}
