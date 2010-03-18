/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.surveyor;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import org.json.JSONException;
import org.micromanager.MMAcquisitionEngineMT;
import org.micromanager.image5d.Image5D;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PositionMode;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class MMAcquisitionEngineMTMosaic extends MMAcquisitionEngineMT {

   private int currentContigIndex_ = 0;
   private Controller controller_;
   private int currentChannelIdx_;
   private int currentSliceIdx_;
   private int currentActualFrameCount_;
   private RoiManager rm_;
   private boolean isSetup[];
   private int currentPosIndex_;
   private final Hub hub_;

   public MMAcquisitionEngineMTMosaic(Hub hub) {
      hub_ = hub;
   }

   public void acquire() throws MMException, MMAcqDataException {
      controller_ = hub_.getController();
      posList_ = ((RoiManager) rm_).convertRoiManagerToPositionList();
      isSetup = new boolean[posList_.getNumberOfPositions()];
      super.acquire();
   }

   protected void insertPixelsIntoImage5D(int sliceIdx, int channelIdx, int actualFrameCount,
         int posIndexNormalized, Object img) {
      // Do nothing.
   }

   protected void wipeImage5D(Image5D image5D) {
      int nc = image5D.getNChannels();
      int ns = image5D.getNSlices();
      int nf = image5D.getNFrames();

      for (int c = 1; c <= nc; ++c) {
         for (int f = 1; f <= nf; ++f) {
            for (int s = 1; s <= ns; ++s) {
               Object emptyPixels = image5D.createEmptyPixels();
               image5D.setPixels(emptyPixels, c, s, f);
            }
         }
      }
   }

   protected Image5D createImage5D(int posIdx, int type, int numSlices, int actualFrames) {
      Rectangle bounds;
      if (posMode_ == PositionMode.TIME_LAPSE)
         bounds = ((MultiStagePositionMosaic) posList_.getPosition(posIdx)).bounds;
      else
         bounds = new Rectangle(0,0,(int) imgWidth_, (int) imgHeight_);
      return (Image5D) new Image5DMosaic(acqName_, type, bounds.width, bounds.height, channels_.size(), numSlices, actualFrames, false);
   }

   public void setRoiManager(RoiManager rm) {
      rm_ = rm;
   }

   protected long getImageWidth() {
      Rectangle bounds = ((MultiStagePositionMosaic) posList_.getPosition(currentPosIndex_)).bounds;
      return (long) bounds.width;
   }

   protected long getImageHeight() {
      Rectangle bounds = ((MultiStagePositionMosaic) posList_.getPosition(currentPosIndex_)).bounds;
      return (long) bounds.height;
   }

   protected void fullSetup(int positionIndex) throws MMException, IOException, MMAcqDataException {
      if (isSetup[positionIndex] == false) {
         super.fullSetup(positionIndex);
         isSetup[positionIndex] = true;
      }
   }

   protected Object snapAndRetrieve() throws Exception {
      MultiStagePositionMosaic currentPos = (MultiStagePositionMosaic) posList_.getPosition(currentPosIndex_);

      Object img;
      Image5DMosaic mosaicI5d;
      imgWidth_ = getImageWidth();
      imgHeight_ = getImageHeight();

      // processing for the first image in the entire sequence
      if (currentSliceIdx_ == 0 && currentChannelIdx_ == 0 && frameCount_ == 0) {
         if (posMode_ == PositionMode.TIME_LAPSE /* Positions first */) {
            if (currentPosIndex_ == 0) {
               fullSetup(currentPosIndex_);
            }
         } else {
            if (i5dWin_.length > 0 && i5dWin_[0] != null) {
               i5dWin_[0].setVisible(false);
               i5dWin_[0].dispose();
            }
            fullSetup(currentPosIndex_);
         }
      }

      acqData_[currentContigIndex_].setImagePhysicalDimensions(img5d_[currentContigIndex_].getWidth(), img5d_[currentContigIndex_].getHeight(), img5d_[currentContigIndex_].getBytesPerPixel());

      mosaicI5d = (Image5DMosaic) img5d_[currentContigIndex_];

      Rectangle bounds = currentPos.bounds;
      mosaicI5d.setPixels(mosaicI5d.createEmptyPixels(), currentChannelIdx_ + 1, currentSliceIdx_ + 1, currentActualFrameCount_ + 1);
      for (Point2D.Double stagePosition : currentPos.subPositions) {

         core_.setXYPosition(core_.getXYStageDevice(), stagePosition.x, stagePosition.y);
         core_.waitForDevice(core_.getXYStageDevice());
         //Snap an image and drop it into the image5d; update display
         core_.snapImage();
         img = core_.getImage();

         Point mapPos = controller_.getCurrentMapPosition();

         mosaicI5d.placePatch(currentChannelIdx_ + 1, currentSliceIdx_ + 1, currentActualFrameCount_ + 1, mapPos.x - bounds.x, mapPos.y - bounds.y, img, (int) core_.getImageWidth(), (int) core_.getImageHeight());
         mosaicI5d.updateAndDraw();
         //gui.displayImage(img);
         if (!i5dWin_[currentContigIndex_].isPlaybackRunning()) {
            mosaicI5d.setCurrentPosition(0, 0, currentChannelIdx_, currentSliceIdx_, currentActualFrameCount_);
         }

      }
      return mosaicI5d.getPixels(currentChannelIdx_ + 1, currentSliceIdx_ + 1, currentActualFrameCount_ + 1); // return full image for consistency.
   }

   public void goToPosition(int posIndex) throws Exception {
      // Do nothing.
   }

   protected void executeProtocolBody(ChannelSpec cs, double z, int sliceIdx,
         int channelIdx, int posIdx, int numSlices, int posIndexNormalized) throws MMException, IOException, JSONException, MMAcqDataException {
      currentChannelIdx_ = channelIdx;
      currentSliceIdx_ = sliceIdx;
      currentContigIndex_ = posIndexNormalized;
      currentPosIndex_ = posIdx;
      currentActualFrameCount_ = singleFrame_ ? 0 : frameCount_;
      imgWidth_ = getImageWidth();
      imgHeight_ = getImageHeight();
      super.executeProtocolBody(cs, z, sliceIdx, channelIdx, posIdx, numSlices, posIndexNormalized);
   }

   public void generateMetadata(double zCur, int sliceIdx, int channelIdx, int posIdx, int posIndexNormalized, double exposureMs, Object img) throws MMAcqDataException {

      int actualFrameCount = singleFrame_ ? 0 : frameCount_;



      img = img5d_[posIndexNormalized].getPixels(channelIdx + 1, sliceIdx + 1, actualFrameCount + 1);
      super.generateMetadata(zCur, sliceIdx, channelIdx, posIdx, posIndexNormalized, exposureMs, img);
   }
}
