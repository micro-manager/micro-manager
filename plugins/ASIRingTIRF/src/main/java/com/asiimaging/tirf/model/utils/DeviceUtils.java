/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model.utils;

import mmcorej.CMMCore;
import mmcorej.StrVector;

/**
 * Useful methods for helping users debug their problems.
 */
public class DeviceUtils {

   public static void printDeviceProperties(final CMMCore core, final String name)
         throws Exception {
      final StrVector properties = core.getDevicePropertyNames(name);
      for (int i = 0; i < properties.size(); i++) {
         final String property = properties.get(i);
         final String value = core.getProperty(name, property);
         System.out.println("PropName: " + property + ", PropValue: " + value);
      }
   }

   public static void printDeviceProperties(final CMMCore core, final String[] deviceNames)
         throws Exception {
      for (final String name : deviceNames) {
         System.out.println("-------[ " + name + "]-------");
         printDeviceProperties(core, name);
      }
   }
}
