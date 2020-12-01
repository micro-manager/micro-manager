///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
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

package com.asiimaging.crisp.ui;

import java.awt.Component;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

@SuppressWarnings("serial")
public class FileSelect extends JFileChooser {
    
    public static final int FILES_ONLY = 1;
    public static final int DIRECTORIES_ONLY = 2;
    
    public FileSelect(final String dialog, final int fileType) {
        super();
        setDialogTitle(dialog);
        if (fileType == FILES_ONLY) {
            setFileSelectionMode(JFileChooser.FILES_ONLY);
        } else if (fileType == DIRECTORIES_ONLY) {
            setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }
        setMultiSelectionEnabled(false);
    }
    
    public String openDialogBox(final Component component) {
        String filename = "";
        final int result = showOpenDialog(component);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File file = getSelectedFile();
            filename = file.getAbsolutePath();
        } else if (result == JFileChooser.CANCEL_OPTION) {
            filename = "";
        }
        return filename;
    }
    
    public void addFilter(final String description, final String extensions) {
        addChoosableFileFilter(new FileNameExtensionFilter("Focus Curve Data (.csv)", "csv"));
    }
}