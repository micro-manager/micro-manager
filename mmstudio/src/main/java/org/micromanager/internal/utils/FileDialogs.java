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
import java.nio.file.Paths;
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

      public GeneralFileFilter(String fileDescription, final String[] fileSuffixes) {
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
               if (!filter.accept(selectedFile)) {
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
         JFileChooser fc;
         try {
            skin.suspendToMode(SkinMode.DAY);
            fc = new JFileChooser();
            if (selectDirectories) {
               fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            }
            if (startFile != null) {
               try {
                  if (startFile.isDirectory()) {
                     fc.setCurrentDirectory(startFile);
                  } else {
                     fc.setSelectedFile(startFile);
                  }
               } catch (RuntimeException e) {
                  // Some paths blow up inside the chooser's own machinery.
                  // Its state is no longer trustworthy at this point, so start
                  // over with no location rather than failing the whole action.
                  ReportingUtils.logError(e, "Could not use " + startFile
                        + " as file chooser start location");
                  fc = new JFileChooser();
                  if (selectDirectories) {
                     fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                  }
               }
            }
         } finally {
            skin.resume();
         }
         fc.setDialogTitle(title);
         if (fileSuffixes != null) {
            fc.setFileFilter(filter);
         }
         int returnVal;
         try {
            if (load) {
               returnVal = fc.showOpenDialog(parent);
            } else {
               returnVal = fc.showSaveDialog(parent);
            }
         } catch (RuntimeException e) {
            ReportingUtils.showError(e, "Error displaying the file chooser");
            return null;
         }
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
         }
      }
      return selectedFile;
   }

   private static File promptForFile(Window parent, String title,
                                     FileType type, boolean selectDirectories, boolean load,
                                     ApplicationSkin skin) {
      String startFile = getSuggestedFile(type);
      File startDir = safeStartFile(startFile);
      if (startFile != null && startDir != null
            && !startFile.trim().equals(startDir.getPath())) {
         // Repair a bad remembered path now, so that the user recovers without
         // having to delete their profile directory (issue #2232).
         storePath(type, startDir);
      }
      File result = promptForFile(parent, title, startDir, selectDirectories,
            load, type.description, type.suffixes, type.suggestFileOnSave, skin);
      if (result != null) {
         storePath(type, result);
      }
      return result;
   }

   /**
    * Returns true if the given string can be used as a path on this platform.
    *
    * <p>Some strings are accepted by the File constructor but rejected by
    * java.nio.file.  On Windows a trailing space is the common case.  Such a
    * path throws InvalidPathException from deep inside JFileChooser, where it
    * escapes as an unchecked exception (issue #2232).
    *
    * @param path path to check, may be null
    * @return true if the path is usable
    */
   static boolean isUsablePath(String path) {
      if (path == null || path.trim().isEmpty()) {
         return false;
      }
      try {
         Paths.get(path);
         // File -> Path is the round trip that JFileChooser and ShellFolder
         // perform internally.  Exercise it here so that we fail now, where
         // we can recover, rather than inside the chooser, where we cannot.
         new File(path).getAbsoluteFile().toPath();
         return true;
      } catch (RuntimeException e) {
         return false;
      }
   }

   /**
    * Converts a remembered path into a File usable as a chooser start location.
    *
    * <p>If the path is malformed or no longer reachable, walks up to the nearest
    * existing ancestor, and otherwise falls back to the user's home directory.
    * A path that still exists is returned unchanged, so remembered locations
    * behave exactly as before.
    *
    * @param path remembered path, may be null or malformed
    * @return a usable start location, or null to let the chooser choose
    */
   public static File safeStartFile(String path) {
      File candidate = null;
      if (isUsablePath(path)) {
         candidate = new File(path.trim());
      }
      // Walk up to the nearest ancestor that both parses and exists.  The
      // parse check has to be repeated on every hop, since getParentFile() of
      // a malformed path can itself be malformed.
      File walker = candidate;
      while (walker != null) {
         if (walker.exists()) {
            return walker;
         }
         File parent = walker.getParentFile();
         if (parent == null || !isUsablePath(parent.getPath())) {
            break;
         }
         walker = parent;
      }
      if (candidate != null) {
         // Parses, but nothing along the chain exists.  Most often this is an
         // offline network share, which can stall the chooser for a long time,
         // so prefer somewhere known to be reachable.
         ReportingUtils.logMessage("FileDialogs: remembered path is not "
               + "reachable, falling back to home directory: " + path);
      }
      String home = System.getProperty("user.home");
      if (isUsablePath(home)) {
         return new File(home);
      }
      return null;
   }

   /**
    * Remembers the given path as the starting location for this file type.
    *
    * <p>Paths that cannot be used on this platform are rejected rather than
    * stored, since a stored bad path would break every later use of the
    * chooser for this file type (issue #2232).
    *
    * @param type file type whose location should be remembered
    * @param path location to remember
    */
   public static void storePath(FileType type, File path) {
      if (path == null) {
         return;
      }
      String absPath;
      try {
         absPath = path.getAbsolutePath();
      } catch (RuntimeException e) {
         ReportingUtils.logError(e, "Unable to store path for " + type.name);
         return;
      }
      if (!isUsablePath(absPath)) {
         ReportingUtils.logError("Refusing to remember unusable path: " + absPath);
         return;
      }
      UserProfile profile = MMStudio.getInstance().profile();
      type.defaultFileName = absPath;
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
