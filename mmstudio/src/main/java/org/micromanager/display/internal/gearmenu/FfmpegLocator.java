///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
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

package org.micromanager.display.internal.gearmenu;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.micromanager.Studio;
import org.micromanager.internal.utils.JavaUtils;

/**
 * Utility class for locating, validating, and storing the path to the
 * ffmpeg binary. Used by ExportMovieDlg to support OUTPUT_MOVIE export.
 */
public final class FfmpegLocator {

   static final String FFMPEG_PATH_KEY = "ffmpeg binary path";

   // Prevent instantiation.
   private FfmpegLocator() {
   }

   /**
    * Attempts to find ffmpeg via PATH or the stored profile path.
    * If not found, shows a dialog offering to locate the binary or
    * display installation instructions.
    *
    * @param studio Micro-Manager Studio instance for profile access.
    * @param parent Parent component for dialogs.
    * @return Absolute path to ffmpeg, or null if the user cancelled or
    *         ffmpeg could not be located.
    */
   public static String findOrLocate(Studio studio, Component parent) {
      // 1. Try system PATH.
      String path = findInPath();
      if (path != null) {
         return path;
      }

      // 2. Try the stored profile path.
      path = findInProfile(studio);
      if (path != null) {
         return path;
      }

      // 3. Offer to locate or install.
      return showLocatorDialog(studio, parent);
   }

   /**
    * Searches for ffmpeg on the system PATH.
    *
    * @return Full path to the ffmpeg executable, or null if not found.
    */
   private static String findInPath() {
      if (JavaUtils.isWindows()) {
         // On Windows, walk PATH manually looking for ffmpeg.exe.
         String pathEnv = System.getenv("PATH");
         if (pathEnv == null) {
            return null;
         }
         for (String dir : pathEnv.split(File.pathSeparator)) {
            File candidate = new File(dir, "ffmpeg.exe");
            if (candidate.isFile() && candidate.canExecute()) {
               return candidate.getAbsolutePath();
            }
         }
         return null;
      } else {
         // On Mac/Linux, use `which` to locate ffmpeg.
         try {
            ProcessBuilder pb = new ProcessBuilder("which", "ffmpeg");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader br = new BufferedReader(
                  new InputStreamReader(proc.getInputStream()))) {
               String line = br.readLine();
               proc.waitFor();
               if (line != null && !line.isEmpty()) {
                  File f = new File(line.trim());
                  if (f.isFile() && f.canExecute()) {
                     return f.getAbsolutePath();
                  }
               }
            } catch (InterruptedException e) {
               proc.destroy();
               Thread.currentThread().interrupt();
            }
         } catch (IOException e) {
            // which not available or ffmpeg not found; fall through to return null.
         }
         return null;
      }
   }

   /**
    * Looks up the ffmpeg path stored in the Micro-Manager user profile and
    * validates that the stored file still exists and is executable.
    *
    * @param studio Micro-Manager Studio instance.
    * @return Stored path if valid, null otherwise.
    */
   private static String findInProfile(Studio studio) {
      String path = studio.profile()
            .getSettings(ExportMovieDlg.class)
            .getString(FFMPEG_PATH_KEY, null);
      if (path != null && validateFfmpegPath(path)) {
         return path;
      }
      return null;
   }

   /**
    * Checks that the given path refers to an existing, executable file.
    *
    * @param path Path to check.
    * @return true if path is a valid executable file.
    */
   public static boolean validateFfmpegPath(String path) {
      if (path == null || path.isEmpty()) {
         return false;
      }
      File f = new File(path);
      return f.isFile() && f.canExecute();
   }

   /**
    * Shows a dialog that explains ffmpeg was not found, provides
    * platform-specific installation instructions, and offers to browse
    * for the binary.
    *
    * @param studio Micro-Manager Studio instance.
    * @param parent Parent component.
    * @return Located and validated ffmpeg path, or null if cancelled.
    */
   private static String showLocatorDialog(Studio studio, Component parent) {
      String message = "<html><body>"
            + "<b>ffmpeg was not found.</b><br><br>"
            + "To export movies, ffmpeg must be installed and accessible.<br><br>"
            + buildInstallInstructions()
            + "<br><br>"
            + "You can also use the 'Locate ffmpeg...' button to browse for<br>"
            + "an existing ffmpeg executable."
            + "</body></html>";

      Object[] options = {"Locate ffmpeg...", "Cancel"};
      int choice = JOptionPane.showOptionDialog(
            parent,
            message,
            "ffmpeg Not Found",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]);

      if (choice != JOptionPane.YES_OPTION) {
         return null;
      }

      // Open a file chooser to browse for the ffmpeg executable.
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Locate ffmpeg executable");
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      chooser.setMultiSelectionEnabled(false);

      int result = chooser.showOpenDialog(parent);
      if (result != JFileChooser.APPROVE_OPTION) {
         return null;
      }

      File selected = chooser.getSelectedFile();
      if (!validateFfmpegPath(selected.getAbsolutePath())) {
         JOptionPane.showMessageDialog(parent,
               "The selected file does not appear to be a valid executable.\n"
                     + "Please select the ffmpeg binary.",
               "Invalid File",
               JOptionPane.ERROR_MESSAGE);
         return null;
      }

      saveToProfile(studio, selected.getAbsolutePath());
      return selected.getAbsolutePath();
   }

   /**
    * Builds platform-specific instructions for installing ffmpeg.
    *
    * @return HTML snippet with install instructions.
    */
   private static String buildInstallInstructions() {
      if (JavaUtils.isWindows()) {
         return "On Windows: download ffmpeg from <b>https://ffmpeg.org/download.html</b><br>"
               + "and add it to your PATH, or use 'Locate ffmpeg...' to find the executable.";
      } else if (JavaUtils.isMac()) {
         return "On macOS: install via Homebrew with: <b>brew install ffmpeg</b><br>"
               + "or download from <b>https://ffmpeg.org/download.html</b>.";
      } else {
         return "On Linux: install via your package manager, e.g.:<br>"
               + "&nbsp;&nbsp;<b>sudo apt install ffmpeg</b><br>"
               + "&nbsp;&nbsp;<b>sudo dnf install ffmpeg</b>";
      }
   }

   /**
    * Saves the given ffmpeg path to the Micro-Manager user profile.
    *
    * @param studio Micro-Manager Studio instance.
    * @param path   Absolute path to the ffmpeg binary.
    */
   public static void saveToProfile(Studio studio, String path) {
      studio.profile()
            .getSettings(ExportMovieDlg.class)
            .putString(FFMPEG_PATH_KEY, path);
   }
}
