///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Marc Bruce, 2020

package org.micromanager.events;

import org.micromanager.ApplicationSkin;
import org.micromanager.MMEvent;

/**
 * This event is posted when the ApplicationSkin is set via
 * {@link org.micromanager.ApplicationSkin#setSkin(org.micromanager.ApplicationSkin.SkinMode)}.
 *
 * <p>The default implementation of this event is posted on the Studio event bus,
 * so subscribe to this event using {@link org.micromanager.events.EventManager}.</p>
 */
public interface ApplicationSkinEvent extends MMEvent {

   /**
    * Returns the newly applied skin mode.
    *
    * @return the new skin mode
    */
   ApplicationSkin.SkinMode getSkinMode();
}
