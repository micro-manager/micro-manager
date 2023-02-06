package org.micromanager.plugins.micromanager;

import fromScenery.Settings;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import microscenery.Ablation;
import microscenery.Util;
import microscenery.hardware.micromanagerConnection.MMConnection;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import microscenery.signals.AblationResults;
import microscenery.signals.ClientSignal;
import net.miginfocom.swing.MigLayout;
import org.joml.Vector3f;
import org.micromanager.Studio;

import javax.swing.*;
import javax.swing.border.TitledBorder;
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

    private final JLabel totalTimeLabel = new JLabel("no data");
    private final JLabel meanTimeLabel = new JLabel("no data");
    private final JLabel stdTimeLabel = new JLabel("no data");

    public AblationPanel(MMConnection mmCon, Studio studio, MicromanagerWrapper mmWrapper) {
        this.mmCon = mmCon;
        this.studio = studio;

        Settings msSettings = Util.getMicroscenerySettings();

        Util.setVector3fIfUnset(msSettings, "Ablation.precision",new Vector3f(1f));
        msSettings.set("Ablation.dwellTimeMillis", 0L);
        msSettings.set("Ablation.laserPower", 0f);
        // count time it takes to move towards next point to that points dwell time
        msSettings.set("Ablation.CountMoveTime", true);
        msSettings.set("Ablation.PauseLaserOnMove", false);
        msSettings.set("Ablation.dryRun", true);

        this.setLayout(new MigLayout());

        TitledBorder title;
        title = BorderFactory.createTitledBorder("Photomanipulation");
        this.setBorder(title);

        this.add(new JLabel("Total Time(ms):"));
        this.add(totalTimeLabel,"wrap");
        this.add(new JLabel("mean time per point(ms):"));
        this.add(meanTimeLabel,"wrap");
        this.add(new JLabel("mtpp std(ms):"));
        this.add(stdTimeLabel,"wrap");

        JButton planButton = new JButton("Plan");
        planButton.addActionListener(e ->{
            @SuppressWarnings("deprecation")
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
                    List<Vector3f> sampled = Ablation.sampleLine(prev, cur, precision);
                    samplePointsIS.addAll(sampled);
                }
                samplePointsIS.add(cur);
                prev = cur;
            }
            if (samplePointsIS.size() > 1){
                samplePointsIS.addAll(Ablation.sampleLine(prev, samplePointsIS.get(0),precision));
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


        });
        this.add(executeBut, "wrap");
    }

    public void updateTimings(AblationResults results){
        totalTimeLabel.setText(results.getTotalTimeMillis()+"");
        meanTimeLabel.setText(results.mean() + "");
        double[] dar = results.getPerPointTime().stream().mapToDouble(Integer::doubleValue).toArray();
        if (dar.length > 2){
            stdTimeLabel.setText(calculateStandardDeviation(dar).intValue()+"");
        }
    }

    // taken from https://www.baeldung.com/java-calculate-standard-deviation
    public static Double calculateStandardDeviation(double[] array) {

        // get the sum of array
        double sum = 0.0;
        for (double i : array) {
            sum += i;
        }

        // get the mean of array
        int length = array.length;
        double mean = sum / length;

        // calculate the standard deviation
        double standardDeviation = 0.0;
        for (double num : array) {
            standardDeviation += Math.pow(num - mean, 2);
        }

        return Math.sqrt(standardDeviation / length);
    }
}
