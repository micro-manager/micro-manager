///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImageCache.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
// COPYRIGHT:    University of California, San Francisco, 2010
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

package org.micromanager.magellan.internal.magellanacq;

import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.XYTiledAcquisition;
import org.micromanager.acqj.util.xytiling.XYStagePosition;
import org.micromanager.explore.ExploreAcquisition;
import org.micromanager.explore.XYTiledAcqViewerStorageAdapater;
import org.micromanager.explore.gui.ChannelGroupSettings;
import org.micromanager.magellan.internal.gui.MagellanMouseListener;
import org.micromanager.magellan.internal.gui.MagellanOverlayer;
import org.micromanager.magellan.internal.gui.SurfaceGridPanel;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Created by magellan acquisition to manage viewer, data storage, and
 * conversion between pixel coordinate space (which the viewer and storage work
 * in) and the stage coordiante space (which the acquisition works in).
 */
public class MagellanUIViewerStorageAdapater extends XYTiledAcqViewerStorageAdapater
        implements SurfaceGridListener {

   private MagellanGenericAcquisitionSettings settings_;
   private MagellanOverlayer overlayer_;
   private MagellanMouseListener mouseListener_;
   private SurfaceGridPanel surfaceGridControls_;

   public MagellanUIViewerStorageAdapater(String dir, String name, boolean explore,
                                          ChannelGroupSettings exploreChannels,
                                          boolean showDisplay) {
      super(dir, name, showDisplay, explore, exploreChannels,
              new Consumer<String>() {
                 @Override
                 public void accept(String s) {
                    Log.log(s);
                 }
              });
      SurfaceGridManager.getInstance().registerSurfaceGridListener(this);
   }

   @Override
   public void initialize(Acquisition acq, JSONObject summaryMetadata) {
      super.initialize(acq, summaryMetadata);
      modifyDisplay();
   }

   //Constructor for opening loaded data
   public MagellanUIViewerStorageAdapater(String dir) throws IOException {
      super(dir, new Consumer<String>() {
         @Override
         public void accept(String s) {
            Log.log(s);
         }
      });
   }

   @Override
   public void close() {
      //on close
      if (!loadedData_) {
         SurfaceGridManager.getInstance().unregisterSurfaceGridListener(this);
      }
   }

   private void modifyDisplay() {
      //Add in magellan specific panels
      surfaceGridControls_ = new SurfaceGridPanel(this, display_);

      //add in custom mouse listener for the canvas
      mouseListener_ = new MagellanMouseListener(this, display_,
              explore_ ? (ExploreAcquisition) acq_ : null,
              surfaceGridControls_);
      display_.setCustomCanvasMouseListener(mouseListener_);

      //add in additional overlayer
      overlayer_ = new MagellanOverlayer(this, surfaceGridControls_);
      // replace the explore overlayer with magellan one
      display_.setOverlayerPlugin(overlayer_);


      if (!loadedData_) {
         display_.addControlPanel(surfaceGridControls_);
         if (isExploreAcquisition()) {
            this.setSurfaceGridMode(false); //start in explore mode
         }
      }
   }


   public void setSurfaceDisplaySettings(boolean showInterp, boolean showStage) {
      overlayer_.setSurfaceDisplayParams(showInterp, showStage);
   }

   public Point2D.Double getStageCoordinateOfViewCenter() {
      return ((XYTiledAcquisition) acq_).getPixelStageTranslator().getStageCoordsFromPixelCoords(
              (long) (display_.getViewOffset().x + display_.getFullResSourceDataSize().x / 2),
              (long) (display_.getViewOffset().y + display_.getFullResSourceDataSize().y / 2));

   }

   public void setSurfaceGridMode(boolean b) {
      ((MagellanOverlayer) overlayer_).setSurfaceGridMode(b);
      ((MagellanMouseListener) mouseListener_).setSurfaceGridMode(b);
   }

   public void update() {
      display_.update();
   }

   public void initializeViewerToLoaded(
           HashMap<String, Object> axisMins, HashMap<String, Object> axisMaxs) {

      LinkedList<String> channelNames = new LinkedList<String>();
      for (HashMap<String, Object> axes : storage_.getAxesSet()) {
         if (axes.containsKey(MagellanMD.CHANNEL_AXIS)) {
            if (!channelNames.contains(axes.get(MagellanMD.CHANNEL_AXIS))) {
               channelNames.add((String) axes.get(MagellanMD.CHANNEL_AXIS));
            }
         }
      }
      display_.initializeViewerToLoaded(channelNames, storage_.getDisplaySettings(),
            axisMins, axisMaxs);
   }

   public Set<HashMap<String, Object>> getAxesSet() {
      return storage_.getAxesSet();
   }

   public Point2D.Double[] getDisplayTileCorners(XYStagePosition pos) {
      return ((XYTiledAcquisition) acq_).getPixelStageTranslator().getDisplayTileCornerStageCoords(pos);
   }

   @Override
   public void surfaceOrGridChanged(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceOrGridDeleted(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceOrGridCreated(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceOrGridRenamed(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceInterpolationUpdated(SurfaceInterpolator s) {
      update();
   }

   public double getZCoordinateOfDisplayedSlice() {
      int index = (Integer) display_.getAxisPosition(AcqEngMetadata.Z_AXIS);
      return index * getZStep() + zOrigin_;
   }

   public int zCoordinateToZIndex(double z) {
      return (int) ((z - zOrigin_) / pixelSizeZ_);
   }
}
