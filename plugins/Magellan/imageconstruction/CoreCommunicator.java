/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imageconstruction;

import acq.AcquisitionEvent;
import acq.CustomAcqEngine;
import acq.SignalTaggedImage;
import demo.DemoModeImageData;
import ij.IJ;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import misc.GlobalSettings;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.MMStudio;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Means for plugin classes to access the core, to support tricky things
 */
public class CoreCommunicator {

    private static final int IMAGE_CONSTRUCTION_QUEUE_SIZE = 200;
    private static CoreCommunicator singleton_;
    private static CMMCore core_ = MMStudio.getInstance().getCore();
    private LinkedBlockingQueue<ImageAndInfo> imageConstructionQueue_ = new LinkedBlockingQueue<ImageAndInfo>(IMAGE_CONSTRUCTION_QUEUE_SIZE);
    private ExecutorService imageConstructionExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Image construction thread");
        }
    });

    public CoreCommunicator() {
        singleton_ = this;
        //start up image construction thread
        imageConstructionExecutor_.submit(new Runnable() {
            @Override
            //Each run of this functions takes all the frames of a given channel, and integrates them into a final image
            //which is added to the acquisition
            public void run() {
                while (true) {
                    try {

                        //TODO: substitute in dummy pixel data for demo mode..or maybe something simpler for demo mode...
//               if (GlobalSettings.getDemoMode()) {
//                   constructedImage = makeDemoImage(iai);
//               } else {

                        //images are ordered in the wuwe with all frames of a given channel follwed by all frames of the next channel
                        ImageAndInfo firstIAI = imageConstructionQueue_.take();


  
                        if (firstIAI.img_ instanceof SignalTaggedImage) {
                            //if its a signal, send it to the loop
                            firstIAI.event_.acquisition_.addImage(firstIAI.img_);
                            continue;
                        }

//                        if (firstIAI.camChannelIndex_ == 5) {
//                            byte[] pix = ((byte[]) firstIAI.img_.pix);
//                            long sum = 0;
//                            for (int i = 0; i < pix.length; i++) {
//                                sum += pix[i];
//                            }
//                            System.out.println("Position: " + firstIAI.event_.positionIndex_ + "\t channel: " + 
//                                    firstIAI.camChannelIndex_ + "\t sum = " + (sum / pix.length));
//                        }
                        
                        //construct image from double wide
                        FrameIntegrationMethod integrator;
                        if (firstIAI.event_.acquisition_.getFilterType() == FrameIntegrationMethod.FRAME_AVERAGE) {
                            integrator = new FrameAverageWrapper(GlobalSettings.getInstance().getChannelOffset(firstIAI.camChannelIndex_),
                                    MDUtils.getWidth(firstIAI.img_.tags), firstIAI.numFrames_);
                        } else if (firstIAI.event_.acquisition_.getFilterType() == FrameIntegrationMethod.RANK_FILTER) {
                            integrator = new RankFilterWrapper(GlobalSettings.getInstance().getChannelOffset(firstIAI.camChannelIndex_),
                                    MDUtils.getWidth(firstIAI.img_.tags), firstIAI.numFrames_, firstIAI.event_.acquisition_.getRank());
                        } else { //frame summation
                            integrator = new FrameSummationWrapper(GlobalSettings.getInstance().getChannelOffset(firstIAI.camChannelIndex_),
                                    MDUtils.getWidth(firstIAI.img_.tags), firstIAI.numFrames_);
                        }

                        //add first frame
                        integrator.addBuffer((byte[]) firstIAI.img_.pix);
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
                        MDUtils.setWidth(firstIAI.img_.tags, integrator.getConstructedImageWidth());
                        MDUtils.setHeight(firstIAI.img_.tags, integrator.getConstructedImageHeight());
                        if (integrator instanceof FrameSummationWrapper) {
                            MDUtils.setPixelTypeFromByteDepth(firstIAI.img_.tags, 2);
                        }

                        //construct image
                        TaggedImage constructedImage;
                        try {
                            constructedImage = new TaggedImage(integrator.constructImage(), firstIAI.img_.tags);
                        } catch (Exception e) {
                            e.printStackTrace();
                            IJ.log("Couldn't construct image: " + e.toString());
                            continue;
                        }

                        //add metadata 
                        CustomAcqEngine.addImageMetadata(constructedImage, firstIAI.event_, firstIAI.numCamChannels_,
                                firstIAI.camChannelIndex_, firstIAI.currentTime_ - firstIAI.event_.acquisition_.getStartTime_ms(),
                                firstIAI.numFrames_);

                        //add to acq for display/saving 
                        firstIAI.event_.acquisition_.addImage(constructedImage);
                    } catch (InterruptedException ex) {
                        IJ.log("Unexpected interrupt of image construction thread! Ignoring...");
                    } catch (JSONException e) {
                        IJ.log(e.toString());
                    }
                }
            }
        });
    }

    public static CoreCommunicator getInstance() {
        return singleton_;
    }
    
    public static int getImageWidth() {
        return RawBufferWrapper.getWidth();
    }

    public static int getImageHeight() {
        return RawBufferWrapper.getHeight();
    }

    public static void snapImage() throws Exception {
        core_.initializeCircularBuffer();
        //clear circular buffer because it is not actuall circular
        core_.clearCircularBuffer();
        core_.snapImage();
    }

    /**
     * need to do this through core communicator so it occurs on the same thread
     * as image construction
     */
    public void addSignalTaggedImage(AcquisitionEvent evt, TaggedImage img) throws InterruptedException {
        imageConstructionQueue_.put(new ImageAndInfo(img, evt, 0, 0, 0, 0));
    }

    /**
     * Intercept calls to get tagged image so image can be created in java layer
     * Grab raw images from core and insert them into image construction
     * executor for processing return immediately if theres space in processing
     * queue so acq can continue, otherwise block until space opens up so acq
     * doesnt try to go way faster than images can be constructed
     */
    public void getTaggedImagesAndAddToAcq(AcquisitionEvent event, final long currentTime) throws Exception {

        //Images go into circular buffer one channel at a time followed by succsessive frames
        //want to change the order to all frames of a channel at a time
        int numFrames = 1;
        try {
            numFrames = (int) core_.getExposure();
        } catch (Exception e) {
            IJ.log("Couldnt get exposure form core");
        }
        final int numCamChannels = (int) core_.getNumberOfCameraChannels();

        for (int c = 0; c < numCamChannels; c++) {
            for (int framesBack = numFrames - 1; framesBack >= 0; framesBack--) {
                //channel 0 is farthest back
                int backIndex = framesBack * numCamChannels + (numCamChannels - 1 - c);
                TaggedImage img = core_.getNBeforeLastTaggedImage(backIndex);
                imageConstructionQueue_.put(new ImageAndInfo(img, event, numCamChannels, c, currentTime, numFrames));

            }
        }
    }

    private TaggedImage makeDemoImage(ImageAndInfo iai) {
        Object demoPix;
        try {
            if (core_.getBytesPerPixel() == 1) {
                demoPix = DemoModeImageData.getBytePixelData(iai.camChannelIndex_, (int) iai.event_.xyPosition_.getCenter().x,
                        (int) iai.event_.xyPosition_.getCenter().y, (int) iai.event_.zPosition_, MDUtils.getWidth(iai.img_.tags), MDUtils.getHeight(iai.img_.tags));
            } else {
                demoPix = DemoModeImageData.getShortPixelData(iai.camChannelIndex_, (int) iai.event_.xyPosition_.getCenter().x,
                        (int) iai.event_.xyPosition_.getCenter().y, (int) iai.event_.zPosition_, MDUtils.getWidth(iai.img_.tags), MDUtils.getHeight(iai.img_.tags));
            }
            return new TaggedImage(demoPix, iai.img_.tags);
        } catch (Exception e) {
            e.printStackTrace();
            ReportingUtils.showError("Problem getting demo data");
            throw new RuntimeException();
        }

    }

    class ImageAndInfo {

        TaggedImage img_;
        AcquisitionEvent event_;
        int numCamChannels_;
        int camChannelIndex_;
        long currentTime_;
        int numFrames_;

        public ImageAndInfo(TaggedImage img, AcquisitionEvent e, int numCamChannels, int cameraChannelIndex, long currentTime,
                int numFrames) {
            img_ = img;
            event_ = e;
            numCamChannels_ = numCamChannels;
            camChannelIndex_ = cameraChannelIndex;
            currentTime_ = currentTime;
            numFrames_ = numFrames;
        }
    }
}
