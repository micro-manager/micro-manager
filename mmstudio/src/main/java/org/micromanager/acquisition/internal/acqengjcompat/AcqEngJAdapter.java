///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard
//
// COPYRIGHT:    Photomics Inc, 2022
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


package org.micromanager.acquisition.internal.acqengjcompat;

import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.AutofocusPlugin;
import org.micromanager.PositionList;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;

import java.util.concurrent.BlockingQueue;

/**
 * This class provides a compatibility layer between IAcquisitionEngine2010 and
 * AcqEngJ
 *
 * IAcquisitionEngine2010 makes the assumption that one and only one acquisition
 * is running at any given time, whereas acqEngJ does not have this restriction.
 * As a result, this class holds a single acquisiton
 */
public class AcqEngJAdapter implements IAcquisitionEngine2010 {

    private Engine acqEngJ_;

    public AcqEngJAdapter(Engine acqEngineJ) {
        acqEngJ_ = acqEngineJ;
    }

    @Override
    public BlockingQueue<TaggedImage> run(SequenceSettings sequenceSettings) {
        //TODO
    }

    @Override
    public BlockingQueue<TaggedImage> run(SequenceSettings sequenceSettings, boolean cleanup, PositionList positionList, AutofocusPlugin device) {
        //TODO
    }

    @Override
    public BlockingQueue<TaggedImage> run(SequenceSettings sequenceSettings, boolean cleanup) {
        //TODO
    }

    @Override
    public JSONObject getSummaryMetadata() {
        //TODO
    }

    @Override
    public void pause() {
        //TODO
    }

    @Override
    public void resume() {
        //TODO
    }

    @Override
    public void stop() {
        ///TODO
    }

    @Override
    public boolean isRunning() {
        //TODO

    }

    @Override
    public boolean isPaused() {
        //TODO
    }

    @Override
    public boolean isFinished() {
        //TODO
    }

    @Override
    public boolean stopHasBeenRequested() {
        //TODO
    }

    @Override
    public long nextWakeTime() {
        //TODO
    }

    @Override
    public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
        //TODO
    }

    @Override
    public void clearRunnables() {
        //TODO
    }
}
