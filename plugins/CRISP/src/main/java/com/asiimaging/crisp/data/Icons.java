/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */

package com.asiimaging.crisp.data;

import java.net.URL;
import java.util.Objects;
import javax.swing.ImageIcon;
import org.micromanager.Studio;

/**
 * A storage class to hold Micro-Manager Icons.
 *
 * <p>This is used to replace the default Java window icon with the Micro-Manager window icon.
 */
public final class Icons {

   // resource location
   private static final URL MICROSCOPE_PATH =
         Studio.class.getResource("/org/micromanager/icons/microscope.gif");

   // convert to ImageIcon
   public static final ImageIcon MICROSCOPE =
         new ImageIcon(Objects.requireNonNull(MICROSCOPE_PATH));

}
