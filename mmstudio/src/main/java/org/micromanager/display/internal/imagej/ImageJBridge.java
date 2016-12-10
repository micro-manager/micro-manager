// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.internal.imagej;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.io.FileInfo;
import ij.measure.Calibration;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.display.internal.DisplayUIController;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 * Bridge to ImageJ1 image viewer window.
 *
 * This class manages our customized ImageJ objects, which we use to plug into
 * ImageJ's concept of an image viewer window.
 *
 * The {@code ImageJBridge} facade object manages an {@code ImagePlus} (ImageJ's
 * wrapper for an image data set), an {@code MMVirtualStack} (our customized
 * proxy for ImageJ's data provider object), a {@code ProxyImageWindow} (our
 * proxy subclass of ImageJ's {@code ImageWindow} (actually
 * {@code StackWindow}), which is never displayed but used to trick ImageJ into
 * thinking it's communicating with a window), and controls an
 * {@code MMImageCanvas} (our extended version of ImageJ's
 * {@code ImageCanvas}).
 *
 * @author Mark Tsuchida
 */
public final class ImageJBridge {
   private final DisplayUIController uiController_;

   // Our child objects on the ImageJ side. These are created and owned by this
   // class. There are three events in the lifetime of these objects: creation,
   // switch to composite image objects, and destruction.
   //
   // Creation:
   // The ImagePlus can be created independently. The VirtualStack is created
   // by supplying the ImagePlus for which it acts as a backing store. Creation
   // of DummyImageWindow is somewhat convoluted because its superclass's
   // constructor requires access back to our Datastore.
   // TODO Document MMImageCanvas creation
   //
   // Shift to composite image objects:
   // The ImagePlus, which was initially an MMImagePlus, is discarded and
   // replaced with an MMCompositeImage (subclass of ImageJ's CompositeImage,
   // which also derives from ImagePlus). The MMVirtualStack and
   // DummyImageWindow are reused, although the window is unregistered and
   // reregistered with ImageJ's WindowManager. The MMImageCanvas also
   // survives the transition.
   //
   // Destruction:
   // TODO Document once implemented, as it's totally unclear what happens now.
   //
   // Policy for what goes in ImageJBridge and what goes in our subclasses of
   // ImageJ classes:
   // - We avoid having references to the same objects from every object
   //   involved. All genuinely display-related state is owned by our parent
   //   (DisplayController), and anything ImageJ-related is owned by us.
   //   MMImagePlus and other subclasses of ImageJ classes hold the minimal
   //   state needed to manage their actions.
   // - Thus, the need for bidirectional state synchronization arises only for
   //   state maintained by both DisplayController (or DisplayFrameController)
   //   and ImageJ.
   private ImagePlus imagePlus_;
   private MMVirtualStack proxyStack_;
   private ProxyImageWindow proxyWindow_;
   private MMImageCanvas canvas_;

   // Get a copy of ImageCanvas's zoom levels
   private static final List<Double> ijZoomLevels_ = new ArrayList<Double>();
   static {
      double factor = 1.0;
      ijZoomLevels_.add(factor);
      for (;;) {
         factor = ImageCanvas.getLowerZoomLevel(factor);
         if (factor == ijZoomLevels_.get(0)) {
            break;
         }
         ijZoomLevels_.add(0, factor);
      }
      factor = ijZoomLevels_.get(ijZoomLevels_.size() - 1);
      for (;;) {
         factor = ImageCanvas.getHigherZoomLevel(factor);
         if (factor == ijZoomLevels_.get(ijZoomLevels_.size() - 1)) {
            break;
         }
         ijZoomLevels_.add(factor);
      }
   }


   @MustCallOnEDT
   public static ImageJBridge create(DisplayUIController parent) {
      ImageJBridge instance = new ImageJBridge(parent);
      instance.initialize();
      return instance;
   }

   private ImageJBridge(DisplayUIController parent) {
      uiController_ = parent;
   }

   @MustCallOnEDT
   public ImagePlus getIJImagePlus() {
      return imagePlus_;
   }

   @MustCallOnEDT
   public MMImageCanvas getIJImageCanvas() {
      return canvas_;
   }

