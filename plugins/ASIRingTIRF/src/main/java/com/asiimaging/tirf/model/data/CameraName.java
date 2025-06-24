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

/**
 * The cameras the plugin supports.
 */
public enum CameraName {
   NOT_SUPPORTED("Not Supported"),
   DEMOCAM("DemoCamera-MultiMode"),
   HAMAMATSU_FUSION("C14440-20UP"),
   PRIME_95B("GS144BSI");

   private final String text;

   private static final Map<String, CameraName> stringToEnum =
         Stream.of(values()).collect(Collectors.toMap(Object::toString, e -> e));

   CameraName(final String text) {
      this.text = text;
   }

   @Override
   public String toString() {
      return text;
   }

   public static CameraName fromString(final String symbol) {
      return stringToEnum.getOrDefault(symbol, CameraName.NOT_SUPPORTED);
   }

}