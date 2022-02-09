package com.asiimaging.asi_gamepad;

import org.micromanager.Studio;

import javax.swing.ImageIcon;
import java.net.URL;
import java.util.Objects;

public final class Icons {

    private static final URL MICROSCOPE_PATH = Studio.class.getResource("/org/micromanager/icons/microscope.gif");
    
    public static final ImageIcon MICROSCOPE = new ImageIcon(Objects.requireNonNull(MICROSCOPE_PATH));

}
