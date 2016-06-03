///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//

package org.micromanager.plugins.magellan.bidc;

import org.micromanager.plugins.magellan.acq.AcquisitionEvent;
import org.micromanager.plugins.magellan.acq.FixedAreaAcquisition;
import org.micromanager.plugins.magellan.acq.MagellanEngine;
import org.micromanager.plugins.magellan.acq.MagellanTaggedImage;
import org.micromanager.plugins.magellan.acq.SignalTaggedImage;
import org.micromanager.plugins.magellan.demo.DemoModeImageData;
import ij.IJ;
import java.awt.geom.Point2D;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import org.micromanager.plugins.magellan.json.JSONException;
import org.micromanager.plugins.magellan.json.JSONObject;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.GlobalSettings;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.MD;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;

public class JavaLayerImageConstructor {

    private static final int IMAGE_CONSTRUCTION_QUEUE_SIZE = 200;
    private static JavaLayerImageConstructor singleton_;
    private static CMMCore core_ = Magellan.getCore();
    private LinkedBlockingQueue<ImageAndInfo> imageConstructionQueue_ = new LinkedBlockingQueue<ImageAndInfo>(IMAGE_CONSTRUCTION_QUEUE_SIZE);
    private ExecutorService imageConstructionExecutor_;
    private boolean javaLayerConstruction_ = false;

