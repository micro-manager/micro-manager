package com.asiimaging.example.data;

import javax.swing.ImageIcon;

import org.micromanager.MMStudio;

import com.swtdesigner.SwingResourceManager;

public final class Icons {
    private static final String MICROSCOPE_ICON_PATH = "/org/micromanager/icons/microscope.gif";
    public static final ImageIcon MICROSCOPE_ICON = SwingResourceManager.getIcon(MMStudio.class, MICROSCOPE_ICON_PATH);
}
