///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Marc Bruce, 2020
package org.micromanager.events;

import org.micromanager.ApplicationSkin;

/**
 * This event is posted when the ApplicationSkin is set via
 * {@link org.micromanager.ApplicationSkin#setSkin(org.micromanager.ApplicationSkin.SkinMode)}
 */
public class ApplicationSkinEvent {
    private final ApplicationSkin.SkinMode mode_;

    public ApplicationSkinEvent(ApplicationSkin.SkinMode mode) {
        mode_ = mode;
    }
    /**
     * @return new skin mode
     */
    public ApplicationSkin.SkinMode getSkinMode() {
        return mode_;
    }
}
