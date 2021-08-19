/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp;

import com.asiimaging.crisp.utils.WindowUtils;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import javax.swing.UIManager;

// The original plugin was created by Nico Stuurman, rewritten by Vikram Kopuri, 
// and then once again rewritten by the current maintainer.
@Plugin(type = MenuPlugin.class)
public class CRISPPlugin implements MenuPlugin, SciJavaPlugin {
    public static final String copyright = "Applied Scientific Instrumentation (ASI), 2014-2021";
    public static final String description = "Interface to control ASIs CRISP Autofocus system.";
    public static final String menuName = "ASI CRISP Control";
    public static final String version = "2.2.0";

    private Studio studio;
    private CRISPFrame frame;

    @Override
    public void setContext(final Studio studio) {
        this.studio = studio;
    }

    @Override
    public String getSubMenu() {
        return "Device Control";
    }

    @Override
    public void onPluginSelected() {
        // only one instance of the plugin can be open
        if (WindowUtils.isOpen(frame)) {
            WindowUtils.close(frame);
        }

        try {
            frame = new CRISPFrame(studio);
            frame.setVisible(true);
            frame.toFront();
        } catch (Exception e) {
            if (studio != null) {
                studio.logs().showError(e);
            }
        }
    }

    @Override
    public String getName() {
        return menuName;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getCopyright() {
        return copyright;
    }

    @Override
    public String getHelpText() {
        return description;
    }

}
