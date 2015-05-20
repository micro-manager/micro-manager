///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal.link;

/**
 * This class is used to inform one DisplayWindow that another, different
 * DisplayWindow has changed a LinkButton's state.
 */
public class RemoteLinkEvent {
   private SettingsLinker linker_;
   private boolean isLinked_;

   public RemoteLinkEvent(SettingsLinker linker, boolean isLinked) {
      linker_ = linker;
      isLinked_ = isLinked;
   }

   public SettingsLinker getLinker() {
      return linker_;
   }

   public boolean getIsLinked() {
      return isLinked_;
   }
}
