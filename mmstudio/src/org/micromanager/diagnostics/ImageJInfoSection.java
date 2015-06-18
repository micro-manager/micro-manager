// AUTHOR:       Mark Tsuchida
// COPYRIGHT:    University of California, San Francisco, 2015
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.diagnostics;

import ij.ImageJ;


public class ImageJInfoSection implements SystemInfo.SystemInfoSection {
   @Override
   public String getTitle() { return "ImageJ version information"; }

   @Override
   public String getReport() {
      StringBuilder sb = new StringBuilder();

      sb.append("ImageJ version: ").append(ImageJ.VERSION).append('\n');

      return sb.toString();
   }
}