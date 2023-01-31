package org.micromanager.plugins.micromanager;

import fromScenery.Settings;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import microscenery.Util;
import microscenery.hardware.micromanagerConnection.MMConnection;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import microscenery.signals.ClientSignal;
import net.miginfocom.swing.MigLayout;
import org.joml.Vector3f;
import org.micromanager.Studio;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AblationPanel extends JPanel {
    private final MMConnection mmCon;
    private final Studio studio;

    private List<Vector3f> plannedCut = null;
    private int imgMidX = 0;
    private int imgMidY = 0;

    public AblationPanel(Settings msSettings, MMConnection mmCon, Studio studio, MicromanagerWrapper mmWrapper) {
        this.mmCon = mmCon;
        this.studio = studio;

        Util.setVector3fIfUnset(msSettings, "Ablation.precision",new Vector3f(1f));

        this.setLayout(new MigLayout());

        JButton planButton = new JButton("Plan");
        planButton.addActionListener(e ->{
            ImagePlus img = this.studio.getSnapLiveManager().getDisplay().getImagePlus();
            //IJ.getImage()
            // do calibration like https://imagej.nih.gov/ij/developer/source/ij/plugin/Coordinates.java.html ?

            if (img == null) {
                this.studio.alerts().postAlert("Ablation not possible", null, "Image required.");
                return;
            }

            Roi roi = img.getRoi();
            if (roi == null) {
                this.studio.alerts().postAlert("Ablation not possible", null, "Selection required.");
                return;
            }

            Polygon polygon = roi.getPolygon();
            int[] xa = polygon.xpoints;
            int[] ya = polygon.ypoints;
            double pixelSize = this.studio.core().getPixelSizeUm();

            Vector3f precision = Objects.requireNonNull(Util.getVector3(msSettings, "Ablation.precision")).div((float) pixelSize);

            // points to sample in image space
            java.util.List<Vector3f> samplePointsIS = new ArrayList<>();
            Vector3f prev = null;
            for(int i = 0; i < polygon.npoints; i++){
                double x = xa[i];
                double y = ya[i];

                Vector3f cur = new Vector3f((float)x, (float) y,0);
                if(prev != null){
                    List<Vector3f> sampled = Util.sampleLine(prev, cur, precision);
                    samplePointsIS.addAll(sampled);
                }
                samplePointsIS.add(cur);
                prev = cur;
            }
            if (samplePointsIS.size() > 1){
                samplePointsIS.addAll(Util.sampleLine(prev, samplePointsIS.get(0),precision));
            }

            int[] newX = samplePointsIS.stream().mapToInt(v -> (int) v.x).toArray();
            int[] newY = samplePointsIS.stream().mapToInt(v -> (int) v.y).toArray();


            PointRoi newPoly = new PointRoi(newX, newY, newX.length);
            img.setOverlay(newPoly, Color.YELLOW, 3,null);

            // assuming the laser points to the middle of the image
            imgMidX = img.getWidth() /2;
            imgMidY = img.getHeight()/2;
            plannedCut = samplePointsIS;
        });
        this.add(planButton, "");

        JButton executeBut = new JButton("execute");
        executeBut.addActionListener(e ->{
            if (plannedCut == null) {
                this.studio.alerts().postAlert("Missing ablation plan", null, "Please plan a ablation path first");
                return;
            }

            double pixelSize = this.studio.core().getPixelSizeUm();

            Vector3f offset = this.mmCon.getStagePosition();
            offset.x += imgMidX * pixelSize;
            offset.y += imgMidY * pixelSize; //todo: or minus?

            List<Vector3f> pathInStageSpace = plannedCut.stream().map(vec -> vec.add(offset)).collect(Collectors.toList());

            mmWrapper.setStagePosition(pathInStageSpace.get(0));
            String msg = "Stage will be moved to first position.\n" +
                    "Please ready the laser then press 'OK'.";
            int result = JOptionPane.showConfirmDialog(null, msg,"Preparing Ablation", JOptionPane.OK_CANCEL_OPTION);
            switch (result){
                case JOptionPane.OK_OPTION:{
                    mmWrapper.ablatePoints(new ClientSignal.AblationPoints(
                            pathInStageSpace.stream().map(vec -> new ClientSignal.AblationPoint(
                                    vec,
                                    0,
                                    pathInStageSpace.get(0).equals(vec),
                                    pathInStageSpace.get(pathInStageSpace.size()-1).equals(vec),
                                    0,
                                    false
                            )).collect(Collectors.toList())
                    ));
                    break;
                }
                case JOptionPane.CANCEL_OPTION:{
                    this.studio.alerts().postAlert("Aborted Ablation", null, "Ablation has been aborted by the user.");
                }
            }

        });
        this.add(executeBut, "wrap");
    }
}
