package org.micromanager.plugins.micromanager;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
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

    @Override
    public void addPositionToPositionList(@NotNull String label, @NotNull Vector3f newPos) {
        PositionList posList = studio.positions().getPositionList();
        MultiStagePosition mSPos = new MultiStagePosition(
                studio.core().getXYStageDevice(),
                newPos.x,
                newPos.y,
                studio.core().getFocusDevice(),
                newPos.z);
        mSPos.setLabel(label);
        posList.addPosition(mSPos);
        studio.positions().setPositionList(posList);
    }
}
