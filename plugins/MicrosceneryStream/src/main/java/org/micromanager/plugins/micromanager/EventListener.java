package org.micromanager.plugins.micromanager;

import com.google.common.eventbus.Subscribe;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import org.joml.Vector3f;
import org.micromanager.Studio;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;

@SuppressWarnings("UnstableApiUsage")
public class EventListener {

    private final Studio studio;
    private final MicromanagerWrapper mmWrapper;

    public boolean listenToStage = true;

    public EventListener(Studio studio, MicromanagerWrapper mmWrapper) {
        this.studio = studio;
        this.mmWrapper = mmWrapper;

        studio.events().registerForEvents(this);
    }

    @Subscribe
    public void stageMoveXY(XYStagePositionChangedEvent event){
        if (!listenToStage) return;

        Vector3f newPos = new Vector3f((float) event.getXPos(), (float) event.getYPos(),mmWrapper.getStagePosition().z);

        mmWrapper.updateStagePositionNoMovement(newPos);
    }
    @Subscribe
    public void stageMoveZ(StagePositionChangedEvent event){
        if (!listenToStage) return;

        Vector3f newPos = new Vector3f(
                mmWrapper.getStagePosition().x,
                mmWrapper.getStagePosition().y,
                (float) event.getPos());

        mmWrapper.updateStagePositionNoMovement(newPos);
    }

    public void close(){
        studio.events().unregisterForEvents(this);
    }
}
