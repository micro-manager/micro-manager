/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.data;

import java.net.URL;
import java.util.Objects;
import javax.swing.ImageIcon;

import org.micromanager.Studio;

/**
 * Load icon image resources from Micro-Manager to be used in the user interface.
 */
public final class Icons {

    private static final URL MICROSCOPE_PATH = Studio.class.getResource("/org/micromanager/icons/microscope.gif");
    public static final ImageIcon MICROSCOPE = new ImageIcon(Objects.requireNonNull(MICROSCOPE_PATH));

}
