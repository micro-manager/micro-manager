/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
package MMCustomization;

import com.imaging100x.twophoton.SettingsDialog;
import com.imaging100x.twophoton.TwoPhotonControl;
import com.imaging100x.twophoton.Util;
import ij.IJ;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;

import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.imagedisplay.IMMImagePlus;


import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.*;

public class DisplayPlus implements ImageCacheListener {

    private static final Color TRANSPARENT_BLUE = new Color(0, 0, 255, 60);
    //VirtualAcquisitionDisplay on top of which this display is built
    private VirtualAcquisitionDisplay vad_;
    private Controls controls_;
    private AcquisitionEngine eng_;
    private JSpinner gridXSpinner_, gridYSpinner_;
    private int tileWidth_, tileHeight_;
    private Point clickStart_;
    private Point gridStart_;
    private JSONArray positionList_;
    private int numRows_ = 1, numCols_ = 1;
    private boolean positionSelectMode_ = false, gotoMode_ = false, newGridMode_ = false;
    private boolean suspendUpdates_ = false;
    private int yOverlap_, xOverlap_;
    private int mouseRowIndex_ = -1, mouseColIndex_ = -1;
    private ArrayList<Point> selectedPositions_ = new ArrayList<Point>();
    private ScrollbarWithLabel tSelector_;

    public DisplayPlus(final ImageCache stitchedCache, AcquisitionEngine eng, JSONObject summaryMD) {
        yOverlap_ = SettingsDialog.getYOverlap();
        xOverlap_ = SettingsDialog.getXOverlap();
        eng_ = eng;
        try {
            tileWidth_ = MDUtils.getWidth(summaryMD);
            tileHeight_ = MDUtils.getHeight(summaryMD);

        } catch (JSONException ex) {
            ReportingUtils.showError("Width and height missing form summary MD");
        }

        try {
            if (summaryMD.has("InitialPositionList") && !summaryMD.isNull("InitialPositionList")) {
                positionList_ = summaryMD.getJSONArray("InitialPositionList");
                //get grid parameters
                for (int i = 0; i < positionList_.length(); i++) {
                    long colInd = positionList_.getJSONObject(i).getLong("GridColumnIndex");
                    long rowInd = positionList_.getJSONObject(i).getLong("GridRowIndex");
                    if (colInd >= numCols_) {
                        numCols_ = (int) (colInd + 1);
                    }
                    if (rowInd >= numRows_) {
                        numRows_ = (int) (rowInd + 1);
                    }
                }
            }
        } catch (Exception e) {
            ReportingUtils.showError("Couldnt get grid info");
        }
        String name = "Untitled";
        try {
            String pre = summaryMD.getString("Prefix");
            if (pre != null && pre.length() > 0) {
                name = pre;
            }
        } catch (Exception e) {}
        
        vad_ = new VirtualAcquisitionDisplay(stitchedCache, eng, name);
        controls_ = new Controls();

        //Add in custom controls
        try {
            JavaUtils.setRestrictedFieldValue(vad_, VirtualAcquisitionDisplay.class, "controls_", controls_);
        } catch (NoSuchFieldException ex) {
            ReportingUtils.showError("Couldn't create display controls");
        }
        vad_.show();

        try {
            //get reference to tSelector          
            tSelector_ = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue(
                    vad_, VirtualAcquisitionDisplay.class, "tSelector_");
        } catch (NoSuchFieldException ex) {
            ReportingUtils.showError("Couldnt get refernce to t Selctor");
        }

        //Zoom to 100%
        vad_.getImagePlus().getWindow().getCanvas().unzoom();

        //add mouse listeners for moving grids
        addMouseListeners();

        IJ.setTool(Toolbar.SPARE6);

        //remove channel switching scroll wheel listener
        vad_.getImagePlus().getWindow().removeMouseWheelListener(
                vad_.getImagePlus().getWindow().getMouseWheelListeners()[0]);

        stitchedCache.addImageCacheListener(this);
    }

