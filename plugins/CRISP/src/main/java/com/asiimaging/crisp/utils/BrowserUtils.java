/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.utils;

import org.micromanager.Studio;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Utility class to open website URLs using the default browser.
 */
public class BrowserUtils {

    /**
     * Opens the default browser and sends the user to the web address given by {@code url}.
     *
     * @param studio Needed to show errors
     * @param url The website URL to open
     */
    public static void openWebsite(final Studio studio, final String url) {
        Objects.requireNonNull(url);
        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.browse(new URI(url));
        } catch (URISyntaxException | IOException e) {
            studio.logs().showError("Could not open the website at the URL:\n" + url + "\nError: " + e);
        }
    }
}
