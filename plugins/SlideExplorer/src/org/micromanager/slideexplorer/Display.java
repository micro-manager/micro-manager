package org.micromanager.slideexplorer;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import ij.process.ImageStatistics;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.slideexplorer.Hub.ModeManager;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.ReportingUtils;

public class Display {

	private ImagePlus imgp_;
	private Window win_;
	private Canvas cvs_;
	private ImageProcessor proc_;
	private Hub hub_;
	private double displayRangeMin_;
	private double displayRangeMax_;
        private int type_;
	private int imgCount_ = 0;
    private boolean currentlyPanning_ = false;
    private RoiManager roiManager_;
    private Coordinates coords_;
	private boolean contrastAutoAdjusted_ = false;

	public Display(Hub hub, int imageType, int width, int height) {
		hub_ = hub;
		if (IJ.getInstance() != null)
		   IJ.setTool("hand");

                type_ = imageType;
		proc_ = ImageUtils.makeProcessor(imageType, width, height);
		imgp_ = new ImagePlus("Slide Explorer",proc_);

		cvs_ = new Canvas(this, imgp_);
		
		win_ = new Window(imgp_, (ImageCanvas) cvs_, this);
		imgCount_=0;
		win_.setVisible(true);

        }
	
    public void setCoords(Coordinates coords) {
        coords_ = coords;
		updateDimensions();
    }

    /*
	 * Updates the dimensions so that OffScreen is 3x
	 * the size of OnScreen and such that OnScreen is
	 * concentric with OffScreen.
	 */
	public void updateDimensions() {
		Rectangle bounds = cvs_.getBounds();
		int w = bounds.width;
		int h = bounds.height;

		Rectangle srcRect = cvs_.getSrcRect();
		if (srcRect.x != w || srcRect.height != h) {
			srcRect.x = w;
			srcRect.y = h;
			srcRect.width = w;
			srcRect.height = h;
            coords_.setViewDimensionsOffScreen(new Dimension(3*w,3*h));

			hub_.resize(new Dimension(w, h));
		}
	}

    public Window getWindow() {
        return win_;
    }
    
	public void placeImage(Point offScreenPosition, ImageProcessor img) {
		proc_.insert(img, offScreenPosition.x, offScreenPosition.y);
        imgCount_++;
	}
	
	public void pan(Rectangle panRectangle, boolean update) {
        if (!currentlyPanning_) {
            currentlyPanning_ = true;
            int w = cvs_.getWidth();
            int h = cvs_.getHeight();
            int dx = panRectangle.x - w;
            int dy = panRectangle.y - h;

            if (dx != 0 || dy != 0 || update) {
                saveDisplayRange();
                ImageProcessor newProc = ImageUtils.makeProcessor(imgp_.getType(), 3*w, 3*h);
                newProc.insert(proc_, -dx, -dy);
                proc_ = newProc;
                imgp_.setProcessor("Slide Explorer", proc_);
                hub_.panBy(-dx, -dy);
                updateDimensions();
                imgp_.setProcessor(imgp_.getTitle(), proc_);
                reapplyDisplayRange();
                panRoisBy(dx, dy);
            }
            currentlyPanning_ = false;
        }
	}

    
    protected void panRoisBy(int dx, int dy) {
        Roi roi = imgp_.getRoi();
		panRoi(roi, dx, dy);
            if (roiManager_ != null) {
            for (Roi eachRoi:roiManager_.getRoisAsArray()) {
                panRoi(eachRoi, dx, dy);
            }
        }
	}

    protected void panRoi(Roi roi, int dx, int dy) {
		if (roi != null) {
			Rectangle rect = roi.getBoundingRect();
			roi.setLocation(rect.x-dx, rect.y-dy);
		}
	}

	void showRoiAt(Rectangle roiRect) {
		imgp_.setRoi(roiRect);
	}
	
	public void updateAndDraw() {
		imgp_.updateAndDraw();
		/*ImageStatistics stats = imgp_.getStatistics();
      double displayRange = imgp_.getDisplayRangeMax()-imgp_.getDisplayRangeMin();
      double actualRange = stats.max - stats.min;
	if (! contrastAutoAdjusted_) // Only do this once.
         if ((displayRange < 5) || (displayRange/actualRange < 0.6667) || (displayRange/actualRange > 1.5)) {
            imgp_.setDisplayRange(stats.min, stats.max);
            contrastAutoAdjusted_ = true;
         }*/
	}

	public void show() {
		imgp_.show();
	}

	protected void saveDisplayRange() {
		displayRangeMin_ = imgp_.getDisplayRangeMin();
		displayRangeMax_ = imgp_.getDisplayRangeMax();
	}

	protected void reapplyDisplayRange() {
		imgp_.setDisplayRange(displayRangeMin_, displayRangeMax_);
	}
	
	public void zoomOut(Point point) {
		hub_.zoomBy(-1);
	}

	public void zoomIn(Point point) {
		hub_.zoomBy(+1);
	}

    public void zoomTo(int zoomLevel, Point point) {
        hub_.zoomTo(zoomLevel, point);
    }

	public void shutdown() {
		hub_.shutdown();
		
	}

	public void navigate(Point canvasPos) {
		hub_.navigate(new Point(canvasPos.x, canvasPos.y));
	}

	public void survey() {
		hub_.survey();
		
	}

    public void pauseSlideExplorer() {
        hub_.pauseSlideExplorer();
    }

	public void hideRoi() {
		imgp_.killRoi();
	}

    public void fillWithBlack() {
        proc_ = ImageUtils.makeProcessor(type_, imgp_.getWidth(), imgp_.getHeight());
        imgp_.setProcessor(imgp_.getTitle(), proc_);
        try {
        ContrastSettings contrastSettings = ((MMStudioMainFrame) hub_.getApp()).getContrastSettings();
        imgp_.setDisplayRange(contrastSettings.min, contrastSettings.max);
        } catch (Exception e) {
            ReportingUtils.logError(e);
        }
    }

    void showConfig() {
        hub_.showConfig();
    }

    void showRoiManager() {
        hub_.deployRoiManager();
    }

    void setRoiManager(RoiManager roiManager) {
        roiManager_ = roiManager;
    }

    void acquireMosaics() {
        hub_.acquireMosaics();
    }

    public int getMode() {
        return hub_.getMode();
    }

    void update() {
        win_.updateControls();
    }

    int getZoomLevel() {
        return hub_.getZoomLevel();
    }

    void snap() {
        hub_.snap();
    }

    void onClick(Point offScreenPos) {
        if (hub_.getMode() == ModeManager.NAVIGATE) {
            navigate(offScreenPos);
        }

    }

    void roiDrawn() {
      roiManager_.add(imgp_,imgp_.getRoi(),-1);
      cvs_.setShowAllROIs(true);
      cvs_.repaint();
    }

    void clearRois() {
        imgp_.killRoi();
        roiManager_.getROIs().clear();
        roiManager_.getList().removeAll();
        cvs_.setShowAllROIs(false);
        cvs_.repaint();
    }
   
}
