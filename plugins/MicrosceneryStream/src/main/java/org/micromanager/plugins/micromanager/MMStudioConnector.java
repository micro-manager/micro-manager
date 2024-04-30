package org.micromanager.plugins.micromanager;

import org.jetbrains.annotations.NotNull;
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

    @Override
    public void alertUser(@NotNull String title, @NotNull String msg) {
        this.studio.alerts().postAlert(title, null, msg);
    }
}
