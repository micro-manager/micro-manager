package org.micromanager.nativegui;

import javax.swing.JFileChooser;
import java.io.File;

public class NativeGUI {
  public static String runMDABrowser(String startDirectory) {
         JFileChooser fc = new JFileChooser();
         fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
         fc.setSelectedFile(new File(startDirectory));
         int retVal = fc.showOpenDialog(null);
         if (retVal == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile().getAbsolutePath();
         } else {
            return "";
        }
  }

}