   @MustCallOnEDT
   private void initialize() {
      // VirtualStack, ImageJ's data source object, does not depend on the
      // ImagePlus, ImageCanvas, and ImageWindow, so we create it first.
      proxyStack_ = MMVirtualStack.create(this);

      // Multiple images (coords) may have already arrived at the UI controller
      // if images are added to the datastore at a high rate. However, during
      // the object creation that follows, we need to pretend that the stack
      // only contains one image. This is because ImagePlus will assign the
      // size of the stack to the number of Z slices (see the ImagePlus method
      // verifyDimensions(), which is called from all over the place).
      proxyStack_.setSingleImageMode(true);

      // ImagePlus, ImageCanvas, and ImageWindow are all interdependent, but
      // need to be created in that order.

      imagePlus_ = MMImagePlus.create(this);
      // TODO ImageJ Metadata: see old DefaultDisplayWindow.setImagePlusMetadata()

      imagePlus_.setStack(uiController_.getDisplayController().getName(),
            proxyStack_);
      imagePlus_.setOpenAsHyperStack(true);

      canvas_ = MMImageCanvas.create(this);
      // TODO Set canvas's zoom to current display settings (1.0 if null).

      proxyWindow_ = ProxyImageWindow.create(this);
      imagePlus_.setWindow(proxyWindow_);

      // Undo the temporary pretence
      proxyStack_.setSingleImageMode(false);
   }

   @MustCallOnEDT
   private void switchToCompositeImage() {
      proxyStack_.setSingleImageMode(true);
      MMCompositeImage composite = MMCompositeImage.create(this, imagePlus_);
      composite.setOpenAsHyperStack(true);
      // TODO Metadata (formerly setImagePlusMetadata(ijImage_);)
      imagePlus_ = composite;

      // Much as we would like to reuse our canvas, ImageCanvas does not allow
      // its ImagePlus to be swapped.
      MMImageCanvas oldCanvas = canvas_;
      canvas_ = null; // Suppress canvas size changed events
      MMImageCanvas newCanvas = MMImageCanvas.create(this);
      newCanvas.setSize(oldCanvas.getSize());
      newCanvas.setMagnification(oldCanvas.getMagnification());
      newCanvas.setSourceRect(oldCanvas.getSrcRect());
      canvas_ = newCanvas;

      imagePlus_.setWindow(proxyWindow_);
      proxyStack_.setSingleImageMode(false);
      uiController_.canvasNeedsSwap();
   }

   @MustCallOnEDT
   public void mm2ijWindowClosed() {
      imagePlus_.changes = false; // Avoid "Save?" dialog
      proxyWindow_ = null;
      canvas_ = null;
      imagePlus_.close(); // Also closes the window
      imagePlus_ = null;
      proxyStack_ = null;
   }

   @MustCallOnEDT
   public void mm2ijSetTitle(String title) {
      imagePlus_.setTitle(title);
   }

   @MustCallOnEDT
   public void mm2ijWindowActivated() {
      WindowManager.setCurrentWindow(proxyWindow_);
   }

   @MustCallOnEDT
   void ij2mmToFront() {
      // TODO Use listener
      uiController_.toFront();
   }

   @MustCallOnEDT
   void ij2mmSetVisible(boolean visible) {
      // TODO Use listener
      uiController_.setVisible(visible);
   }

   @MustCallOnEDT
   private void mm2ijSetDisplayAxisExtents(
         int nChannels, int nZSlices, int nTimePoints)
   {
      int oldNChannels =
            ((IMMImagePlus) imagePlus_).getNChannelsWithoutSideEffect();

      if (nChannels > 1 && nChannels != oldNChannels) {
         if (!(imagePlus_ instanceof CompositeImage)) {
            switchToCompositeImage();
         }
      }

      ((IMMImagePlus) imagePlus_).setDimensionsWithoutUpdate(
            nChannels, nZSlices, nTimePoints);

      if (imagePlus_ instanceof MMCompositeImage && nChannels != oldNChannels) {
         // Tell ImageJ to update internal data for new channel count
         ((MMCompositeImage) imagePlus_).reset();
      }
   }

