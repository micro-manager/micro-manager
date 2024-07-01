package org.micromanager.plugins.micromanager;

import microscenery.Settings;
import microscenery.Util;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.Studio;

import javax.swing.*;

public class MMStudioConnector implements microscenery.hardware.micromanagerConnection.MMStudioConnector {
    private final Studio studio;
    private final MicrosceneryContext context;

    public MMStudioConnector(Studio studio, MicrosceneryContext context) {
        this.studio = studio;
        this.context = context;
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
    public StageLimitErrorResolves askForStageLimitErrorResolve() {
        fromScenery.Settings msSettings = Util.getMicroscenerySettings();
        String stagePos = Util.toReadableString(context.mmCon.getStagePosition());
        String limitMin = Util.toReadableString(msSettings.get(Settings.Stage.Limits.Min, new Vector3f()));
        String limitMax = Util.toReadableString(msSettings.get(Settings.Stage.Limits.Max, new Vector3f()));
        String text = "Stage "+ stagePos+" not in allowed area from "+limitMin + " to "+  limitMax;

        Object[] options = {"Reset limits to stage position",
                "Move stage within limits"};
        int n = JOptionPane.showOptionDialog(null,
                text,
                "Stage out of bounds!",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);

        if (n == 0){
            return StageLimitErrorResolves.RESET_LIMITS;
        } else if (n == 1) {
            return StageLimitErrorResolves.MOVE_STAGE;
        } else {
            return null;
        }
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