    public JavaLayerImageConstructor() {
        singleton_ = this;

        //start up image construction thread
        javaLayerConstruction_ = GlobalSettings.getInstance().isBIDCTwoPhoton();
        if (javaLayerConstruction_) {
            imageConstructionExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "Image construction thread");
                }
            });
            imageConstructionExecutor_.submit(new Runnable() {
                @Override
                //Each run of this functions takes all the frames of a given channel, and integrates them into a final image
                //which is added to the acquisition
                public void run() {
                    while (true) {
                        try {
                            //images are ordered in the wuwe with all frames of a given channel follwed by all frames of the next channel
                            ImageAndInfo firstIAI = imageConstructionQueue_.take();

                            if (firstIAI.img_ instanceof SignalTaggedImage) {
                                //if its a signal, send it to the loop
                                firstIAI.event_.acquisition_.addImage(firstIAI.img_);
                                continue;
                            }

                            //construct image from double wide
                            FrameIntegrationMethod integrator;
                            if (firstIAI.event_.acquisition_.getFilterType() == FrameIntegrationMethod.FRAME_AVERAGE) {
                                integrator = new FrameAverageWrapper(GlobalSettings.getInstance().getChannelOffset(firstIAI.camChannelIndex_),
                                        MD.getWidth(firstIAI.img_.tags), firstIAI.numFrames_);
                            } else if (firstIAI.event_.acquisition_.getFilterType() == FrameIntegrationMethod.RANK_FILTER) {
                                integrator = new RankFilterWrapper(GlobalSettings.getInstance().getChannelOffset(firstIAI.camChannelIndex_),
                                        MD.getWidth(firstIAI.img_.tags), firstIAI.numFrames_, firstIAI.event_.acquisition_.getRank());
                            } else if (firstIAI.event_.acquisition_.getFilterType() == FrameIntegrationMethod.BURST_MODE){
                                integrator = new FrameAverageWrapper(GlobalSettings.getInstance().getChannelOffset(firstIAI.camChannelIndex_),
                                        MD.getWidth(firstIAI.img_.tags), 1);
                            } else { //frame summation
                                integrator = new FrameSummationWrapper(GlobalSettings.getInstance().getChannelOffset(firstIAI.camChannelIndex_),
                                        MD.getWidth(firstIAI.img_.tags), firstIAI.numFrames_);
                            }

                            //add first frame
                            integrator.addBuffer((byte[]) firstIAI.img_.pix);

                            if (firstIAI.event_.acquisition_ instanceof FixedAreaAcquisition
                                    && ((FixedAreaAcquisition) firstIAI.event_.acquisition_).burstModeActive()) {
                                //skip adding successive frames
                            } else {
                                //add all successive frames
                                for (int i = 1; i < firstIAI.numFrames_; i++) {
                                    ImageAndInfo nextIAI = imageConstructionQueue_.take();
                                    if (nextIAI.img_ instanceof SignalTaggedImage) {
                                        //bypass construction
                                        nextIAI.event_.acquisition_.addImage(nextIAI.img_);
                                        continue;
                                    }
                                    integrator.addBuffer((byte[]) nextIAI.img_.pix);
                                }
                            }
                            MD.setWidth(firstIAI.img_.tags, integrator.getConstructedImageWidth());
                            MD.setHeight(firstIAI.img_.tags, integrator.getConstructedImageHeight());
                            if (integrator instanceof FrameSummationWrapper) {
                                MD.setPixelTypeFromByteDepth(firstIAI.img_.tags, 2);
                            }

                            //construct image
                            MagellanTaggedImage constructedImage;
                            try {
                                constructedImage = new MagellanTaggedImage(integrator.constructImage(), firstIAI.img_.tags);
                            } catch (Exception e) {
                                e.printStackTrace();
                                IJ.log("Couldn't construct image: " + e.toString());
                                continue;
                            }

                            if (firstIAI.event_.acquisition_ instanceof FixedAreaAcquisition
                                    && ((FixedAreaAcquisition) firstIAI.event_.acquisition_).burstModeActive()) {
                                //burst mode
                                MagellanEngine.addImageMetadata(constructedImage.tags, firstIAI.event_, 
                                        firstIAI.event_.timeIndex_ * firstIAI.numFrames_ + firstIAI.frameNumber_,
                                        firstIAI.camChannelIndex_, firstIAI.currentTime_ - firstIAI.event_.acquisition_.getStartTime_ms(),
                                        firstIAI.numFrames_);
                            } else {
                                //add metadata 
                                MagellanEngine.addImageMetadata(constructedImage.tags, firstIAI.event_, firstIAI.event_.timeIndex_,
                                        firstIAI.camChannelIndex_, firstIAI.currentTime_ - firstIAI.event_.acquisition_.getStartTime_ms(),
                                        firstIAI.numFrames_);
                            }
                            
                            //add to acq for display/saving 
                            firstIAI.event_.acquisition_.addImage(constructedImage);

                        } catch (InterruptedException ex) {
                            IJ.log("Unexpected interrupt of image construction thread! Ignoring...");
                        }
                    }
                }
            });
        }
    }

    public static JavaLayerImageConstructor getInstance() {
        return singleton_;
    }

    public int getImageWidth() {
        return (int) (javaLayerConstruction_ ? RawBufferWrapper.getWidth() : core_.getImageWidth());
    }

    public int getImageHeight() {
        return (int) (javaLayerConstruction_ ? RawBufferWrapper.getHeight() : core_.getImageHeight());
    }

    public void snapImage() throws Exception {
        if (javaLayerConstruction_) {
            core_.initializeCircularBuffer();
            //clear circular buffer because it is not actuall circular
            core_.clearCircularBuffer();
        }
        core_.snapImage();
    }

    /**
     * need to do this through core communicator so it occurs on the same thread
     * as image construction
     */
    public void addSignalMagellanTaggedImage(AcquisitionEvent evt, MagellanTaggedImage img) throws InterruptedException {
        if (javaLayerConstruction_) {
            imageConstructionQueue_.put(new ImageAndInfo(img, evt, 0, 0, 0, 0, 0));
        } else {
            evt.acquisition_.addImage(img);
        }
    }

    /**
     * Intercept calls to get tagged image so image can be created in java layer
     * Grab raw images from core and insert them into image construction
     * executor for processing return immediately if theres space in processing
     * queue so acq can continue, otherwise block until space opens up so acq
     * doesnt try to go way faster than images can be constructed
     *
     */
    public void getMagellanTaggedImagesAndAddToAcq(AcquisitionEvent event, final long currentTime) throws Exception {
        if (javaLayerConstruction_) {
            //Images go into circular buffer one channel at a time followed by succsessive frames
            //want to change the order to all frames of a channel at a time
            int numFrames = 1;
            try {
                numFrames = (int) core_.getExposure();
            } catch (Exception e) {
                IJ.log("Couldnt get exposure form core");
            }
            final int numCamChannels = (int) core_.getNumberOfCameraChannels();

            for (int c = 0; c < core_.getNumberOfCameraChannels(); c++) {
                for (int framesBack = numFrames - 1; framesBack >= 0; framesBack--) {
                    //channel 0 is farthest back
                    int backIndex = framesBack * numCamChannels + (numCamChannels - 1 - c);
                    MagellanTaggedImage img = convertTaggedImage(core_.getNBeforeLastTaggedImage(backIndex));
                    imageConstructionQueue_.put(new ImageAndInfo(img, event, numCamChannels, c, currentTime, numFrames, numFrames - 1 - framesBack));

                }
            }
        } else {
            if (GlobalSettings.getInstance().getDemoMode()) {
                //add demo image
                for (int c = 0; c < DemoModeImageData.getNumChannels(); c++) {
                    JSONObject tags = convertTaggedImage(core_.getTaggedImage()).tags;
                    MD.setChannelIndex(tags, c);
                    MagellanEngine.addImageMetadata(tags, event, event.timeIndex_, c, currentTime - event.acquisition_.getStartTime_ms(), 1);
                    event.acquisition_.addImage(makeDemoImage(c, event.xyPosition_.getCenter(), event.zPosition_, tags));
                }
            } else {
                for (int c = 0; c < core_.getNumberOfCameraChannels(); c++) {
                    MagellanTaggedImage img = convertTaggedImage(core_.getTaggedImage(c));
                    MagellanEngine.addImageMetadata(img.tags, event, event.timeIndex_, c, currentTime - event.acquisition_.getStartTime_ms(), 1);
                    event.acquisition_.addImage(img);
                }
            }
        }
    }

    private MagellanTaggedImage makeDemoImage(int camChannelIndex, Point2D.Double position, double zPos, JSONObject tags) {
        Object demoPix;
        try {
            if (core_.getBytesPerPixel() == 1) {
                demoPix = DemoModeImageData.getBytePixelData(camChannelIndex, (int) position.x,
                        (int) position.y, (int) zPos, MD.getWidth(tags), MD.getHeight(tags));
            } else {
                demoPix = DemoModeImageData.getShortPixelData(camChannelIndex, (int) position.x,
                        (int) position.y, (int) zPos, MD.getWidth(tags), MD.getHeight(tags));
            }
            return new MagellanTaggedImage(demoPix, tags);
        } catch (Exception e) {
            e.printStackTrace();
            Log.log("Problem getting demo data");
            throw new RuntimeException();
        }

    }

    public static MagellanTaggedImage convertTaggedImage(TaggedImage img) {
        try {
            return new MagellanTaggedImage(img.pix, new JSONObject(img.tags.toString()));
        } catch (JSONException ex) {
            Log.log("Couldn't convert JSON metadata");
            throw new RuntimeException();
        }
    }

    class ImageAndInfo {

        MagellanTaggedImage img_;
        AcquisitionEvent event_;
        int numCamChannels_;
        int camChannelIndex_;
        long currentTime_;
        int numFrames_;
        int frameNumber_;

        public ImageAndInfo(MagellanTaggedImage img, AcquisitionEvent e, int numCamChannels, int cameraChannelIndex, long currentTime,
                int numFrames, int frameNumber) {
            img_ = img;
            event_ = e;
            numCamChannels_ = numCamChannels;
            camChannelIndex_ = cameraChannelIndex;
            currentTime_ = currentTime;
            numFrames_ = numFrames;
            frameNumber_ = frameNumber;
        }
    }
}