   @MustCallOnEDT
   public void mm2ijEnsureDisplayAxisExtents()
   {
      int newNChannels = getMMNumberOfChannels();
      int newNSlices = getMMNumberOfZSlices();
      int newNFrames = getMMNumberOfTimePoints();
      int oldNChannels =
            ((IMMImagePlus) imagePlus_).getNChannelsWithoutSideEffect();
      int oldNSlices =
            ((IMMImagePlus) imagePlus_).getNSlicesWithoutSideEffect();
      int oldNFrames =
            ((IMMImagePlus) imagePlus_).getNFramesWithoutSideEffect();

      if (newNChannels > oldNChannels ||
            newNSlices > oldNSlices ||
            newNFrames > oldNFrames)
      {
         mm2ijSetDisplayAxisExtents(Math.max(newNChannels, oldNChannels),
               Math.max(newNSlices, oldNSlices),
               Math.max(newNFrames, oldNFrames));
      }
   }

   @MustCallOnEDT
   public void mm2ijSetDisplayPosition(Coords coords) {
      int ijFlatIndex = getIJFlatIndexForMMCoords(coords);

      // Calling imagePlus_.setSlice() would result in a call to
      // imagePlus_.updateAndRepaintWindow() _if_ we are setting a position
      // that differs from the ImagePlus's current position.
      // So we need to force a repaint by other means anyway (see below).
      // So we call the no-update version here.
      // Note also that we cannot use setPositionWithoutUpdate() here, because
      // that method has side effects on the ImagePlus's axis extents.
      imagePlus_.setSliceWithoutUpdate(ijFlatIndex);

      // The way to get ImagePlus to repaint even when the position hasn't
      // changed is to refresh its internal ImageProcessor that holds its
      // currently displayed image.
      // Unfortunately ImagePlus does not provide a way to invalidate its
      // ImageProcessor without other side effects.
      // So we explicitly set its ImageProcessor to what it would normally
      // fetch from the ImageStack.
      // However, it turns out that imagePlus_.setProcessor(), when called
      // with the stack having a size of 1, will unset the stack for the
      // ImagePlus. So in that case only, call setStack() instead (which is
      // less efficient but internally calls the equivalent of setProcessor()
      // with the processor fetched from the stack and without unsetting the
      // stack). Whew!
      if (proxyStack_.getSize() == 1) {
         imagePlus_.setStack(proxyStack_);
      }
      else {
         imagePlus_.setProcessor(proxyStack_.getProcessor(ijFlatIndex));
      }
   }

   public void repaint() {
      imagePlus_.updateAndRepaintWindow();
   }

   void ijPaintDidFinish() {
      uiController_.paintDidFinish();
   }

   Coords getMMCoordsForIJFlatIndex(int flatIndex) {
      int[] ijPos3d = imagePlus_.convertIndexToPosition(flatIndex);
      int channel = ijPos3d[0] - 1;
      int zSlice = ijPos3d[1] - 1;
      int timePoint = ijPos3d[2] - 1;

      Coords.CoordsBuilder cb =
            uiController_.getMMPrincipalDisplayedCoords().copy();
      if (uiController_.isAxisDisplayed(Coords.CHANNEL)) {
         cb.channel(channel);
      }
      if (uiController_.isAxisDisplayed(Coords.Z)) {
         cb.z(zSlice);
      }
      if (uiController_.isAxisDisplayed(Coords.TIME)) {
         cb.time(timePoint);
      }
      return cb.build();
   }

   int getIJFlatIndexForMMCoords(Coords coords) {
      int channel = Math.max(0, coords.getChannel());
      int zSlice = Math.max(0, coords.getZ());
      int timePoint = Math.max(0, coords.getTime());
      int nChannels = getMMNumberOfChannels();
      int nZSlices = getMMNumberOfZSlices();
      int nTimePoints = getMMNumberOfTimePoints();
      return timePoint * nZSlices * nChannels +
            zSlice * nChannels +
            channel +
            1;
   }

   int getMMNumberOfTimePoints() {
      return Math.max(1, uiController_.getDisplayedAxisLength(Coords.TIME));
   }

   int getMMNumberOfZSlices() {
      return Math.max(1, uiController_.getDisplayedAxisLength(Coords.Z));
   }

   int getMMNumberOfChannels() {
      return Math.max(1, uiController_.getDisplayedAxisLength(Coords.CHANNEL));
   }

   int getMMWidth() {
      return uiController_.getImageWidth();
   }

   int getMMHeight() {
      return uiController_.getImageHeight();
   }

