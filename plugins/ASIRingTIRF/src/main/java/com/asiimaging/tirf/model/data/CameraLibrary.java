/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model.data;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


// To add support for a new camera:
// Add the device library the CameraLibrary enum
// Add the camera name to the CameraName enum
// Edit setupProperties() in the Camera class
// Edit getCameraNameString in the Camera class

/**
 * The camera vendor's name.
 */
public enum CameraLibrary {
   NOT_SUPPORTED("Not Supported"),
   DEMOCAMERA("DemoCamera"),
   HAMAMATSUHAM("HamamatsuHam"),
   PVCAM("PVCAM");

   private final String text;

   private static final Map<String, CameraLibrary> stringToEnum =
         Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

   CameraLibrary(final String text) {
      this.text = text;
   }

   @Override
   public String toString() {
      return text;
   }

   public static CameraLibrary fromString(final String symbol) {
      return stringToEnum.getOrDefault(symbol, CameraLibrary.NOT_SUPPORTED);
   }

}