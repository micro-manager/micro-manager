///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//               Modifications by Arthur Edelstein, Nico Stuurman, Henry Pinkard
//COPYRIGHT:     University of California, San Francisco, 2006-2013
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//CVS:          $Id$
//
package org.micromanager.internal.pluginmanagement;

import java.util.Comparator;
import org.micromanager.MMPlugin;

public final class PluginSorter implements Comparator<MMPlugin> {
   public int compare(MMPlugin a, MMPlugin b) {
      return a.getName().compareTo(b.getName());
   }

   public boolean equals(MMPlugin a, MMPlugin b) {
      return a == b;
   }
}