   Image getMMImage(Coords coords) {
      // Normally, return the currently displayed images cached by the UI
      // controller
      List<Image> images = uiController_.getDisplayedImages();
      for (Image image : images) {
         if (coords.equals(image.getCoords())) {
            return image;
         }
      }
      return getBlankImage(coords);
   }

   private Image getBlankImage(Coords coords) {
      // TODO Clearly this should be factored out as a general utility method
      // in org.micromanager.data.
      Image template =
            uiController_.getDisplayController().getDatastore().getAnyImage();
      if (template == null) {
         throw new IllegalStateException("Image requested from empty dataset");
      }
      Object templatePixels = template.getRawPixels();
      Object blankPixels;
      if (templatePixels instanceof byte[]) {
         blankPixels = new byte[((byte[]) templatePixels).length];
      }
      else if (templatePixels instanceof short[]) {
         blankPixels = new short[((short[]) templatePixels).length];
      }
      else if (templatePixels instanceof int[]) {
         blankPixels = new int[((int[]) templatePixels).length];
      }
      else if (templatePixels instanceof long[]) {
         blankPixels = new long[((long[]) templatePixels).length];
      }
      else {
         throw new UnsupportedOperationException("Pixel buffer of unknown type");
      }
      return new DefaultImage(blankPixels,
            template.getWidth(), template.getHeight(),
            template.getBytesPerPixel(), template.getNumComponents(), coords,
            new DefaultMetadata.Builder().build());
   }

   @MustCallOnEDT
   public void mm2ijSetMetadata(double pixelWidthUm, double pixelHeightUm,
         double pixelDepthUm, double frameIntervalMs,
         int width, int height,
         String directory, String fileName)
   {
      Calibration cal = new Calibration(imagePlus_);
      if (pixelWidthUm * pixelHeightUm == 0.0) {
         cal.setUnit("px");
         cal.pixelWidth = cal.pixelHeight = 1.0;
      }
      else {
         cal.setUnit("um");
         cal.pixelWidth = pixelWidthUm;
         cal.pixelHeight = pixelHeightUm;
      }
      imagePlus_.setCalibration(cal);

      FileInfo finfo = new FileInfo();
      finfo.width = width;
      finfo.height = height;
      finfo.directory = directory;
      finfo.fileName = fileName;
      imagePlus_.setFileInfo(finfo);
   }

   @MustCallOnEDT
   public boolean isIJZoomedAllTheWayOut() {
      return canvas_.getMagnification() <= ijZoomLevels_.get(0) + 0.001;
   }

   @MustCallOnEDT
   public boolean isIJZoomedAllTheWayIn() {
      return canvas_.getMagnification() >=
            ijZoomLevels_.get(ijZoomLevels_.size() - 1) - 0.001;
   }

   @MustCallOnEDT
   public double getIJZoom() {
      return canvas_.getMagnification();
   }

   @MustCallOnEDT
   public void setIJZoom(double factor) {
      double originalFactor = getIJZoom();
      if (factor == originalFactor) {
         return;
      }

      // We need to manually adjust the source rect of the ImageCanvas's view
      // port. This requires knowledge of the size of the canvas _after_ the
      // zoom has changed -- information we don't yet have. However, we
      // separate the zooming from the later window (and canvas) size
      // adjustment, so here we only need to compute the _ideal_ canvas size
      // after the zoom. If done right (all on the EDT), the window size
      // adjustment should happen before any repaint occurs.
      Dimension newIdealCanvasSize =
            computeIdealCanvasSizeAfterZoom(factor, originalFactor,
                  getMMWidth(), getMMHeight(),
                  canvas_.getSize().width, canvas_.getSize().height);

      // Center the new source rect where the precious source rect was, to the
      // extent it fits in the image
      Rectangle newSourceRect = computeSourceRect(factor,
            getMMWidth(), getMMHeight(),
            newIdealCanvasSize.width, newIdealCanvasSize.height,
            canvas_.getSrcRect());

      // Make sure to update zoom and source rect before setting size, since
      // setSize necessarily must "fix" the source rect.
      canvas_.setMagnification(factor);
      canvas_.setSourceRect(newSourceRect);
      canvas_.setSize(newIdealCanvasSize);
      canvas_.repaint();
   }

   @MustCallOnEDT
   public void mm2ijZoomIn() {
      setIJZoom(ImageCanvas.getHigherZoomLevel(getIJZoom()));
   }

