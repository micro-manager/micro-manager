package org.micromanager.surveyor;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import org.micromanager.MMAcquisitionEngineMT;
import org.micromanager.api.ScriptInterface;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.surveyor.MosaicContigs.Contig;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;


public class MMAcquisitionEngineMTMosaic extends MMAcquisitionEngineMT {

	ArrayList<Integer> contigSetup = new ArrayList<Integer>();
	ArrayList<Image5D> img5dList = new ArrayList<Image5D>();
	Coordinates coords_;

    double magOld_;
	Point2D.Double originOld_;
	private MosaicContigs contigs_;
	protected ScriptInterface gui_;
	private RoiManager rm_;


    int currentContig_;
    Point currentTile_;
    Controller controller_;


	protected Image5D createImage5D(int posIdx, int type, int numSlices, int actualFrames) {
		Rectangle mapRect = contigs_.getContigBoundsOnMap(contigs_.get(posIdx));
        Rectangle2D.Double stageRect = contigs_.getContigBounds(contigs_.get(posIdx));
        System.out.println("rect: "+mapRect);
        Controller controller = new Controller(core_);

        controller.specifyMapRelativeToStage(new Point2D.Double(stageRect.x, stageRect.y), controller.getAngle(), core_.getPixelSizeUm());
		return (Image5D) new Image5DMosaic(acqName_, type, (int) mapRect.getWidth(), (int) mapRect.getHeight(), channels_.size(), numSlices, actualFrames, true, controller);
	}

	public MMAcquisitionEngineMTMosaic(Controller controller, Coordinates coords, ScriptInterface gui) {
        coords_ = coords;

        controller_ = controller;

		gui_ = gui;
		this.enableMultiPosition(true);
	}

	private int getContigIndex(int positionIndex) {
		Contig contig = contigs_.getContig(posList_.getPosition(positionIndex));
		return contigs_.indexOf(contig);
	}

	protected void setupImage5DArray() {
		img5d_ = new Image5DMosaic[contigs_.size()]; 
		i5dWin_ = new Image5DWindow[contigs_.size()];
	}

	protected void fullSetup(int posIdx) throws MMException, IOException, MMAcqDataException {
		int conIdx = getContigIndex(posIdx);
		setupImage5d(conIdx);
		contigSetup.add(conIdx);
		acquisitionDirectorySetup(posIdx);
	}

	protected void insertPixelsIntoImage5D(int sliceIdx, int channelIdx, int actualFrameCount,
			int posIndexNormalized, Object img) {
		int conIdx = getContigIndex(posIndexNormalized);

        Image5DMosaic mosaicI5d = (Image5DMosaic) img5d_[conIdx];
        Controller controller = mosaicI5d.getController();
		MultiStagePosition pos = posList_.getPosition(posIndexNormalized);
        Point mapPos = controller.stageToMap(new Point2D.Double(pos.getX(), pos.getY()));//gui_.getXYStagePosition());

        mosaicI5d.placePatch(channelIdx+1, sliceIdx+1, actualFrameCount+1, mapPos.x, mapPos.y, img, (int) core_.getImageWidth(), (int) core_.getImageHeight());
        mosaicI5d.updateAndDraw();
        //gui.displayImage(img);
        if (!i5dWin_[conIdx].isPlaybackRunning()) {
            mosaicI5d.setCurrentPosition(0, 0, channelIdx, sliceIdx, actualFrameCount);
        }

	}

	protected int getAvailablePosIndex(int posIndexNormalized) {
		int conIdx = getContigIndex(posIndexNormalized);
		int index=(null!=img5d_[conIdx])
		?conIdx
				:0;
		return index;
	}

	protected void setupImage5DWindowCountdown(GregorianCalendar cldStart,
			int posIndexNormalized) {
		int conIdx = getContigIndex(posIndexNormalized);
		if (i5dWin_ != null)
			if (i5dWin_[conIdx] != null) {
				i5dWin_[conIdx].startCountdown((long)frameIntervalMs_ - (GregorianCalendar.getInstance().getTimeInMillis() - cldStart.getTimeInMillis()), numFrames_ - frameCount_);
			}
	}


	protected void generateMetaData(double zCur, int sliceIdx, int channelIdx, int posIdx, int posIndexNormalized, double exposureMs, Object img) {
		// Do nothing for now.
		;
	}

	protected void cleanup() {
		/*offScreenToStageTranform = offScreenToStageTransformOld_;
		setMag(magOld_);
		setOrigin(originOld_);*/

		super.cleanup();
		contigSetup.clear();
		img5dList.clear();
	}

	public void setRoiManager(RoiManager rm) {
		rm_ = rm;
	}

	public void acquire() {
        controller_ = new Controller(core_);
        controller_.specifyMapRelativeToStage(magOld_, core_.getPixelSizeUm());
		posList_ = ((RoiManager) rm_).convertRoiManagerToPositionList();
		setupContigs(posList_);
		try {
			super.acquire();
		} catch (MMException e) {
			ReportingUtils.logError(e);
		} catch (MMAcqDataException e) {
			ReportingUtils.logError(e);
		}
	}

	public void setupContigs(PositionList posList) {
        Dimension navigationFrameDimensions = controller_.getTileDimensions();
		double pixelSize = core_.getPixelSizeUm();
		contigs_ = new MosaicContigs(navigationFrameDimensions.width, navigationFrameDimensions.height, pixelSize, controller_);
		contigs_.findContigs(posList);
	}


    public void snapAndRetrieve() {
        Point p = new Point(0,0);
        controller_.grabImageAtMapPosition(p);
    }
}

