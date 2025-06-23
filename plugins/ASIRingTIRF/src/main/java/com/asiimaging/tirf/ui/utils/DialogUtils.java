/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.utils;

import javax.swing.JOptionPane;
import java.awt.Component;

public class DialogUtils {

    /**
     * Return {@code true} if "Yes" is selected or {@code false} if "No" is selected.
     *
     * @param component the parent {@code Component}
     * @param title the title string
     * @param message the message to display
     * @return an int indicating the option selected by the user.
     */
    public static boolean showYesNoDialog(final Component component, final String title, final String message) {
        return JOptionPane.showConfirmDialog(component, message, title, JOptionPane.YES_NO_OPTION) == 0;
    }

}
