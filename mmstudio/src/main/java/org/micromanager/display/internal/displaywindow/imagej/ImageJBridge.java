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

package org.micromanager.display.internal.displaywindow.imagej;

import com.google.common.base.Preconditions;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.Menus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.MenuBar;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.imglib2.display.ColorTable8;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.display.internal.displaywindow.DisplayUIController;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.internal.utils.JavaUtils;
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

   // How to set up intensity scaling and/or LUT for the current ImagePlus,
   // which may be monochrome, composite, or RGB.
   private ColorModeStrategy colorModeStrategy_;

   private Roi lastSeenRoi_;
   private Rectangle lastSeenRoiRect_;

   // Get a copy of ImageCanvas's zoom levels
   private static final List<Double> IJ_ZOOM_LEVELS = new ArrayList<Double>();
   static {
      double factor = 1.0;
      IJ_ZOOM_LEVELS.add(factor);
      for (;;) {
         factor = ImageCanvas.getLowerZoomLevel(factor);
         if (factor == IJ_ZOOM_LEVELS.get(0)) {
            break;
         }
         IJ_ZOOM_LEVELS.add(0, factor);
      }
      factor = IJ_ZOOM_LEVELS.get(IJ_ZOOM_LEVELS.size() - 1);
      for (;;) {
         factor = ImageCanvas.getHigherZoomLevel(factor);
         if (factor == IJ_ZOOM_LEVELS.get(IJ_ZOOM_LEVELS.size() - 1)) {
            break;
         }
         IJ_ZOOM_LEVELS.add(factor);
      }
   }


   @MustCallOnEDT
   public static ImageJBridge create(final DisplayUIController parent, 
           final ImagesAndStats images) {
      ImageJBridge instance = new ImageJBridge(parent, images);
      instance.initialize();
      return instance;
   }

   private ImageJBridge(DisplayUIController parent, ImagesAndStats images) {
      uiController_ = parent;
      if (images != null && images.getRequest().getImages().size() > 0 && 
              images.getRequest().getImage(0).getNumComponents() > 1) {
         colorModeStrategy_ = RGBColorModeStrategy.create();
      }
      else {
         colorModeStrategy_ = GrayscaleColorModeStrategy.create();
      }
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

      imagePlus_.setStack("New µManager-ImageJ Bridge", proxyStack_);
      imagePlus_.setOpenAsHyperStack(true);
      applyColorMode(colorModeStrategy_);

      canvas_ = MMImageCanvas.create(this);

      proxyWindow_ = ProxyImageWindow.create(this);
      imagePlus_.setWindow(proxyWindow_);

      // Undo the temporary pretence
      proxyStack_.setSingleImageMode(false);
      
      mm2ijSetMetadata();
   }

   @MustCallOnEDT
   private void switchToCompositeImage() {
      proxyStack_.setSingleImageMode(true);
      MMCompositeImage composite = MMCompositeImage.create(this, imagePlus_);
      composite.setOpenAsHyperStack(true);
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
      colorModeStrategy_.releaseImagePlus();
      proxyStack_ = null;
   }

   @MustCallOnEDT
   public void mm2ijSetTitle(String title) {
      imagePlus_.setTitle(title);
   }

   @MustCallOnEDT
   public void mm2ijWindowActivated() {
      // On Mac OS X, where the menu bar is not attached to windows, we need to
      // switch to ImageJ's menu bar when a viewer window has focus.
      if (JavaUtils.isMac()) {
         MenuBar ijMenuBar = Menus.getMenuBar();
         MenuBar curMenuBar = uiController_.getFrame().getMenuBar();
         // Avoid call to setMenuBar() if our JFrame already has the right
         // menu bar (e.g. because we are switching between MM and IJ windows),
         // because setMenuBar() is very, very, slow in at least some Java
         // versions on OS X. See
         // http://imagej.1557.x6.nabble.com/java-8-and-OSX-td5016839.html and
         // links therein (although I note that the slowness is seen even with
         // Java 6 on Yosemite (10.10)).
         if (ijMenuBar != null && ijMenuBar != curMenuBar) {
            uiController_.getFrame().setMenuBar(ijMenuBar);
         }
      }

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
   public int getIJNumberOfChannels() {
      return ((IMMImagePlus) imagePlus_).getNChannelsWithoutSideEffect();
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
         applyColorMode(colorModeStrategy_);
      }
   }

   @MustCallOnEDT
   public void mm2ijEnsureDisplayAxisExtents() {
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

      // Since setSliceWithoutUpdate (or setSlice, for that matter) does not
      // set the TZC coordinates, we need this:
      int channel = coords.hasAxis(Coords.CHANNEL) ? coords.getChannel() : 0;
      int slice = coords.hasAxis(Coords.Z) ? coords.getZ() : 0;
      int timepoint = coords.hasAxis(Coords.T) ? coords.getT() : 0;
      imagePlus_.updatePosition(channel + 1, slice + 1, timepoint + 1);

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

         if (imagePlus_ instanceof CompositeImage) {
            // This is an incantation to ensure the per-channel ImageProcessor
            // instances (stored in CompositeImage.cip) get flushed.
            // Probably not necessary, but harmless.
            CompositeImage compositeImage = (CompositeImage) imagePlus_;
            int saveMode = compositeImage.getMode();
            compositeImage.setMode(CompositeImage.GRAYSCALE);
            compositeImage.setMode(saveMode);
         }
      }
      mm2ijRepaint(); // Redundant, but just in case.

      colorModeStrategy_.displayedImageDidChange();
   }

   @MustCallOnEDT
   public boolean isIJRGB() {
      return colorModeStrategy_ instanceof RGBColorModeStrategy;
   }

   @MustCallOnEDT
   private void applyColorMode(ColorModeStrategy strategy) {
      Preconditions.checkState(
            (strategy instanceof RGBColorModeStrategy) == isIJRGB());
      colorModeStrategy_ = strategy;
      colorModeStrategy_.applyModeToImagePlus(imagePlus_);
      mm2ijRepaint();
   }

   // Does not preserve min/max/gamma
   @MustCallOnEDT
   public void mm2ijSetColorModeComposite(List<Color> channelColors) {
      applyColorMode(CompositeColorModeStrategy.create(channelColors));
   }

   // Does not preserve min/max/gamma
   @MustCallOnEDT
   public void mm2ijSetColorModeGrayscale() {
      applyColorMode(GrayscaleColorModeStrategy.create());
   }

   // Does not preserve min/max/gamma
   @MustCallOnEDT
   public void mm2ijSetColorModeColor(List<Color> channelColors) {
      applyColorMode(ColorColorModeStrategy.create(channelColors));
   }

   // Does not preserve min/max/gamma
   @MustCallOnEDT
   public void mm2ijSetColorModeLUT(ColorTable8 lut) {
      applyColorMode(LUTColorModeStrategy.create(lut));
   }

   @MustCallOnEDT
   public void mm2ijSetHighlightSaturatedPixels(boolean enable) {
      colorModeStrategy_.applyHiLoHighlight(enable);
   }

   @MustCallOnEDT
   public boolean isIJColorModeColor() {
      return colorModeStrategy_.getClass() == ColorColorModeStrategy.class;
   }

   @MustCallOnEDT
   public boolean isIJColorModeComposite() {
      return colorModeStrategy_ instanceof CompositeColorModeStrategy;
   }

   @MustCallOnEDT
   public boolean isIJColorModeGrayscale() {
      return colorModeStrategy_ instanceof GrayscaleColorModeStrategy;
   }

   @MustCallOnEDT
   public boolean isIJColorModeLUT() {
      return colorModeStrategy_ instanceof LUTColorModeStrategy;
   }

   @MustCallOnEDT
   public void mm2ijSetChannelColor(int channel, Color color) {
      colorModeStrategy_.applyColor(channel, color);
      mm2ijRepaint();
   }

   @MustCallOnEDT
   public void mm2ijSetIntensityScaling(int channelOrComponent, int min, int max) {
      colorModeStrategy_.applyScaling(channelOrComponent, min, max);
      mm2ijRepaint();
   }

   @MustCallOnEDT
   public void mm2ijSetIntensityGamma(int channelOrComponent, double gamma) {
      colorModeStrategy_.applyGamma(channelOrComponent, gamma);
      mm2ijRepaint();
   }

   @MustCallOnEDT
   public void mm2ijSetVisibleChannels(int channelOrComponent, boolean visible) {
      colorModeStrategy_.applyVisibleInComposite(channelOrComponent, visible);
      mm2ijRepaint();
   }

   @MustCallOnEDT
   public void mm2ijRepaint() {
      if (canvas_ != null) {
         canvas_.setImageUpdated();
         canvas_.repaint();
      }
   }

   void paintMMOverlays(Graphics2D g, int canvasWidth, int canvasHeight,
         Rectangle sourceRect)
   {
      Rectangle canvasBounds = new Rectangle(0, 0, canvasWidth, canvasHeight);
      Rectangle2D.Float viewPort = new Rectangle2D.Float(
            sourceRect.x, sourceRect.y, sourceRect.width, sourceRect.height);
      uiController_.paintOverlays(g, canvasBounds, viewPort);
   }

   void ijPaintDidFinish() {
      uiController_.paintDidFinish();
   }

   Coords getMMCoordsForIJFlatIndex(int flatIndex) {
      int[] ijPos3d = imagePlus_.convertIndexToPosition(flatIndex);
      int channel = ijPos3d[0] - 1;
      int zSlice = ijPos3d[1] - 1;
      int timePoint = ijPos3d[2] - 1;

      Coords position = uiController_.getMMPrincipalDisplayedCoords();
      if (position == null) {
         return new DefaultCoords.Builder().build();
      }

      Coords.CoordsBuilder cb = position.copyBuilder();
      if (uiController_.isAxisDisplayed(Coords.CHANNEL)) {
         cb.channel(channel);
      }
      if (uiController_.isAxisDisplayed(Coords.Z)) {
         cb.z(zSlice);
      }
      if (uiController_.isAxisDisplayed(Coords.T)) {
         cb.time(timePoint);
      }
      return cb.build();
   }

   int getIJFlatIndexForMMCoords(Coords coords) {
      int channel = Math.max(0, coords.getChannel());
      int zSlice = Math.max(0, coords.getZ());
      int timePoint = Math.max(0, coords.getT());
      int nChannels = getMMNumberOfChannels();
      int nZSlices = getMMNumberOfZSlices();
      int nTimePoints = getMMNumberOfTimePoints();
      return timePoint * nZSlices * nChannels +
            zSlice * nChannels +
            channel +
            1;
   }

   int getMMNumberOfTimePoints() {
      return Math.max(1, uiController_.getDisplayedAxisLength(Coords.T));
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
      // This is where we map MM images to the TZC coords requested by ImageJ.
      // Normally, return the currently displayed images cached by the UI
      // controller.
      List<Image> images = uiController_.getDisplayedImages();
      IMAGES: for (Image image : images) {
         Coords c = image.getCoords();
         // We are making the assumption that no two displayed images share
         // the same TZC coordinates. This is true for mono/composite/RGB 2D
         // viewer, but may need refinement upon future ganaralizations or new
         // viewers (e.g. a Z-projecting viewer)
         for (String axis : coords.getAxes()) {
            if (coords.getIndex(axis) != c.getIndex(axis)) {
               continue IMAGES;
            }
         }
         return image;
      }
      // TODO When enabling missing image strategies, we need to map back to
      // the image assigned to the nominal coordinates
      return makeBlankImage(coords);
   }

   private Image makeBlankImage(Coords coords) {
      // TODO Clearly this should be factored out as a general utility method
      // in org.micromanager.data.
      Image template;
      try {
         template = uiController_.getDisplayController().getDataProvider().
               getAnyImage();
      }
      catch (IOException e) {
         throw new RuntimeException(e);
      }
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
   
   /**
    * Tell the ImagePlus about certain properties of our data that it doesn't
    * otherwise know how to access.
    */
   @MustCallOnEDT
   public void mm2ijSetMetadata() {
      // TODO: ImageJ only allows for one pixel size across all images, even
      // if e.g. different channels actually vary.
      // On the flipside, we only allow for square pixels, so we aren't
      // exactly perfect either.
      DataProvider dataProvider = uiController_.getDisplayController().getDataProvider();
      try {
         Image sample = dataProvider.getAnyImage();
         if (sample == null) {
            // TODO: log error
            // studio_.logs().logError("Unable to get an image for setting ImageJ metadata properties");
            return;
         }
         SummaryMetadata summaryMetadata = dataProvider.getSummaryMetadata();
         Metadata metadata = sample.getMetadata();
         if (summaryMetadata != null && metadata != null) {
            // elaborate code to protect against missing info
            double pixelSize = 0.0;
            double zStepSize = 0.0;
            double timeInterval = 0.0;
            String dir = "";
            String prefix = "";
            if (metadata.getPixelSizeUm() != null) {
               pixelSize = metadata.getPixelSizeUm();
            }
            if (summaryMetadata.getZStepUm() != null) {
               zStepSize = summaryMetadata.getZStepUm();
            }
            if (summaryMetadata.getWaitInterval() != null) { 
               timeInterval = summaryMetadata.getWaitInterval();
            }
            if (summaryMetadata.getDirectory() != null) {
               dir = summaryMetadata.getDirectory();
            } if (summaryMetadata.getPrefix() != null) {
               prefix = summaryMetadata.getPrefix();
            }
            if (prefix.equals("")) {
               prefix = uiController_.getDisplayController().getName();
            }
            mm2ijSetMetadata(pixelSize,
                    pixelSize,  // TODO: add asepct ratio here, however, that currently throws a null pointer exception
                    zStepSize,
                    timeInterval,
                    sample.getWidth(),
                    sample.getHeight(),
                    dir,
                    prefix);
         }
      } catch (IOException ioe) {
         // TODO: report
      }

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
         cal.pixelWidth = 1.0;
         cal.pixelHeight = 1.0;
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
      
      // ensure that ImageJ saves files using our name, not "NewImageJBridge"...
      imagePlus_.setTitle(fileName);
   }

   @MustCallOnEDT
   public boolean isIJZoomedAllTheWayOut() {
      return canvas_.getMagnification() <= IJ_ZOOM_LEVELS.get(0) + 0.001;
   }

   @MustCallOnEDT
   public boolean isIJZoomedAllTheWayIn() {
      return canvas_.getMagnification() >=
            IJ_ZOOM_LEVELS.get(IJ_ZOOM_LEVELS.size() - 1) - 0.001;
   }

   @MustCallOnEDT
   public double getIJZoom() {
      return canvas_.getMagnification();
   }

   @MustCallOnEDT
   public void mm2ijSetZoom(double factor) {
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
      canvas_.setSizeToCurrent();
      canvas_.repaint();
   }
   
   /**
    * Sets the new zoomed view, centered around the user-desired position
    *   
    * @param factor New zoom factor
    * @param centerScreenX Desired x coordinate on the current canvas
    * @param centerScreenY Desired y coordinate on the current canvas
    */
   @MustCallOnEDT
   
   public void mm2ijSetZoom(double factor, int centerScreenX, int centerScreenY) {
      double originalFactor = getIJZoom();
      Rectangle originalSrcRect = this.canvas_.getSrcRect();
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

      // Center the new source rect where requested, to the
      // extent it fits in the image
      Rectangle newSourceRect = computeSourceRect(factor,
              (centerScreenX / originalFactor) + originalSrcRect.x, 
              (centerScreenY / originalFactor) + originalSrcRect.y,
            getMMWidth(), getMMHeight(),
            (int) Math.min(canvas_.getPreferredSize().getWidth(), newIdealCanvasSize.width), 
            (int) Math.min(canvas_.getPreferredSize().getHeight(), newIdealCanvasSize.height) );

      // Make sure to update zoom and source rect before setting size, since
      // setSize necessarily must "fix" the source rect.
      canvas_.setMagnification(factor);
      canvas_.setSourceRect(newSourceRect);
      canvas_.setSizeToCurrent();
      canvas_.repaint();
   }

   @MustCallOnEDT
   public void mm2ijZoomIn() {
      mm2ijSetZoom(ImageCanvas.getHigherZoomLevel(getIJZoom()));
   }
   
   @MustCallOnEDT
   public void mm2ijZoomIn(int centerX, int centerY) {
      mm2ijSetZoom(ImageCanvas.getHigherZoomLevel(getIJZoom()), centerX, centerY);
   }

   @MustCallOnEDT
   public void mm2ijZoomOut() {
      mm2ijSetZoom(ImageCanvas.getLowerZoomLevel(getIJZoom()));
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
      double centerX = oldSourceRect.x + 0.5 * oldSourceRect.width;
      double centerY = oldSourceRect.y + 0.5 * oldSourceRect.height;
      return computeSourceRect(zoomRatio, centerX, centerY, 
               imageWidth, imageHeight, dstWidth, dstHeight);
   }

   // dst width and height must not be larger than image
   private static Rectangle computeSourceRect(double zoomRatio,
         double centerX, double centerY,
         int imageWidth, int imageHeight,
         int dstWidth, int dstHeight)
   {
      Rectangle ret = new Rectangle();

      // First, compute a rect without regard to the image bounds.
      // Use ceiling to ensure all of the dest rect can be covered by the
      // scaled source rect, but don't exceed image size
      ret.width = Math.min(imageWidth,
            (int) Math.ceil((dstWidth + 1) / zoomRatio));
      ret.height = Math.min(imageHeight,
            (int) Math.ceil((dstHeight + 1) / zoomRatio));
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
         ret.x = imageWidth - ret.width;
      }
      if (ret.y + ret.height > imageHeight) {
         ret.y = imageHeight - ret.height;
      }

      return ret;
   }

   void ij2mmRoiMayHaveChanged() {
      // Reduce load by eliminating some of the most common unchanged cases
      Roi roi = imagePlus_.getRoi();
      if (roi == null) {
         if (lastSeenRoi_ == null) {
            return;
         }
      }
      else {
         if (roi.getType() == Roi.RECTANGLE && roi.getCornerDiameter() == 0) {
            Rectangle bounds = roi.getBounds();
            if (bounds.equals(lastSeenRoiRect_)) {
               return;
            }
            lastSeenRoiRect_ = new Rectangle(bounds);
         }
         else {
            lastSeenRoiRect_ = null;
         }
         lastSeenRoi_ = roi;
      }

      uiController_.selectionMayHaveChanged(makeBoundsAndMaskFromIJRoi(roi));
   }

   boolean ij2mmKeyPressConsumed(KeyEvent e) {
      return uiController_.keyPressOnImageConsumed(e);
   }
   
   void ij2mmMouseClicked(MouseEvent e) {
      uiController_.mouseEventOnImage(e, 
            computeImageRectForCanvasPoint(e.getPoint()), ij.gui.Toolbar.getToolId());
   }
   
   void ij2mmMousePressed(MouseEvent e) {
      uiController_.mouseEventOnImage(e, 
            computeImageRectForCanvasPoint(e.getPoint()), ij.gui.Toolbar.getToolId());
      
   }
   
   void ij2mmMouseReleased(MouseEvent e) {
      uiController_.mouseEventOnImage(e, 
            computeImageRectForCanvasPoint(e.getPoint()), ij.gui.Toolbar.getToolId());
   }

   void ij2mmMouseEnteredCanvas(MouseEvent e) {
      uiController_.mouseEventOnImage(e,
            computeImageRectForCanvasPoint(e.getPoint()), ij.gui.Toolbar.getToolId());
   }

   void ij2mmMouseExitedCanvas(MouseEvent e) {
      uiController_.mouseEventOnImage(e, null, ij.gui.Toolbar.getToolId());
   }

   void ij2mmMouseDraggedOnCanvas(MouseEvent e) {
      uiController_.mouseEventOnImage(e,
            computeImageRectForCanvasPoint(e.getPoint()), ij.gui.Toolbar.getToolId());
   }

   void ij2mmMouseMovedOnCanvas(MouseEvent e) {
      uiController_.mouseEventOnImage(e, 
            computeImageRectForCanvasPoint(e.getPoint()), ij.gui.Toolbar.getToolId());
   }
   
   void ij2mmMouseWheelMoved(MouseWheelEvent e) {
      uiController_.mouseWheelMoved(e);
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

   private BoundsRectAndMask makeBoundsAndMaskFromIJRoi(Roi ijRoi) {
      if (ijRoi == null) {
         return BoundsRectAndMask.unselected();
      }

      Rectangle bounds = ijRoi.getBounds();

      if (ijRoi.getType() == Roi.OVAL) {
         // ImageJ (1.51g) has a bug in OvalRoi that fails to flush its
         // cached mask while dragging to create the oval. Force recompute.
         ijRoi = (Roi) ijRoi.clone();
      }
      ByteProcessor maskProc = (ByteProcessor) ijRoi.getMask();

      // Sanity check, just in case.
      if (maskProc != null &&
            (maskProc.getWidth() != bounds.width ||
            maskProc.getHeight() != bounds.height))
      {
         return BoundsRectAndMask.unselected();
      }

      switch (ijRoi.getType()) {
         case Roi.RECTANGLE:
            if (ijRoi.getCornerDiameter() == 0) {
               return BoundsRectAndMask.create(bounds, null);
            }
            // Fall through for rounded rect
         case Roi.OVAL:
         case Roi.POLYGON:
         case Roi.FREEROI:
         case Roi.TRACED_ROI:
         case Roi.COMPOSITE:
            if (maskProc == null) {
               return BoundsRectAndMask.unselected();
            }
            byte[] mask = ((byte[]) maskProc.getPixels()).clone();
            return BoundsRectAndMask.create(bounds, mask);

         default:
            // We do not use lines, points, angles, etc., for histograms.
            // Any unsupported types should behave as if no Roi is selected.
            return BoundsRectAndMask.unselected();
      }
   }
}