   @MustCallOnEDT
   public void mm2ijZoomOut() {
      setIJZoom(ImageCanvas.getLowerZoomLevel(getIJZoom()));
   }

   void ij2mmZoomDidChange(double factor) {
      if (canvas_ == null) {
         return; // In the process of swapping the canvas
      }
      uiController_.uiDidSetZoom(factor);
   }

   void ij2mmCanvasDidChangeSize() {
      if (canvas_ == null) {
         return; // In the process of swapping the canvas
      }
      uiController_.canvasDidChangeSize();
   }

   Rectangle computeSourceRectForCanvasSize(double zoomRatio,
         int width, int height, Rectangle oldSourceRect)
   {
      return computeSourceRect(zoomRatio, getMMWidth(), getMMHeight(),
            width, height, oldSourceRect);
   }

   private static Dimension computeIdealCanvasSizeAfterZoom(
         double newZoomRatio, double oldZoomRatio,
         int imageWidth, int imageHeight,
         int oldCanvasWidth, int oldCanvasHeight)
   {
      Dimension ret = new Dimension(
            (int) Math.floor(imageWidth * newZoomRatio),
            (int) Math.floor(imageHeight * newZoomRatio));

      // Avoid enlarging window if more than 5% of one dimension is already
      // occluded
      if (oldCanvasWidth < 0.95 * Math.floor(imageWidth * oldZoomRatio)) {
         ret.width = oldCanvasWidth;
      }
      if (oldCanvasHeight < 0.95 * Math.floor(imageHeight* oldZoomRatio)) {
         ret.height = oldCanvasHeight;
      }
      return ret;
   }

   // dst width and height must not be larger than image
   private static Rectangle computeSourceRect(double zoomRatio,
         int imageWidth, int imageHeight,
         int dstWidth, int dstHeight,
         Rectangle oldSourceRect)
   {
      Rectangle ret = new Rectangle();

      // First, compute a rect without regard to the image bounds.
      // Use ceiling to ensure all of the dest rect can be covered by the
      // scaled source rect, but don't exceed image size
      ret.width = Math.min(imageWidth,
            (int) Math.ceil((dstWidth + 1) / zoomRatio));
      ret.height = Math.min(imageHeight,
            (int) Math.ceil((dstHeight + 1) / zoomRatio));
      double centerX = oldSourceRect.x + 0.5 * oldSourceRect.width;
      double centerY = oldSourceRect.y + 0.5 * oldSourceRect.height;
      ret.x = (int) Math.round(centerX - 0.5 * ret.width);
      ret.y = (int) Math.round(centerY - 0.5 * ret.height);

      // Shift the rect so that it fits within the image bounds
      if (ret.x < 0) {
         ret.x = 0;
      }
      if (ret.y < 0) {
         ret.y = 0;
      }
      if (ret.x + ret.width > imageWidth) {
         ret.x -= imageWidth - ret.width;
      }
      if (ret.y + ret.height > imageHeight) {
         ret.y -= imageHeight - ret.height;
      }

      return ret;
   }

   void ij2mmMouseEnteredCanvas(MouseEvent e) {
      uiController_.updatePixelInfoUI(
            computeImageRectForCanvasPoint(e.getPoint()));
   }

   void ij2mmMouseExitedCanvas(MouseEvent e) {
      uiController_.updatePixelInfoUI(null);
   }

   void ij2mmMouseDraggedOnCanvas(MouseEvent e) {
      uiController_.updatePixelInfoUI(
            computeImageRectForCanvasPoint(e.getPoint()));
   }

   void ij2mmMouseMovedOnCanvas(MouseEvent e) {
      uiController_.updatePixelInfoUI(
            computeImageRectForCanvasPoint(e.getPoint()));
   }

   private Rectangle computeImageRectForCanvasPoint(Point canvasPoint) {
      double zoomRatio = canvas_.getMagnification();
      Rectangle sourceRect = canvas_.getSrcRect();
      double x = canvasPoint.x / zoomRatio + sourceRect.x;
      double y = canvasPoint.y / zoomRatio + sourceRect.y;
      double widthAndHeight = 1.0 / zoomRatio;
      return new Rectangle((int) Math.floor(x), (int) Math.floor(y),
            (int) Math.ceil(widthAndHeight), (int) Math.ceil(widthAndHeight));
   }
}