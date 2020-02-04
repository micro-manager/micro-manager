/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay.events;

import org.micromanager.magellan.internal.imagedisplay.ScrollbarLockIcon;

   /**
    * This event informs listeners of when the lock button is toggled.
    */
    public class LockEvent {
      private String axis_;
      private ScrollbarLockIcon.LockedState lockedState_;
      public LockEvent(String axis, ScrollbarLockIcon.LockedState lockedState) {
         axis_ = axis;
         lockedState_ = lockedState;
      }
      public String getAxis() {
         return axis_;
      }
      public ScrollbarLockIcon.LockedState getLockedState() {
         return lockedState_;
      }
   }