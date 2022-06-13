///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2005
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

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 * File filter class for Open/Save file choosers.
 */
public final class ScriptFileFilter extends FileFilter {
   private final String extBsh;
   private final String description;

   public ScriptFileFilter() {
      super();
      extBsh = new String("bsh");
      description = new String("BeanShell files (*.bsh)");
   }

   @Override
   public boolean accept(File f) {
      if (f.isDirectory()) {
         return true;
      }

      if (extBsh.equals(getExtension(f))) {
         return true;
      }
      return false;
   }

   public String getDescription() {
      return description;
   }

   private String getExtension(File f) {
      String ext = null;
      String s = f.getName();
      int i = s.lastIndexOf('.');

      if (i > 0 && i < s.length() - 1) {
         ext = s.substring(i + 1).toLowerCase();
      }
      return ext;
   }
}
