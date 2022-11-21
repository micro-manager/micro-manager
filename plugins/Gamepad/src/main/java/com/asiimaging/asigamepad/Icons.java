package com.asiimaging.asigamepad;

import java.net.URL;
import java.util.Objects;
import javax.swing.ImageIcon;
import org.micromanager.Studio;

public final class Icons {

   private static final URL MICROSCOPE_PATH =
         Studio.class.getResource("/org/micromanager/icons/microscope.gif");

   public static final ImageIcon MICROSCOPE =
         new ImageIcon(Objects.requireNonNull(MICROSCOPE_PATH));

}
