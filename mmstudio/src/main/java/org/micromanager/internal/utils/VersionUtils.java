///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// -----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, cweisiger@msg.ucsf.edu, June 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:          $Id$
//
package org.micromanager.internal.utils;

/** This class handles comparing version strings. */
public final class VersionUtils {
  /**
   * Returns true if the first version string comes from an earlier version than the second version
   * string. NOTE: version strings are assumed to be numerical only, e.g. a version like "10.0.0a"
   * is invalid. Adapted from
   * http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
   */
  public static boolean isOlderVersion(String first, String second) {
    String[] tokens1 = first.split("\\.");
    String[] tokens2 = second.split("\\.");
    int len = Math.min(tokens1.length, tokens2.length);
    for (int i = 0; i < len; ++i) {
      if (!tokens1[i].equals(tokens2[i])) {
        return (Integer.parseInt(tokens1[i]) < Integer.parseInt(tokens2[i]));
      }
    }
    // Made it to the end; if the second string is longer than the first,
    // assume the second is a newer version.
    return (tokens2.length > tokens1.length);
  }
}
