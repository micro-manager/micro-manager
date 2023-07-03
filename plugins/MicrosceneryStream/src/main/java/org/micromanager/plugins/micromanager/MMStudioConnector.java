package org.micromanager.plugins.micromanager;

import org.micromanager.Studio;

public class MMStudioConnector implements microscenery.hardware.micromanagerConnection.MMStudioConnector {
    private final Studio studio;

    public MMStudioConnector(Studio studio) {
        this.studio = studio;
    }

    @Override
    public void startAcquisition() {
        studio.getAcquisitionManager().runAcquisition();
    }

    @Override
    public void snap() {
        studio.getSnapLiveManager().snap(true);
    }

    @Override
    public void live(boolean b) {
        studio.live().setLiveModeOn(b);
    }
}
