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
 * This event is published when a LinkButton is created, so that its Linker
 * can be tracked by the DisplayGroupManager.
 */
public class LinkButtonCreatedEvent {
   private LinkButton button_;
   private SettingsLinker linker_;

   public LinkButtonCreatedEvent(LinkButton button, SettingsLinker linker) {
      button_ = button;
      linker_ = linker;
   }

   public LinkButton getButton() {
      return button_;
   }

   public SettingsLinker getLinker() {
      return linker_;
   }
}
