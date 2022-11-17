/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.model.data;

import java.net.URL;
import java.util.Objects;

import javax.swing.ImageIcon;

import org.micromanager.Studio;


public final class Icons {

    private static final URL CANCEL_PATH = Studio.class.getResource("/org/micromanager/icons/cancel.png");
    private static final URL CAMERA_PATH = Studio.class.getResource("/org/micromanager/icons/camera.png");
    private static final URL CAMERA_GO_PATH = Studio.class.getResource("/org/micromanager/icons/camera_go.png");
    private static final URL ARROW_UP_PATH = Studio.class.getResource("/org/micromanager/icons/arrow_up.png");
    private static final URL ARROW_DOWN_PATH = Studio.class.getResource("/org/micromanager/icons/arrow_down.png");
    private static final URL ARROW_RIGHT_PATH = Studio.class.getResource("/org/micromanager/icons/arrow_right.png");
    private static final URL MICROSCOPE_PATH = Studio.class.getResource("/org/micromanager/icons/microscope.gif");

    public static final ImageIcon CANCEL = new ImageIcon(Objects.requireNonNull(CANCEL_PATH));
    public static final ImageIcon CAMERA = new ImageIcon(Objects.requireNonNull(CAMERA_PATH));
    public static final ImageIcon CAMERA_GO = new ImageIcon(Objects.requireNonNull(CAMERA_GO_PATH));
    public static final ImageIcon ARROW_UP = new ImageIcon(Objects.requireNonNull(ARROW_UP_PATH));
    public static final ImageIcon ARROW_DOWN = new ImageIcon(Objects.requireNonNull(ARROW_DOWN_PATH));
    public static final ImageIcon ARROW_RIGHT = new ImageIcon(Objects.requireNonNull(ARROW_RIGHT_PATH));
    public static final ImageIcon MICROSCOPE = new ImageIcon(Objects.requireNonNull(MICROSCOPE_PATH));
}