    private Roi makeROIRect(int rowIndex, int colIndex) {
        int y, x;
        int width = tileWidth_ - xOverlap_ / 2, height = tileHeight_ - yOverlap_ / 2;
        ImageCanvas canvas = vad_.getImagePlus().getCanvas();
        int canvasWidth = (int) (canvas.getWidth() / canvas.getMagnification()),
                canvasHeight = (int) (canvas.getHeight() / canvas.getMagnification());

        if (rowIndex == 0) {
            y = 0;
        } else if (rowIndex == numRows_ - 1) {
            y = canvasHeight - (tileHeight_ - yOverlap_ / 2);
        } else {
            height = tileHeight_ - yOverlap_;
            y = (tileHeight_ - yOverlap_ / 2) + (rowIndex - 1) * (tileHeight_ - yOverlap_);
        }
        if (colIndex == 0) {
            x = 0;
        } else if (colIndex == numCols_ - 1) {
            x = canvasWidth - (tileWidth_ - xOverlap_ / 2);
        } else {
            width = tileWidth_ - xOverlap_;
            x = (tileWidth_ - xOverlap_ / 2) + (colIndex - 1) * (tileWidth_ - xOverlap_);
        }
        return new Roi(x, y, width, height);
    }

    private TextRoi makeTextRoi(int rowIndex, int colIndex, int offset) {
        int y, x;
        int width = tileWidth_ - xOverlap_ / 2, height = tileHeight_ - yOverlap_ / 2;
        ImageCanvas canvas = vad_.getImagePlus().getCanvas();
        int canvasWidth = (int) (canvas.getWidth() / canvas.getMagnification()),
                canvasHeight = (int) (canvas.getHeight() / canvas.getMagnification());

        if (rowIndex == 0) {
            y = 0;
        } else if (rowIndex == numRows_ - 1) {
            y = canvasHeight - (tileHeight_ - yOverlap_ / 2);
        } else {
            height = tileHeight_ - yOverlap_;
            y = (tileHeight_ - yOverlap_ / 2) + (rowIndex - 1) * (tileHeight_ - yOverlap_);
        }
        if (colIndex == 0) {
            x = 0;
        } else if (colIndex == numCols_ - 1) {
            x = canvasWidth - (tileWidth_ - xOverlap_ / 2);
        } else {
            width = tileWidth_ - xOverlap_;
            x = (tileWidth_ - xOverlap_ / 2) + (colIndex - 1) * (tileWidth_ - xOverlap_);
        }
        double mag = vad_.getImagePlus().getCanvas().getMagnification();
        TextRoi tr = new TextRoi(x + width / 2, y + height / 2, "Offset: " + offset + " um");
        tr.setCurrentFont(tr.getCurrentFont().deriveFont((float) (tr.getCurrentFont().getSize() / mag)));
        tr.setJustification(TextRoi.CENTER);
        tr.setLocation(x + width / 2, y + height / 2 - (height / 40 / mag));
        return tr;
    }

    private void drawDepthListOverlay(ImageCanvas canvas) {
        Overlay overlay = new Overlay();
        if (mouseRowIndex_ != -1 && mouseColIndex_ != -1) {
            Roi rect = makeROIRect(mouseRowIndex_, mouseColIndex_);
            rect.setFillColor(TRANSPARENT_BLUE);
            overlay.add(rect);
        }

        if (positionSelectMode_) {
            for (int row = 0; row < numRows_; row++) {
                for (int col = 0; col < numCols_; col++) {
                    overlay.add(makeTextRoi(row, col, Util.getDepthListOffset(getPosIndex(row, col))));
                }
            }
        }

        if (!selectedPositions_.isEmpty()) {
           for (Point p : selectedPositions_) {
              Roi selectionRect = makeROIRect(p.x, p.y);
              selectionRect.setStrokeWidth(10f);
              overlay.add(selectionRect);
           }
        }

        canvas.setOverlay(overlay);
    }

