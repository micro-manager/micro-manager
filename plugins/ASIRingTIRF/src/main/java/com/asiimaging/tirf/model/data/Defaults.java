/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model.data;

import org.micromanager.data.Datastore;

import java.io.File;

/**
 * Default values for software settings.
 */
public class Defaults {

   public static final int NUM_IMAGES = 10;
   public static final int NUM_FAST_CIRCLES = 1;
   public static final int STARTUP_SCRIPT_DELAY_MS = 500;
   public static final boolean USE_STARTUP_SCRIPT = false;
   public static final boolean USE_SHUTDOWN_SCRIPT = false;
   public static final String STARTUP_SCRIPT_PATH = "";
   public static final String SHUTDOWN_SCRIPT_PATH = "";

   public static final String DATASTORE_SAVE_DIRECTORY = "C:" + File.separator;
   public static final String DATASTORE_SAVE_FILENAME = "default";
   public static final Datastore.SaveMode DATASTORE_SAVE_MODE =
         Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;

}
