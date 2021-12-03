///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Marc Bruce, 2020

package org.micromanager.events.internal;

import org.micromanager.ApplicationSkin;
import org.micromanager.events.ApplicationSkinEvent;

/**
 * Event signalling that the Application Skin was changed.
 *
 * @author marc
 */
public final class DefaultApplicationSkinEvent implements ApplicationSkinEvent {
   private final ApplicationSkin.SkinMode mode_;

   public DefaultApplicationSkinEvent(ApplicationSkin.SkinMode mode) {
      mode_ = mode;
   }
    
   @Override
   public ApplicationSkin.SkinMode getSkinMode() {
      return mode_;
   }
}