    private void addMouseListeners() {
        vad_.getImagePlus().getCanvas().addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (newGridMode_) {
                    Point finalPos = e.getPoint();
                    ImageCanvas canvas = vad_.getImagePlus().getCanvas();
                    int dx = (int) ((finalPos.x - clickStart_.x) / canvas.getMagnification());
                    int dy = (int) ((finalPos.y - clickStart_.y) / canvas.getMagnification());
                    vad_.getImagePlus().getOverlay().get(0).setLocation(
                            gridStart_.x + dx, gridStart_.y + dy);
                    if (!canvas.getPaintPending()) {
                        canvas.setPaintPending(true);
                        canvas.paint(canvas.getGraphics());
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (positionSelectMode_) {
                    ImageCanvas canvas = vad_.getImagePlus().getCanvas();
                    int canvasWidth = (int) (canvas.getWidth() / canvas.getMagnification()),
                            canvasHeight = (int) (canvas.getHeight() / canvas.getMagnification());
                    Point p = e.getPoint();
                    p.x = (int) (p.x / canvas.getMagnification());
                    p.y = (int) (p.y / canvas.getMagnification());
                    if (p.y < tileHeight_ - yOverlap_ / 2) {
                        mouseRowIndex_ = 0;
                    } else if (p.y > canvasHeight - (tileHeight_ - yOverlap_ / 2)) {
                        mouseRowIndex_ = numRows_ - 1;
                    } else {
                        mouseRowIndex_ = 1 + (p.y - (tileHeight_ - yOverlap_ / 2)) / (tileHeight_ - yOverlap_);
                    }
                    if (p.x < tileWidth_ - xOverlap_ / 2) {
                        mouseColIndex_ = 0;
                    } else if (p.x > canvasWidth - (tileWidth_ - xOverlap_ / 2)) {
                        mouseColIndex_ = numCols_ - 1;
                    } else {
                        mouseColIndex_ = 1 + (p.x - (tileWidth_ - xOverlap_ / 2))
                                / (tileWidth_ - xOverlap_);
                    }

                    drawDepthListOverlay(canvas);
                }
            }
        });

        vad_.getImagePlus().getCanvas().addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (positionSelectMode_) {
                   Point p = new Point();
                    p.x = mouseRowIndex_;
                    p.y = mouseColIndex_;
                    if (e.getModifiersEx() != MouseEvent.SHIFT_DOWN_MASK) {
                       selectedPositions_.clear();
                    }
                    selectedPositions_.add(p);
                    drawDepthListOverlay(vad_.getImagePlus().getCanvas());
                    int i = getPosIndex(p.x, p.y);
                    try {
                        controls_.updateSelectedPosition(positionList_.getJSONObject(i).getString("Label"));
                    } catch (JSONException ex) {
                        ReportingUtils.showError("couldnt update dpeth list offset");
                    }
                } else if (gotoMode_) {
                    //translate point into stage coordinates and move there
                    Point p = e.getPoint();
                    double xPixelDisp = (p.x / vad_.getImagePlus().getCanvas().getMagnification())
                            + vad_.getImagePlus().getCanvas().getSrcRect().x - vad_.getImagePlus().getWidth() / 2;
                    double yPixelDisp = (p.y / vad_.getImagePlus().getCanvas().getMagnification())
                            + vad_.getImagePlus().getCanvas().getSrcRect().y - vad_.getImagePlus().getHeight() / 2;

                    Point2D stagePos = stagePositionFromPixelPosition(xPixelDisp, yPixelDisp);
                    try {
                        MMStudio.getInstance().setXYStagePosition(stagePos.getX(), stagePos.getY());
                    } catch (MMScriptException ex) {
                        ReportingUtils.showError("Couldn't move xy stage");
                    }
                    controls_.clearSelectedButtons();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (newGridMode_) {
                    clickStart_ = e.getPoint();
                    Roi rect = vad_.getImagePlus().getOverlay().get(0);
                    Rectangle2D bounds = rect.getFloatBounds();
                    gridStart_ = new Point((int) bounds.getX(), (int) bounds.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!(positionSelectMode_ || gotoMode_)) {
                    return;
                }
                mouseRowIndex_ = -1;
                mouseColIndex_ = -1;
                drawDepthListOverlay(vad_.getImagePlus().getCanvas());
            }
        });

        vad_.getImagePlus().getCanvas().addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
              if (!selectedPositions_.isEmpty()) {
                 for (Point p : selectedPositions_) {
                    int numClicks = e.getWheelRotation();
                    Util.setDepthListOffset(getPosIndex(p.x, p.y), Util.getDepthListOffset(getPosIndex(p.x, p.y)) + numClicks);
                 }
                 drawDepthListOverlay(vad_.getImagePlus().getCanvas());
              }
           }
        });
    }

    public int getPosIndex(int row, int col) {
        for (int i = 0; i < positionList_.length(); i++) {
            try {
                long colInd = positionList_.getJSONObject(i).getLong("GridColumnIndex");
                long rowInd = positionList_.getJSONObject(i).getLong("GridRowIndex");
                if (rowInd == row && colInd == col) {
                    return i;
                }
            } catch (JSONException ex) {
                ReportingUtils.showError("Couldn't read position list");
            }
        }
        return 0;
    }

    private void createGrid() {
        try {
            //get displacements of center of rectangle from center of stitched image
            double rectCenterXDisp = vad_.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterX()
                    - vad_.getImagePlus().getWidth() / 2;
            double rectCenterYDisp = vad_.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterY()
                    - vad_.getImagePlus().getHeight() / 2;

            Point2D.Double stagePos = stagePositionFromPixelPosition(rectCenterXDisp, rectCenterYDisp);

            int xOverlap = SettingsDialog.getXOverlap(), yOverlap = SettingsDialog.getYOverlap();
            Util.createGrid(stagePos.x, stagePos.y,
                    (Integer) gridXSpinner_.getValue(), (Integer) gridYSpinner_.getValue(),
                    xOverlap, yOverlap);
            controls_.clearSelectedButtons();

        } catch (Exception e) {
            ReportingUtils.showError("Couldnt create grid");
        }
    }

    private Point2D.Double stagePositionFromPixelPosition(double xPixelDispFromCenter, double yPixelDispFromCenter) {
        try {
            //get coordinates of center of exisitng grid
            String xyStage = MMStudio.getInstance().getCore().getXYStageDevice();

            //row column map to coordinates for exisiting stage positiions
            Point2D.Double[][] coordinates = new Point2D.Double[numCols_][numRows_];
            for (int i = 0; i < positionList_.length(); i++) {
                int colInd = (int) positionList_.getJSONObject(i).getLong("GridColumnIndex");
                int rowInd = (int) positionList_.getJSONObject(i).getLong("GridRowIndex");
                JSONArray coords = positionList_.getJSONObject(i).getJSONObject("DeviceCoordinatesUm").getJSONArray(xyStage);
                coordinates[colInd][rowInd] = new Point2D.Double(coords.getDouble(0), coords.getDouble(1));
            }

            //find stage coordinate of center of existing grid
            double currentCenterX, currentCenterY;
            if (coordinates.length % 2 == 0 && coordinates[0].length % 2 == 0) {
                //even number of tiles in both directions
                currentCenterX = 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2 - 1].x + 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2].x
                        + 0.25 * coordinates[numCols_ / 2][numRows_ / 2 - 1].x + 0.25 * coordinates[numCols_ / 2][numRows_ / 2].x;
                currentCenterY = 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2 - 1].y + 0.25 * coordinates[numCols_ / 2 - 1][numRows_ / 2].y
                        + 0.25 * coordinates[numCols_ / 2][numRows_ / 2 - 1].y + 0.25 * coordinates[numCols_ / 2][numRows_ / 2].y;
            } else if (coordinates.length % 2 == 0) {
                //even number of columns
                currentCenterX = 0.5 * coordinates[numCols_ / 2 - 1][numRows_ / 2].x + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].x;
                currentCenterY = 0.5 * coordinates[numCols_ / 2 - 1][numRows_ / 2].y + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].y;
            } else if (coordinates[0].length % 2 == 0) {
                //even number of rows
                currentCenterX = 0.5 * coordinates[numCols_ / 2][numRows_ / 2 - 1].x + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].x;
                currentCenterY = 0.5 * coordinates[numCols_ / 2][numRows_ / 2 - 1].y + 0.5 * coordinates[numCols_ / 2][numRows_ / 2].y;
            } else {
                //odd number of both
                currentCenterX = coordinates[numCols_ / 2][numRows_ / 2].x;
                currentCenterY = coordinates[numCols_ / 2][numRows_ / 2].y;
            }

            //use affine transform to convert to stage coordinate of center of new grid
            AffineTransform transform = null;
            Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);
            try {
                transform = (AffineTransform) JavaUtils.getObjectFromPrefs(prefs, "affine_transform_"
                        + MMStudio.getInstance().getCore().getCurrentPixelSizeConfig(), null);
                //set map origin to current stage position
                double[] matrix = new double[6];
                transform.getMatrix(matrix);
                matrix[4] = currentCenterX;
                matrix[5] = currentCenterY;
                transform = new AffineTransform(matrix);
            } catch (Exception ex) {
                ReportingUtils.logError(ex);
                ReportingUtils.showError("Couldnt get affine transform");
            }

            //convert pixel displacement of center of new grid to new center stage position
            Point2D.Double pixelPos = new Point2D.Double(xPixelDispFromCenter, yPixelDispFromCenter);
            Point2D.Double stagePos = new Point2D.Double();
            transform.transform(pixelPos, stagePos);
            return stagePos;
        } catch (Exception e) {
            ReportingUtils.showError("Couldn't convert pixel coordinates to stage coordinates");
            return null;
        }
    }

    private void makeGridOverlay(int centerX, int centerY) {
        Overlay overlay = vad_.getImagePlus().getOverlay();
        if (overlay == null || overlay.size() == 0) {
            overlay = new Overlay();
        } else {
            overlay.clear();
        }

        int gridWidth = (Integer) gridXSpinner_.getValue();
        int gridHeight = (Integer) gridYSpinner_.getValue();
        int xOverlap = SettingsDialog.getXOverlap();
        int yOverlap = SettingsDialog.getYOverlap();
        int roiWidth = (gridWidth * tileWidth_) - (gridWidth - 1) * xOverlap;
        int roiHeight = gridHeight * tileHeight_ - (gridHeight - 1) * yOverlap;

        Roi rectangle = new Roi(centerX - roiWidth / 2, centerY - roiHeight / 2, roiWidth, roiHeight);
        rectangle.setStrokeWidth(20f);
        overlay.add(rectangle);
        vad_.getImagePlus().setOverlay(overlay);
    }

    private void gridSizeChanged() {
        //resize exisiting grid but keep centered on same area
        Overlay overlay = vad_.getImagePlus().getOverlay();
        if (overlay == null || overlay.get(0) == null) {
            return;
        }
        Rectangle2D oldBounds = overlay.get(0).getFloatBounds();
        int centerX = (int) oldBounds.getCenterX();
        int centerY = (int) oldBounds.getCenterY();
        makeGridOverlay(centerX, centerY);
    }

    @Override
    public void imageReceived(TaggedImage taggedImage) {
        try {
            //duplicate so image storage doesnt see incorrect tags
            JSONObject newTags = new JSONObject(taggedImage.tags.toString());
            MDUtils.setPositionIndex(newTags, 0);
            taggedImage = new TaggedImage(taggedImage.pix, newTags);
        } catch (JSONException ex) {
            ReportingUtils.showError("Couldn't manipulate image tags for display");
        }



        if (!suspendUpdates_) {
            vad_.imageReceived(taggedImage);
        } else {
            try {
                //tSelector will be null on first frame
                if (tSelector_ != null) {
                    int frame = MDUtils.getFrameIndex(taggedImage.tags);
                    if (tSelector_.getMaximum() <= (1 + frame)) {
                        ((IMMImagePlus) vad_.getHyperImage()).setNFramesUnverified(frame + 1);
                        tSelector_.setMaximum(frame + 2);
                        tSelector_.invalidate();
                        tSelector_.validate();
                    }
                }
            } catch (Exception ex) {
                ReportingUtils.showError("Couldn't suspend updates");
            }
        }

    }

    @Override
    public void imagingFinished(String path) {
        vad_.imagingFinished(path);
    }

    class Controls extends DisplayControls {

        private JButton pauseButton_, abortButton_;
        private JTextField fpsField_;
        private JLabel zPosLabel_, timeStampLabel_, nextFrameLabel_, posNameLabel_;
        private JToggleButton dlOffsetsButton_, newGridButton_, gotoButton_, suspendUpdatesButton_;
        private Timer nextFrameTimer_;

        public Controls() {
            initComponents();
            nextFrameTimer_ = new Timer(1000, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    long nextImageTime = 0;
                    try {
                        nextImageTime = vad_.getNextWakeTime();
                    } catch (NullPointerException ex) {
                        nextFrameTimer_.stop();
                    }
                    if (!vad_.acquisitionIsRunning()) {
                        nextFrameTimer_.stop();
                    }
                    double timeRemainingS = (nextImageTime - System.nanoTime() / 1000000) / 1000;
                    if (timeRemainingS > 0 && vad_.acquisitionIsRunning()) {
                        nextFrameLabel_.setText("Next frame: " + NumberUtils.doubleToDisplayString(1 + timeRemainingS) + " s");
                        nextFrameTimer_.setDelay(100);
                    } else {
                        nextFrameTimer_.setDelay(1000);
                        nextFrameLabel_.setText("");
                    }

                }
            });
            nextFrameTimer_.start();
        }

        public void acquiringImagesUpdate(boolean state) {
            abortButton_.setEnabled(state);
            pauseButton_.setEnabled(state);
        }

        private void updateFPS() {
            try {
                double fps = NumberUtils.displayStringToDouble(fpsField_.getText());
                vad_.setPlaybackFPS(fps);
            } catch (ParseException ex) {
            }
        }

        public void updateSelectedPosition(String posName) {
            posNameLabel_.setText(posName);
        }

        @Override
        public void imagesOnDiskUpdate(boolean bln) {
//         abortButton_.setEnabled(bln);
//         pauseButton_.setEnabled(bln);
        }

        @Override
        public void setStatusLabel(String string) {
        }

        private void updateLabels(JSONObject tags) {
            //Z position label
            String zPosition = "";
            try {
                zPosition = NumberUtils.doubleToDisplayString(MDUtils.getZPositionUm(tags));
            } catch (Exception e) {
               // Do nothing...
            }
            zPosLabel_.setText("Z Position: " + zPosition + " um ");

            //time label
            try {
                int ms = (int) tags.getDouble("ElapsedTime-ms");
                int s = ms / 1000;
                int min = s / 60;
                int h = min / 60;

                String time = twoDigitFormat(h) + ":" + twoDigitFormat(min % 60)
                        + ":" + twoDigitFormat(s % 60) + "." + threeDigitFormat(ms % 1000);
                timeStampLabel_.setText("Elapsed time: " + time + " ");
            } catch (JSONException ex) {
                ReportingUtils.logError("MetaData did not contain ElapsedTime-ms field");
            }
        }

        private String twoDigitFormat(int i) {
            String ret = i + "";
            if (ret.length() == 1) {
                ret = "0" + ret;
            }
            return ret;
        }

        private String threeDigitFormat(int i) {
            String ret = i + "";
            if (ret.length() == 1) {
                ret = "00" + ret;
            } else if (ret.length() == 2) {
                ret = "0" + ret;
            }
            return ret;
        }

        @Override
        public void newImageUpdate(JSONObject tags) {
            if (tags == null) {
                return;
            }
            updateLabels(tags);
        }

        private void clearSelectedButtons() {
            newGridButton_.setSelected(false);
            newGridButton_.getActionListeners()[0].actionPerformed(null);
            dlOffsetsButton_.setSelected(false);
            dlOffsetsButton_.getActionListeners()[0].actionPerformed(null);
            gotoButton_.setSelected(false);
            gotoButton_.getActionListeners()[0].actionPerformed(null);
            IJ.setTool(Toolbar.SPARE2);
        }

        private void initComponents() {
            setPreferredSize(new java.awt.Dimension(700, 66));
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            final JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT)),
                    row2 = new JPanel(new FlowLayout(FlowLayout.LEFT)),
                    row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
            this.add(row1);
            this.add(row2);

            gridXSpinner_ = new JSpinner();
            gridXSpinner_.setModel(new SpinnerNumberModel(2, 1, 1000, 1));
            gridXSpinner_.setPreferredSize(new Dimension(35, 24));
            gridYSpinner_ = new JSpinner();
            gridYSpinner_.setModel(new SpinnerNumberModel(2, 1, 1000, 1));
            gridYSpinner_.setPreferredSize(new Dimension(35, 24));
            gridXSpinner_.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    gridSizeChanged();
                }
            });
            gridYSpinner_.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    gridSizeChanged();
                }
            });
            final JLabel gridLabel = new JLabel(" grid");
            final JLabel byLabel = new JLabel("by");
            gridLabel.setEnabled(false);
            byLabel.setEnabled(false);
            gridXSpinner_.setEnabled(false);
            gridYSpinner_.setEnabled(false);

            final JButton createGridButton = new JButton("Create");
            createGridButton.setEnabled(false);
            createGridButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    createGrid();
                }
            });


            newGridButton_ = new JToggleButton("New grid");
            newGridButton_.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (newGridButton_.isSelected()) {
                        clearSelectedButtons();
                        newGridMode_ = true;
                        newGridButton_.setSelected(true);
                        makeGridOverlay(vad_.getImagePlus().getWidth() / 2, vad_.getImagePlus().getHeight() / 2);
                        newGridButton_.setText("Cancel");
                    } else {
                        newGridMode_ = false;
                        vad_.getImagePlus().setOverlay(null);
                        vad_.getImagePlus().getCanvas().repaint();
                        newGridButton_.setText("New grid");
                    }
                    gridLabel.setEnabled(newGridMode_);
                    byLabel.setEnabled(newGridMode_);
                    gridXSpinner_.setEnabled(newGridMode_);
                    gridYSpinner_.setEnabled(newGridMode_);
                    createGridButton.setEnabled(newGridMode_);
                }
            });

            gotoButton_ = new JToggleButton("Goto");
            gotoButton_.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (gotoButton_.isSelected()) {
                        clearSelectedButtons();
                        gotoButton_.setSelected(true);
                        gotoMode_ = true;
                        vad_.getImagePlus().getCanvas().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), 0);
                    } else {
                        gotoMode_ = false;
                        vad_.getImagePlus().getCanvas().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR), 0);
                    }
                }
            });

            dlOffsetsButton_ = new JToggleButton("Set depth list offsets");
            dlOffsetsButton_.setPreferredSize(new Dimension(133, 23));
            posNameLabel_ = new JLabel() {
                @Override
                public void setText(String s) {
                    Controls.this.invalidate();
                    super.setText(s);
                    Controls.this.validate();
                }
            };
            dlOffsetsButton_.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (dlOffsetsButton_.isSelected()) {
                        clearSelectedButtons();
                        dlOffsetsButton_.setSelected(true);
                        dlOffsetsButton_.setText("Select XY position");
                        positionSelectMode_ = true;
                    } else {
                        posNameLabel_.setText("");
                        dlOffsetsButton_.setText("Set depth list offsets");
                        positionSelectMode_ = false;
                        vad_.getImagePlus().getCanvas().setOverlay(null);
                        selectedPositions_.clear();
                    }
                    drawDepthListOverlay(vad_.getImagePlus().getCanvas());
                }
            });

            suspendUpdatesButton_ = new JToggleButton("Suspend updates");
            suspendUpdatesButton_.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (suspendUpdatesButton_.isSelected()) {
                        suspendUpdatesButton_.setText("Resume updates");
                        suspendUpdates_ = true;
                    } else {
                        suspendUpdatesButton_.setText("Suspend updates");
                        suspendUpdates_ = false;
                    }
                }
            });


            //button area
            abortButton_ = new JButton();
            abortButton_.setBackground(new java.awt.Color(255, 255, 255));
            abortButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/cancel.png"))); // NOI18N
            abortButton_.setToolTipText("Abort acquisition");
            abortButton_.setFocusable(false);
            abortButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
            abortButton_.setMaximumSize(new java.awt.Dimension(25, 25));
            abortButton_.setMinimumSize(new java.awt.Dimension(25, 25));
            abortButton_.setPreferredSize(new java.awt.Dimension(25, 25));
            abortButton_.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
            abortButton_.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        JavaUtils.invokeRestrictedMethod(vad_, VirtualAcquisitionDisplay.class, "abort");
                    } catch (Exception ex) {
                        ReportingUtils.showError("Couldn't abort. Try pressing stop on Multi-Dimensional acquisition Window");
                    }
                }
            });

            pauseButton_ = new JButton();
            pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
            pauseButton_.setToolTipText("Pause acquisition");
            pauseButton_.setFocusable(false);
            pauseButton_.setMargin(new java.awt.Insets(0, 0, 0, 0));
            pauseButton_.setMaximumSize(new java.awt.Dimension(25, 25));
            pauseButton_.setMinimumSize(new java.awt.Dimension(25, 25));
            pauseButton_.setPreferredSize(new java.awt.Dimension(25, 25));
            pauseButton_.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    try {
                        JavaUtils.invokeRestrictedMethod(vad_, VirtualAcquisitionDisplay.class, "pause");
                    } catch (Exception ex) {
                        ReportingUtils.showError("Couldn't pause");
                    }
                    if (eng_.isPaused()) {
                        pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/resultset_next.png"))); // NOI18N
                    } else {
                        pauseButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/control_pause.png"))); // NOI18N
                    }
                }
            });

            //text area
            zPosLabel_ = new JLabel("Z position:") {
                @Override
                public void setText(String s) {
                    Controls.this.invalidate();
                    super.setText(s);
                    Controls.this.validate();
                }
            };
            timeStampLabel_ = new JLabel("Elapsed time:") {
                @Override
                public void setText(String s) {
                    Controls.this.invalidate();
                    super.setText(s);
                    Controls.this.validate();
                }
            };
            nextFrameLabel_ = new JLabel("Next frame: ") {
                @Override
                public void setText(String s) {
                    Controls.this.invalidate();
                    super.setText(s);
                    Controls.this.validate();
                }
            };
            fpsField_ = new JTextField();
            fpsField_.setText("7");
            fpsField_.setToolTipText("Set the speed at which the acquisition is played back.");
            fpsField_.setPreferredSize(new Dimension(25, 18));
            fpsField_.addFocusListener(new java.awt.event.FocusAdapter() {
                public void focusLost(java.awt.event.FocusEvent evt) {
                    updateFPS();
                }
            });
            fpsField_.addKeyListener(new java.awt.event.KeyAdapter() {
                public void keyReleased(java.awt.event.KeyEvent evt) {
                    updateFPS();
                }
            });
            JLabel fpsLabel = new JLabel("Animation playback FPS: ");


            row1.add(newGridButton_);
            row1.add(gridXSpinner_);
            row1.add(byLabel);
            row1.add(gridYSpinner_);
            row1.add(gridLabel);
            row1.add(createGridButton);
            row1.add(gotoButton_);

            row1.add(dlOffsetsButton_);
            row1.add(posNameLabel_);

            row1.add(suspendUpdatesButton_);


            row2.add(abortButton_);
            row2.add(pauseButton_);
            row2.add(fpsLabel);
            row2.add(fpsField_);
            row2.add(zPosLabel_);
            row2.add(timeStampLabel_);
            row2.add(nextFrameLabel_);
        }
    }
}
