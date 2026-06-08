package org.micromanager.autofocus.tca_af;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.stat.StatUtils;

import ij.process.ImageProcessor;

/**
 * Java translation of compute_best_focus_from_sampled_imagesfor_460nm_updated.m
 * Computes best focus from sampled images using various focus metrics.
 */
public class ComputeBestFocus300nm {

    // Settings class equivalent to MATLAB struct
    public static class Settings {
        public double z_range_um = 20;
        public double skipFactor = Double.NaN; // NaN means auto
        public double thresholdFraction = 0.1;
        public double assumedMaxMinDistance_um = 22;
        public double maxOnlyShiftRight_um = 5;
        public double minOnlyShiftLeft_um = 10;
        public double maxOnlyEdgeThreshold_um = 10;
        public double minOnlyEdgeThreshold_um = 8;
        public double maxOnlyInflectionAlpha = 0.35;
        public double minOnlyInflectionBeta = 0.58;
        public double maxOnlyInflectionMinDist_um = 12;
        public double maxOnlyInflectionMaxDist_um = 19;
        public double minOnlyInflectionMinDist_um = 5;
        public double minOnlyInflectionMaxDist_um = 11;
        public int nFinePoints = 5000;
        public double smoothingParam = 0.1;
        public int nDenseCrossingPoints = 50000;
        public double minExtremumWidth_um = 4.8;
        public double extremumPolyWindowHalf_um = 4;
        public int minExtremumFitPoints = 7;
        public double minExtremumRsq = 0.55;
        public boolean showFinalBestFocusLineOnAllPlots = true;
        public double outlierTolerance_um = 1.0;
        public int minAgreementCount = 3;
        public boolean useMedianForConsensusCenter = true;
    }

    // Extremum struct
    public static class Extremum {
        public double z;
        public double width;
        public double value;
        public int idxFine;
        public double zFineClosest;
        public int idxSampled;
        public double zSampledClosest;
    }

    // ExtremaResults class
    public static class ExtremaResults {
        public String metricName;
        public List<Extremum> max = new ArrayList<>();
        public List<Extremum> min = new ArrayList<>();
        public Extremum representativeMax;
        public Extremum representativeMin;
        public String detectionStatus;
    }

    // AcceptedExtremaSummary class
    public static class AcceptedExtremaSummary {
        public String metricName;
        public String detectionStatus;
        public double[] max_z;
        public int[] max_idxFine;
        public int[] max_idxSampled;
        public double[] min_z;
        public int[] min_idxFine;
        public int[] min_idxSampled;
        public double representativeMax_z;
        public double representativeMin_z;
        public String inflectionStatus;
        public double selectedInflectionZ;
        public boolean inflectionConsensusKept;
        public boolean maxConsensusKept;
        public boolean minConsensusKept;
        public boolean extremaAvailabilityKept;
        public String bestFocusCaseType;
        public String bestFocusStatus;
        public String bestFocusMessage;
        public double bestFocusTargetValue;
        public double bestFocusZOnSmoothCurve;
        public int bestFocusIdxSampled;
        public double bestFocusZSampledClosest;
        public boolean bestFocusConsensusKept;
    }

    // InflectionSummary class
    public static class InflectionSummary {
        public double avgInflectionZ;
        public int closestSampledIndex;
        public double closestSampledZ;
        public List<PerMetricInflection> perMetric = new ArrayList<>();
        public String status;
    }

    public static class PerMetricInflection {
        public String metricName;
        public String referenceType;
        public double[] referenceZ;
        public double[] zeroCrossingsZ;
        public double[] candidateInflectionZ;
        public double selectedInflectionZ;
        public int selectedInflectionIdxFine;
        public double selectedInflectionValueOnSmoothCurve;
        public int selectedInflectionIdxSampled;
        public double selectedInflectionZSampledClosest;
        public double[] selectionDistanceRange_um;
        public String status;
        public boolean isConsensusKept;
    }

    // BestFocusSummary class
    public static class BestFocusSummary {
        public String caseUsed;
        public String note;
        public List<PerMetricBestFocus> perMetric = new ArrayList<>();
        public double avgBestFocusZOnSmoothCurves;
        public int closestBestFocusIndexSampled;
        public double closestBestFocusZSampled;
        public double thresholdFraction;
        public double maxOnlyShiftRight_um;
        public double minOnlyShiftLeft_um;
        public double assumedMaxMinDistance_um;
        public double maxOnlyInflectionAlpha;
        public double minOnlyInflectionBeta;
        public String majorityCase;
        public boolean[] majorityCaseMask;
        public boolean[] finalConsensusMask;
        public int finalConsensusCount;
        public double finalConsensusCenterZ;
        public boolean[] extremaAvailabilityMask;
        public String extremaAvailabilityMode;
    }

    public static class PerMetricBestFocus {
        public String metricName;
        public String caseType;
        public String status;
        public String message;
        public double zMax;
        public int idxFineMax;
        public double Fmax;
        public double zMin;
        public int idxFineMin;
        public double Fmin;
        public double zInflection;
        public int idxFineInflection;
        public double targetValue;
        public double deltaZToEdge;
        public double min_infl_deltaz;
        public double half_remainder;
        public double bestFocusZOnSmoothCurve;
        public int bestFocusIdxFine;
        public double bestFocusValueOnSmoothCurve;
        public int bestFocusIdxSampled;
        public double bestFocusZSampledClosest;
        public boolean isConsensusKept;
        public boolean ignoredByExtremaAvailabilityRule;
    }

    // Results class
    public static class Results {
        public int nImagesSampled;
        public double[] zSampled;
        public double dzActual;
        public int idxZiniClosest;
        public double zAtZiniClosest;
        public String[] metricNames;
        public double[][] metricValuesRaw;
        public double[][] metricValuesNorm;
        public double[] zFine;
        public double[][] smoothCurvesNorm;
        public List<UnivariateFunction> fitObjects;
        public Settings settings;
        public double z_ini;
        public double deltaz_samp;
        public double z_best_focus;
    }

    // OverallDecision class
    public static class OverallDecision {
        public double avgMaxZ;
        public int closestAvgMaxSampledIndex;
        public double closestAvgMaxSampledZ;
        public double avgMinZ;
        public int closestAvgMinSampledIndex;
        public double closestAvgMinSampledZ;
        public double avgInflectionZ;
        public int closestAvgInflectionSampledIndex;
        public double closestAvgInflectionSampledZ;
        public String bestFocusCaseUsed;
        public double bestFocusAvgZOnSmoothCurves;
        public int bestFocusClosestIndexSampled;
        public double bestFocusClosestZSampled;
        public String bestFocusNote;
        public boolean[] maxConsensusMask;
        public boolean[] minConsensusMask;
        public boolean[] inflectionConsensusMask;
        public boolean[] bestFocusConsensusMask;
        public String majorityBestFocusCase;
        public boolean[] extremaAvailabilityMask;
        public String extremaAvailabilityMode;
    }

    // Other classes would be defined similarly...

    /**
     * Main computation method
     */
    public static Results computeBestFocus(List<ImageProcessor> imageArray, double z_ini, double deltaz_samp, double[] zSampled) {
        Settings settings = new Settings();

        // Input checks
        if (imageArray == null || imageArray.isEmpty()) {
            throw new IllegalArgumentException("imageArray must be a non-empty list.");
        }
        if (zSampled == null || zSampled.length == 0) {
            throw new IllegalArgumentException("zSampled must be a non-empty array.");
        }
        if (imageArray.size() != zSampled.length) {
            throw new IllegalArgumentException("imageArray length does not match zSampled length.");
        }
        if (zSampled.length < 5) {
            throw new IllegalArgumentException("Too few sampled z points.");
        }
        for (double z : zSampled) {
            if (Double.isNaN(z) || Double.isInfinite(z)) {
                throw new IllegalArgumentException("zSampled contains NaN or Inf.");
            }
        }

        // Basic sampling info
        int nImagesSampled = zSampled.length;
        double dzActual = (zSampled.length > 1) ? mean(diff(zSampled)) : Double.NaN;

        int idxZiniClosest = findClosestIndex(zSampled, z_ini);
        double zAtZiniClosest = zSampled[idxZiniClosest];

        // Calculate metrics on sampled images
        String[] metricNames = {"Brenner", "Vollath F4", "Gradient Energy", "Tenengrad"};
        int nMetrics = metricNames.length;
        double[][] metricValuesRaw = new double[zSampled.length][nMetrics];

        for (int i = 0; i < zSampled.length; i++) {
            ImageProcessor I = imageArray.get(i);
            if (I.getNChannels() == 3) {
                I = I.convertToByte(true).convertToRGB();
                // Convert to grayscale - simple average for now
                I = I.convertToByte(true);
            }
            double[][] img = ipToDoubleArray(I);

            metricValuesRaw[i][0] = calcBrenner(img);
            metricValuesRaw[i][1] = calcVollathF4(img);
            metricValuesRaw[i][2] = calcGradientEnergy(img);
            metricValuesRaw[i][3] = calcTenengrad(img);
        }

        // Normalize metrics
        double[][] metricValuesNorm = normalizeMetricsForPlot(metricValuesRaw);

        // Fit smoothing splines
        double[] zFine = linspace(min(zSampled), max(zSampled), settings.nFinePoints);
        double[][] smoothCurvesNorm = new double[zFine.length][nMetrics];
        List<UnivariateFunction> fitObjects = new ArrayList<>();

        for (int m = 0; m < nMetrics; m++) {
            double[] y = new double[metricValuesNorm.length];
            for (int i = 0; i < y.length; i++) {
                y[i] = metricValuesNorm[i][m];
            }
            boolean[] validMask = new boolean[y.length];
            int validCount = 0;
            for (int i = 0; i < y.length; i++) {
                validMask[i] = !Double.isNaN(y[i]) && !Double.isInfinite(y[i]);
                if (validMask[i]) validCount++;
            }

            if (validCount < 4) {
                System.out.println("Metric " + metricNames[m] + " has too few valid points.");
                fitObjects.add(null);
                continue;
            }

            double[] zValid = new double[validCount];
            double[] yValid = new double[validCount];
            int idx = 0;
            for (int i = 0; i < zSampled.length; i++) {
                if (validMask[i]) {
                    zValid[idx] = zSampled[i];
                    yValid[idx] = y[i];
                    idx++;
                }
            }

            // Use spline interpolation with smoothing parameter
            LocalSmoothingSplineFit.FitResult result = LocalSmoothingSplineFit.fit(zValid, yValid, settings.smoothingParam);

            fitObjects.add(result.fitFunction);

            double[] curve = new double[zFine.length];
            for (int i = 0; i < zFine.length; i++) {
                smoothCurvesNorm[i][m] = result.fitFunction.value(zFine[i]);
                curve[i] = smoothCurvesNorm[i][m];
            }
            // Save smoothCurvesNorm for plotting later:
            // saveSmoothCurveAsCSV(zFine, curve, 
            //                     metricNames[m] + "_smooth_curve.csv");
        }   

        // Detect extrema on zFine curves
        List<ExtremaResults> extremaResults = new ArrayList<>();
        for (int m = 0; m < nMetrics; m++) {
            ExtremaResults er = new ExtremaResults();
            er.metricName = metricNames[m];
            er.max = new ArrayList<>();
            er.min = new ArrayList<>();
            er.representativeMax = null;
            er.representativeMin = null;
            er.detectionStatus = "none";

            double[] y = new double[zFine.length];
            for (int i = 0; i < zFine.length; i++) {
                y[i] = smoothCurvesNorm[i][m];
            }

            if (fitObjects.get(m) == null || allNaN(y)) {
                extremaResults.add(er);
                continue;
            }

            double[][] extremaData = customExtrema(zFine, y);
            double[] maxLocs = extremaData[0];
            System.out.println("Metric: " + metricNames[m] + ", Detected max locations: " + Arrays.toString(maxLocs));
            double[] minLocs = extremaData[1];
            System.out.println("Metric: " + metricNames[m] + ", Detected min locations: " + Arrays.toString(minLocs));

            double[] maxWidths = extremaData[2];
            double[] minWidths = extremaData[3];

            for (int k = 0; k < maxLocs.length; k++) {
                if (isTrueExtremum(zFine, y, maxLocs[k], "max", maxWidths[k], settings)) {
                    er.max.add(buildExtremumStruct(maxLocs[k], maxWidths[k], zFine, y, zSampled, fitObjects.get(m)));
                }
            }

            for (int k = 0; k < minLocs.length; k++) {
                if (isTrueExtremum(zFine, y, minLocs[k], "min", minWidths[k], settings)) {
                    er.min.add(buildExtremumStruct(minLocs[k], minWidths[k], zFine, y, zSampled, fitObjects.get(m)));
                }
            }

            er.representativeMax = selectRepresentativeExtremum(er.max);
            er.representativeMin = selectRepresentativeExtremum(er.min);

            boolean hasMax = er.representativeMax != null;
            boolean hasMin = er.representativeMin != null;
            if (hasMax && hasMin) {
                if (er.representativeMax.z < er.representativeMin.z) {
                    er.detectionStatus = "both";
                } else {
                    er.detectionStatus = "invalid_order";
                }
            } else if (hasMax) {
                er.detectionStatus = "max_only";
            } else if (hasMin) {
                er.detectionStatus = "min_only";
            } else {
                er.detectionStatus = "none";
            }

            extremaResults.add(er);
            System.out.println("Metric: " + metricNames[m] + ", Detection status: " + er.detectionStatus);
            System.out.println("  Representative max Z at " + (er.representativeMax != null ? er.representativeMax.z : "null"));
        }

        // Extrema-availability dominance rule
        String[] detectionStatuses = new String[nMetrics];
        for (int m = 0; m < nMetrics; m++) {
            detectionStatuses[m] = extremaResults.get(m).detectionStatus;
        }

        int countBoth = 0, countMaxOnly = 0, countMinOnly = 0;
        for (String status : detectionStatuses) {
            if ("both".equals(status)) countBoth++;
            else if ("max_only".equals(status)) countMaxOnly++;
            else if ("min_only".equals(status)) countMinOnly++;
        }

        boolean[] extremaAvailabilityMask = new boolean[nMetrics];
        for (int i = 0; i < nMetrics; i++) extremaAvailabilityMask[i] = true;
        String extremaAvailabilityMode = "mixed";

        if (countBoth >= 2) {
            for (int m = 0; m < nMetrics; m++) {
                extremaAvailabilityMask[m] = "both".equals(detectionStatuses[m]);
            }
            extremaAvailabilityMode = "both_only";
        } else if (countMaxOnly >= 2 && countBoth == 0 && countMinOnly < 2) {
            for (int m = 0; m < nMetrics; m++) {
                extremaAvailabilityMask[m] = "max_only".equals(detectionStatuses[m]);
            }
            extremaAvailabilityMode = "max_only";
        } else if (countMinOnly >= 2 && countBoth == 0 && countMaxOnly < 2) {
            for (int m = 0; m < nMetrics; m++) {
                extremaAvailabilityMask[m] = "min_only".equals(detectionStatuses[m]);
            }
            extremaAvailabilityMode = "min_only";
        }

        // Average max/min summary with outlier rejection
        double[] repMaxZ = new double[nMetrics];
        double[] repMinZ = new double[nMetrics];
        for (int m = 0; m < nMetrics; m++) {
            repMaxZ[m] = Double.NaN;
            repMinZ[m] = Double.NaN;
            if (extremaResults.get(m).representativeMax != null) {
                repMaxZ[m] = extremaResults.get(m).representativeMax.z;
            }
            if (extremaResults.get(m).representativeMin != null) {
                repMinZ[m] = extremaResults.get(m).representativeMin.z;
            }
        }

        boolean[] maxConsensusMask = consensusMask1D(repMaxZ, settings.outlierTolerance_um, settings.minAgreementCount, settings.useMedianForConsensusCenter);
        double maxConsensusCenter = consensusCenter(repMaxZ, maxConsensusMask, settings.useMedianForConsensusCenter);
        int maxConsensusCount = countTrue(maxConsensusMask);

        boolean[] minConsensusMask = consensusMask1D(repMinZ, settings.outlierTolerance_um, settings.minAgreementCount, settings.useMedianForConsensusCenter);
        double minConsensusCenter = consensusCenter(repMinZ, minConsensusMask, settings.useMedianForConsensusCenter);
        int minConsensusCount = countTrue(minConsensusMask);

        double avgMaxZ = Double.NaN;
        if (anyTrue(maxConsensusMask)) {
            avgMaxZ = mean(repMaxZ, maxConsensusMask);
        } else {
            avgMaxZ = nanmean(repMaxZ);
        }

        double avgMinZ = Double.NaN;
        if (anyTrue(minConsensusMask)) {
            avgMinZ = mean(repMinZ, minConsensusMask);
        } else {
            avgMinZ = nanmean(repMinZ);
        }

        int idxAvgMaxSampled = -1;
        double zAvgMaxSampledClosest = Double.NaN;
        if (!Double.isNaN(avgMaxZ)) {
            int[] closest = findClosestIndexAndZ(zSampled, avgMaxZ);
            idxAvgMaxSampled = closest[0];
            zAvgMaxSampledClosest = zSampled[closest[0]];
        }

        int idxAvgMinSampled = -1;
        double zAvgMinSampledClosest = Double.NaN;
        if (!Double.isNaN(avgMinZ)) {
            int[] closest = findClosestIndexAndZ(zSampled, avgMinZ);
            idxAvgMinSampled = closest[0];
            zAvgMinSampledClosest = zSampled[closest[0]];
        }

        // Second derivative / inflection per metric
        double[][] secondDerivativeCurves = new double[zFine.length][nMetrics];

        InflectionSummary inflectionSummary = new InflectionSummary();
        inflectionSummary.avgInflectionZ = Double.NaN;
        inflectionSummary.closestSampledIndex = -1;
        inflectionSummary.closestSampledZ = Double.NaN;
        inflectionSummary.perMetric = new ArrayList<>();
        inflectionSummary.status = "no_inflection";

        double[] inflectionZAll = new double[nMetrics];
        for (int i = 0; i < nMetrics; i++) inflectionZAll[i] = Double.NaN;

        for (int m = 0; m < nMetrics; m++) {
            PerMetricInflection pmi = new PerMetricInflection();
            pmi.metricName = metricNames[m];
            pmi.referenceType = "";
            pmi.referenceZ = new double[0];
            pmi.zeroCrossingsZ = new double[0];
            pmi.candidateInflectionZ = new double[0];
            pmi.selectedInflectionZ = Double.NaN;
            pmi.selectedInflectionIdxFine = -1;
            pmi.selectedInflectionValueOnSmoothCurve = Double.NaN;
            pmi.selectedInflectionIdxSampled = -1;
            pmi.selectedInflectionZSampledClosest = Double.NaN;
            pmi.selectionDistanceRange_um = new double[0];
            pmi.status = "no_inflection";
            pmi.isConsensusKept = false;

            double[] y = new double[zFine.length];
            for (int i = 0; i < zFine.length; i++) {
                y[i] = smoothCurvesNorm[i][m];
            }

            if (fitObjects.get(m) == null || allNaN(y)) {
                inflectionSummary.perMetric.add(pmi);
                continue;
            }

            double[] dy = gradient(y, zFine);
            double[] d2y = gradient(dy, zFine);
            for (int i = 0; i < d2y.length; i++) {
                secondDerivativeCurves[i][m] = d2y[i];
            }

            double[] zeroZ = findZeroCrossings(zFine, d2y);
            System.out.println("Metric: " + metricNames[m] + ", Detected inflection points at Z: " + Arrays.toString(zeroZ));
            pmi.zeroCrossingsZ = zeroZ;

            Extremum repMax = extremaResults.get(m).representativeMax;
            Extremum repMin = extremaResults.get(m).representativeMin;

            if (repMax != null && repMin == null) {
                double refZ = repMax.z;
                double[] candidateZ = new double[0];
                double selectedZ = Double.NaN;
                String statusHere = "no_inflection";
                
                Object[] result = selectInflectionFromZeroCrossingsDirectional(
                    zeroZ, refZ, settings.maxOnlyInflectionMinDist_um, 
                    settings.maxOnlyInflectionMaxDist_um, "after");
                selectedZ = (Double) result[0];
                statusHere = (String) result[1];
                candidateZ = (double[]) result[2];

                pmi.referenceType = "max";
                pmi.referenceZ = new double[]{refZ};
                pmi.candidateInflectionZ = candidateZ;
                pmi.selectionDistanceRange_um = new double[]{settings.maxOnlyInflectionMinDist_um, settings.maxOnlyInflectionMaxDist_um};
                pmi.selectedInflectionZ = selectedZ;
                pmi.status = statusHere;

            } else if (repMax == null && repMin != null) {
                double refZ = repMin.z;
                double[] candidateZ = new double[0];
                double selectedZ = Double.NaN;
                String statusHere = "no_inflection";
                
                Object[] result = selectInflectionFromZeroCrossingsDirectional(
                    zeroZ, refZ, settings.minOnlyInflectionMinDist_um, 
                    settings.minOnlyInflectionMaxDist_um, "before");
                selectedZ = (Double) result[0];
                statusHere = (String) result[1];
                candidateZ = (double[]) result[2];

                pmi.referenceType = "min";
                pmi.referenceZ = new double[]{refZ};
                pmi.candidateInflectionZ = candidateZ;
                pmi.selectionDistanceRange_um = new double[]{settings.minOnlyInflectionMinDist_um, settings.minOnlyInflectionMaxDist_um};
                pmi.selectedInflectionZ = selectedZ;
                pmi.status = statusHere;

            } else if (repMax != null && repMin != null && repMax.z < repMin.z) {
                double[] candidateZ = new double[0];
                double selectedZ = Double.NaN;
                String statusHere = "no_inflection";
                
                Object[] result = selectInflectionBetweenTwoExtrema(zeroZ, repMax.z, repMin.z);
                selectedZ = (Double) result[0];
                statusHere = (String) result[1];
                candidateZ = (double[]) result[2];

                pmi.referenceType = "both";
                pmi.referenceZ = new double[]{repMax.z, repMin.z};
                pmi.candidateInflectionZ = candidateZ;
                pmi.selectionDistanceRange_um = new double[]{repMax.z, repMin.z};
                pmi.selectedInflectionZ = selectedZ;
                pmi.status = statusHere;
            }

            if (!Double.isNaN(pmi.selectedInflectionZ)) {
                double zSel = pmi.selectedInflectionZ;
                int[] closestFine = findClosestIndexAndZ(zFine, zSel);
                pmi.selectedInflectionIdxFine = closestFine[0];
                pmi.selectedInflectionValueOnSmoothCurve = fitObjects.get(m).value(zSel);
                int[] closestSampled = findClosestIndexAndZ(zSampled, zSel);
                pmi.selectedInflectionIdxSampled = closestSampled[0];
                pmi.selectedInflectionZSampledClosest = zSampled[closestSampled[0]];

                inflectionZAll[m] = zSel;
            }

            inflectionSummary.perMetric.add(pmi);
        }

        boolean[] infConsensusMask = consensusMask1D(inflectionZAll, settings.outlierTolerance_um, settings.minAgreementCount, settings.useMedianForConsensusCenter);
        double infConsensusCenter = consensusCenter(inflectionZAll, infConsensusMask, settings.useMedianForConsensusCenter);
        int infConsensusCount = countTrue(infConsensusMask);

        for (int m = 0; m < nMetrics; m++) {
            if (m < inflectionSummary.perMetric.size()) {
                inflectionSummary.perMetric.get(m).isConsensusKept = infConsensusMask[m];
            }
        }

        if (anyTrue(infConsensusMask)) {
            inflectionSummary.avgInflectionZ = mean(inflectionZAll, infConsensusMask);
        } else if (!allNaN(inflectionZAll)) {
            inflectionSummary.avgInflectionZ = nanmean(inflectionZAll);
        } else {
            inflectionSummary.avgInflectionZ = Double.NaN;
        }

        if (!Double.isNaN(inflectionSummary.avgInflectionZ)) {
            int[] closest = findClosestIndexAndZ(zSampled, inflectionSummary.avgInflectionZ);
            inflectionSummary.closestSampledIndex = closest[0];
            inflectionSummary.closestSampledZ = zSampled[closest[0]];
            inflectionSummary.status = "found_some";
        }

        // Best focus per metric
        BestFocusSummary bestFocusSummary = new BestFocusSummary();
        bestFocusSummary.caseUsed = "mixed_or_none";
        bestFocusSummary.note = "";
        bestFocusSummary.perMetric = new ArrayList<>();
        bestFocusSummary.avgBestFocusZOnSmoothCurves = Double.NaN;
        bestFocusSummary.closestBestFocusIndexSampled = -1;
        bestFocusSummary.closestBestFocusZSampled = Double.NaN;
        bestFocusSummary.thresholdFraction = settings.thresholdFraction;
        bestFocusSummary.maxOnlyShiftRight_um = settings.maxOnlyShiftRight_um;
        bestFocusSummary.minOnlyShiftLeft_um = settings.minOnlyShiftLeft_um;
        bestFocusSummary.assumedMaxMinDistance_um = settings.assumedMaxMinDistance_um;
        bestFocusSummary.maxOnlyInflectionAlpha = settings.maxOnlyInflectionAlpha;
        bestFocusSummary.minOnlyInflectionBeta = settings.minOnlyInflectionBeta;

        List<PerMetricBestFocus> bestPerMetric = new ArrayList<>();
        for (int m = 0; m < nMetrics; m++) {
            PerMetricBestFocus pmb = new PerMetricBestFocus();
            pmb.metricName = metricNames[m];
            pmb.caseType = "none";
            pmb.status = "not_used";
            pmb.message = "";
            pmb.zMax = Double.NaN;
            pmb.idxFineMax = -1;
            pmb.Fmax = Double.NaN;
            pmb.zMin = Double.NaN;
            pmb.idxFineMin = -1;
            pmb.Fmin = Double.NaN;
            pmb.zInflection = Double.NaN;
            pmb.idxFineInflection = -1;
            pmb.targetValue = Double.NaN;
            pmb.deltaZToEdge = Double.NaN;
            pmb.min_infl_deltaz = Double.NaN;
            pmb.half_remainder = Double.NaN;
            pmb.bestFocusZOnSmoothCurve = Double.NaN;
            pmb.bestFocusIdxFine = -1;
            pmb.bestFocusValueOnSmoothCurve = Double.NaN;
            pmb.bestFocusIdxSampled = -1;
            pmb.bestFocusZSampledClosest = Double.NaN;
            pmb.isConsensusKept = false;
            pmb.ignoredByExtremaAvailabilityRule = false;
            bestPerMetric.add(pmb);
            
        }

        double[] allValidBestZ = new double[nMetrics];
        for (int i = 0; i < nMetrics; i++) allValidBestZ[i] = Double.NaN;
        String[] allCaseTypes = new String[nMetrics];
        for (int i = 0; i < nMetrics; i++) allCaseTypes[i] = "";

        for (int m = 0; m < nMetrics; m++) {
            PerMetricBestFocus pmb = bestPerMetric.get(m);

            if (!extremaAvailabilityMask[m]) {
                pmb.caseType = "ignored_by_extrema_availability_rule";
                pmb.status = "ignored";
                pmb.message = String.format("Ignored because other graphs had a stronger shared extrema condition (%s).", extremaAvailabilityMode);
                pmb.ignoredByExtremaAvailabilityRule = true;
                continue;
            }

            if (fitObjects.get(m) == null || allNaN(smoothCurvesNorm, m)) {
                pmb.status = "invalid";
                pmb.message = "Fit object is empty.";
                continue;
            }

            Extremum repMax = extremaResults.get(m).representativeMax;
            Extremum repMin = extremaResults.get(m).representativeMin;

            boolean hasMax = repMax != null;
            boolean hasMin = repMin != null;

            boolean hasInfl = false;
            double zInfl = Double.NaN;
            int idxFineInfl = -1;

            if (m < inflectionSummary.perMetric.size() && 
                !Double.isNaN(inflectionSummary.perMetric.get(m).selectedInflectionZ) &&
                (inflectionSummary.perMetric.get(m).isConsensusKept || infConsensusCount < settings.minAgreementCount)) {
                hasInfl = true;
                zInfl = inflectionSummary.perMetric.get(m).selectedInflectionZ;
                idxFineInfl = inflectionSummary.perMetric.get(m).selectedInflectionIdxFine;
                pmb.zInflection = zInfl;
                pmb.idxFineInflection = idxFineInfl;
            }

            if (hasMax) {
                pmb.zMax = repMax.z;
                pmb.idxFineMax = repMax.idxFine;
                pmb.Fmax = repMax.value;
            }

            if (hasMin) {
                pmb.zMin = repMin.z;
                pmb.idxFineMin = repMin.idxFine;
                pmb.Fmin = repMin.value;
            }

            if (hasMax && hasMin && repMax.z < repMin.z) {
                double targetValue = repMax.value - settings.thresholdFraction * (repMax.value - repMin.value);

                Object[] result = findBestFocusBetweenMaxAndMin_dense_from_fit(
                    fitObjects.get(m), repMax.z, repMin.z, zFine, targetValue, settings.nDenseCrossingPoints);
                double bestZHere = (Double) result[0];
                int bestIdxFineHere = (Integer) result[1];
                double bestValHere = (Double) result[2];

                pmb.caseType = "both";
                pmb.status = "used";
                pmb.message = "Used threshold-drop rule on dense evaluation of smoothing spline.";
                pmb.targetValue = targetValue;
                pmb.bestFocusZOnSmoothCurve = bestZHere;
                pmb.bestFocusIdxFine = bestIdxFineHere;
                pmb.bestFocusValueOnSmoothCurve = bestValHere;

                if (!Double.isNaN(bestZHere)) {
                    int[] closest = findClosestIndexAndZ(zSampled, bestZHere);
                    pmb.bestFocusIdxSampled = closest[0];
                    pmb.bestFocusZSampledClosest = zSampled[closest[0]];
                    allValidBestZ[m] = bestZHere;
                    allCaseTypes[m] = "both";
                }
                continue;
            }

            if (hasMax && !hasMin && hasInfl && zInfl > repMax.z) {
                double bestZHere = repMax.z + settings.maxOnlyInflectionAlpha * (zInfl - repMax.z);
                double bestValHere = fitObjects.get(m).value(bestZHere);
                int[] closestFine = findClosestIndexAndZ(zFine, bestZHere);
                int bestIdxFineHere = closestFine[0];

                pmb.caseType = "max_only_with_inflection";
                pmb.status = "used";
                pmb.message = "Used alpha rule between max and inflection.";
                pmb.bestFocusZOnSmoothCurve = bestZHere;
                pmb.bestFocusIdxFine = bestIdxFineHere;
                pmb.bestFocusValueOnSmoothCurve = bestValHere;

                int[] closest = findClosestIndexAndZ(zSampled, bestZHere);
                pmb.bestFocusIdxSampled = closest[0];
                pmb.bestFocusZSampledClosest = zSampled[closest[0]];
                allValidBestZ[m] = bestZHere;
                allCaseTypes[m] = "max_only_with_inflection";
                continue;
            }

            if (hasMax && !hasMin && !hasInfl) {
                double deltaRight = max(zFine) - repMax.z;
                pmb.deltaZToEdge = deltaRight;

                if (deltaRight >= settings.maxOnlyEdgeThreshold_um) {
                    double bestZHere = repMax.z + settings.maxOnlyShiftRight_um;
                    double bestValHere = fitObjects.get(m).value(bestZHere);
                    int[] closestFine = findClosestIndexAndZ(zFine, bestZHere);
                    int bestIdxFineHere = closestFine[0];

                    pmb.caseType = "max_only_no_inflection";
                    pmb.status = "used";
                    pmb.message = "Used fixed shift right from max.";
                    pmb.bestFocusZOnSmoothCurve = bestZHere;
                    pmb.bestFocusIdxFine = bestIdxFineHere;
                    pmb.bestFocusValueOnSmoothCurve = bestValHere;

                    int[] closest = findClosestIndexAndZ(zSampled, bestZHere);
                    pmb.bestFocusIdxSampled = closest[0];
                    pmb.bestFocusZSampledClosest = zSampled[closest[0]];
                    allValidBestZ[m] = bestZHere;
                    allCaseTypes[m] = "max_only_no_inflection";
                } else {
                    pmb.caseType = "max_only_no_inflection";
                    pmb.status = "too_far";
                    pmb.message = "Too far from actual focus correct the z location";
                }
                continue;
            }

            if (!hasMax && hasMin && hasInfl && zInfl < repMin.z) {
                double min_infl_deltaz = repMin.z - zInfl;
                double half_remainder = (settings.assumedMaxMinDistance_um - min_infl_deltaz) / 2;
                double bestZHere = zInfl - settings.minOnlyInflectionBeta * half_remainder;
                double bestValHere = fitObjects.get(m).value(bestZHere);
                int[] closestFine = findClosestIndexAndZ(zFine, bestZHere);
                int bestIdxFineHere = closestFine[0];

                pmb.caseType = "min_only_with_inflection";
                pmb.status = "used";
                pmb.message = String.format("Used inflection-based estimate with beta = %.4g.", settings.minOnlyInflectionBeta);
                pmb.min_infl_deltaz = min_infl_deltaz;
                pmb.half_remainder = half_remainder;
                pmb.bestFocusZOnSmoothCurve = bestZHere;
                pmb.bestFocusIdxFine = bestIdxFineHere;
                pmb.bestFocusValueOnSmoothCurve = bestValHere;

                int[] closest = findClosestIndexAndZ(zSampled, bestZHere);
                pmb.bestFocusIdxSampled = closest[0];
                pmb.bestFocusZSampledClosest = zSampled[closest[0]];
                allValidBestZ[m] = bestZHere;
                allCaseTypes[m] = "min_only_with_inflection";
                continue;
            }

            if (!hasMax && hasMin && !hasInfl) {
                double deltaLeft = repMin.z - min(zFine);
                pmb.deltaZToEdge = deltaLeft;

                if (deltaLeft >= settings.minOnlyEdgeThreshold_um) {
                    double bestZHere = repMin.z - settings.minOnlyShiftLeft_um;
                    double bestValHere = fitObjects.get(m).value(bestZHere);
                    int[] closestFine = findClosestIndexAndZ(zFine, bestZHere);
                    int bestIdxFineHere = closestFine[0];

                    pmb.caseType = "min_only_no_inflection";
                    pmb.status = "used";
                    pmb.message = "Used fixed shift left from min.";
                    pmb.bestFocusZOnSmoothCurve = bestZHere;
                    pmb.bestFocusIdxFine = bestIdxFineHere;
                    pmb.bestFocusValueOnSmoothCurve = bestValHere;

                    int[] closest = findClosestIndexAndZ(zSampled, bestZHere);
                    pmb.bestFocusIdxSampled = closest[0];
                    pmb.bestFocusZSampledClosest = zSampled[closest[0]];
                    allValidBestZ[m] = bestZHere;
                    allCaseTypes[m] = "min_only_no_inflection";
                } else {
                    pmb.caseType = "min_only_no_inflection";
                    pmb.status = "too_far";
                    pmb.message = "Too far from actual focus correct the z location";
                }
                continue;
            }
            pmb.caseType = "none";
            pmb.status = "ignored";
            pmb.message = "No valid best-focus case identified.";
        }

        // Final best focus
        double z_best_focus = Double.NaN;
        System.out.println("Valid best-focus estimates per metric...");
        boolean[] validBestMask = new boolean[nMetrics];
        for (int i = 0; i < nMetrics; i++) {
            validBestMask[i] = !Double.isNaN(allValidBestZ[i]);
        }
        String[] validCaseTypes = new String[countTrue(validBestMask)];
        int idx = 0;
        for (int i = 0; i < nMetrics; i++) {
            if (validBestMask[i]) {
                validCaseTypes[idx++] = allCaseTypes[i];
            }
        }

        String majorityCase = "";
        boolean[] majorityCaseMaskFull = new boolean[nMetrics];
        int majorityCount = 0;

        if (anyTrue(validBestMask)) {
            Object[] result = majorityCategory(validCaseTypes, settings.minAgreementCount);
            majorityCase = (String) result[0];
            boolean[] majorityCaseMaskLocal = (boolean[]) result[1];
            majorityCount = (Integer) result[2];

            if (majorityCount >= settings.minAgreementCount) {
                int localIdx = 0;
                for (int m = 0; m < nMetrics; m++) {
                    if (validBestMask[m]) {
                        majorityCaseMaskFull[m] = majorityCaseMaskLocal[localIdx++];
                    }
                }
            } else {
                for (int m = 0; m < nMetrics; m++) {
                    majorityCaseMaskFull[m] = validBestMask[m];
                }
            }
        }

        double[] candidateBestZ = allValidBestZ.clone();
        for (int i = 0; i < candidateBestZ.length; i++) {
            if (!majorityCaseMaskFull[i]) {
                candidateBestZ[i] = Double.NaN;
            }
        }

        boolean[] bestConsensusMask = consensusMask1D(candidateBestZ, settings.outlierTolerance_um, settings.minAgreementCount, settings.useMedianForConsensusCenter);
        double bestConsensusCenter = consensusCenter(candidateBestZ, bestConsensusMask, settings.useMedianForConsensusCenter);
        int bestConsensusCount = countTrue(bestConsensusMask);

        boolean[] finalBestMask = new boolean[nMetrics];
        if (anyTrue(bestConsensusMask)) {
            finalBestMask = bestConsensusMask;
        } else if (anyTrue(majorityCaseMaskFull)) {
            finalBestMask = majorityCaseMaskFull;
        } else {
            finalBestMask = validBestMask;
        }

        for (int m = 0; m < nMetrics; m++) {
            System.out.println("Metric: " + metricNames[m] + ", Best focus Z: " + bestPerMetric.get(m).bestFocusZOnSmoothCurve);

            bestPerMetric.get(m).isConsensusKept = finalBestMask[m];
        }
        System.out.println("Best focus consensus count: " + countTrue(finalBestMask));

        if (anyTrue(finalBestMask)) {
            z_best_focus = mean(allValidBestZ, finalBestMask);
            int[] closest = findClosestIndexAndZ(zSampled, z_best_focus);
            bestFocusSummary.closestBestFocusIndexSampled = closest[0];
            bestFocusSummary.closestBestFocusZSampled = zSampled[closest[0]];

            bestFocusSummary.caseUsed = "robust_average_of_consensus_metric_estimates";
            if (majorityCount >= settings.minAgreementCount) {
                bestFocusSummary.note = String.format(
                    "Final best focus uses only the majority-agreeing case (%s) and removes z outliers farther than %.3f um from the consensus cluster.",
                    majorityCase, settings.outlierTolerance_um);
            } else {
                bestFocusSummary.note = String.format(
                    "Final best focus uses all valid estimates, then removes z outliers farther than %.3f um from the consensus cluster when possible.",
                    settings.outlierTolerance_um);
            }
            bestFocusSummary.avgBestFocusZOnSmoothCurves = z_best_focus;
        } else {
            bestFocusSummary.caseUsed = "none";
            bestFocusSummary.note = "No valid best-focus rule could be applied.";
        }

        bestFocusSummary.perMetric = bestPerMetric;
        bestFocusSummary.majorityCase = majorityCase;
        bestFocusSummary.majorityCaseMask = majorityCaseMaskFull;
        bestFocusSummary.finalConsensusMask = finalBestMask;
        bestFocusSummary.finalConsensusCount = countTrue(finalBestMask);
        bestFocusSummary.finalConsensusCenterZ = bestConsensusCenter;
        bestFocusSummary.extremaAvailabilityMask = extremaAvailabilityMask;
        bestFocusSummary.extremaAvailabilityMode = extremaAvailabilityMode;

        // Accepted extrema summary
        List<AcceptedExtremaSummary> acceptedExtremaSummary = new ArrayList<>();
        for (int m = 0; m < nMetrics; m++) {
            AcceptedExtremaSummary aes = new AcceptedExtremaSummary();
            aes.metricName = metricNames[m];
            aes.detectionStatus = extremaResults.get(m).detectionStatus;

            if (extremaResults.get(m).max.isEmpty()) {
                aes.max_z = new double[0];
                aes.max_idxFine = new int[0];
                aes.max_idxSampled = new int[0];
            } else {
                aes.max_z = new double[extremaResults.get(m).max.size()];
                aes.max_idxFine = new int[extremaResults.get(m).max.size()];
                aes.max_idxSampled = new int[extremaResults.get(m).max.size()];
                for (int i = 0; i < extremaResults.get(m).max.size(); i++) {
                    aes.max_z[i] = extremaResults.get(m).max.get(i).z;
                    aes.max_idxFine[i] = extremaResults.get(m).max.get(i).idxFine;
                    aes.max_idxSampled[i] = extremaResults.get(m).max.get(i).idxSampled;
                }
            }

            if (extremaResults.get(m).min.isEmpty()) {
                aes.min_z = new double[0];
                aes.min_idxFine = new int[0];
                aes.min_idxSampled = new int[0];
            } else {
                aes.min_z = new double[extremaResults.get(m).min.size()];
                aes.min_idxFine = new int[extremaResults.get(m).min.size()];
                aes.min_idxSampled = new int[extremaResults.get(m).min.size()];
                for (int i = 0; i < extremaResults.get(m).min.size(); i++) {
                    aes.min_z[i] = extremaResults.get(m).min.get(i).z;
                    aes.min_idxFine[i] = extremaResults.get(m).min.get(i).idxFine;
                    aes.min_idxSampled[i] = extremaResults.get(m).min.get(i).idxSampled;
                }
            }

            if (extremaResults.get(m).representativeMax != null) {
                aes.representativeMax_z = extremaResults.get(m).representativeMax.z;
            } else {
                aes.representativeMax_z = Double.NaN;
            }

            if (extremaResults.get(m).representativeMin != null) {
                aes.representativeMin_z = extremaResults.get(m).representativeMin.z;
            } else {
                aes.representativeMin_z = Double.NaN;
            }

            if (m < inflectionSummary.perMetric.size()) {
                aes.inflectionStatus = inflectionSummary.perMetric.get(m).status;
                aes.selectedInflectionZ = inflectionSummary.perMetric.get(m).selectedInflectionZ;
                aes.inflectionConsensusKept = inflectionSummary.perMetric.get(m).isConsensusKept;
            } else {
                aes.inflectionStatus = "no_inflection";
                aes.selectedInflectionZ = Double.NaN;
                aes.inflectionConsensusKept = false;
            }

            aes.maxConsensusKept = maxConsensusMask[m];
            aes.minConsensusKept = minConsensusMask[m];
            aes.extremaAvailabilityKept = extremaAvailabilityMask[m];

            aes.bestFocusCaseType = bestPerMetric.get(m).caseType;
            aes.bestFocusStatus = bestPerMetric.get(m).status;
            aes.bestFocusMessage = bestPerMetric.get(m).message;
            aes.bestFocusTargetValue = bestPerMetric.get(m).targetValue;
            aes.bestFocusZOnSmoothCurve = bestPerMetric.get(m).bestFocusZOnSmoothCurve;
            aes.bestFocusIdxSampled = bestPerMetric.get(m).bestFocusIdxSampled;
            aes.bestFocusZSampledClosest = bestPerMetric.get(m).bestFocusZSampledClosest;
            aes.bestFocusConsensusKept = bestPerMetric.get(m).isConsensusKept;

            acceptedExtremaSummary.add(aes);
        }
        System.out.println("Accepted extrema summary");
        // Create results object
        Results results = new Results();
        results.nImagesSampled = nImagesSampled;
        results.zSampled = zSampled.clone();
        results.dzActual = dzActual;
        results.idxZiniClosest = idxZiniClosest;
        results.zAtZiniClosest = zAtZiniClosest;
        results.metricNames = metricNames.clone();
        results.metricValuesRaw = metricValuesRaw;
        results.metricValuesNorm = metricValuesNorm;
        results.zFine = zFine;
        results.smoothCurvesNorm = smoothCurvesNorm;
        results.fitObjects = fitObjects;
        results.settings = settings;
        results.z_ini = z_ini;
        results.deltaz_samp = deltaz_samp;
        results.z_best_focus = z_best_focus;

        // Add additional fields to Results if needed
        // For now, we'll keep it simple and just return the basic results

        return results;
    }

    // Helper methods

    private static double[][] ipToDoubleArray(ImageProcessor ip) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        double[][] result = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                result[y][x] = ip.getPixelValue(x, y);
            }
        }
        return result;
    }

    private static double[] linspace(double start, double end, int n) {
        double[] result = new double[n];
        double step = (end - start) / (n - 1);
        for (int i = 0; i < n; i++) {
            result[i] = start + i * step;
        }
        return result;
    }

    private static double min(double[] arr) {
        double min = Double.MAX_VALUE;
        for (double v : arr) {
            if (v < min) min = v;
        }
        return min;
    }

    private static double max(double[] arr) {
        double max = Double.MIN_VALUE;
        for (double v : arr) {
            if (v > max) max = v;
        }
        return max;
    }

    private static double mean(double[] arr) {
        return StatUtils.mean(arr);
    }

    private static double[] diff(double[] arr) {
        double[] result = new double[arr.length - 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = arr[i + 1] - arr[i];
        }
        return result;
    }

    private static int findClosestIndex(double[] arr, double target) {
        int idx = 0;
        double minDiff = Math.abs(arr[0] - target);
        for (int i = 1; i < arr.length; i++) {
            double diff = Math.abs(arr[i] - target);
            if (diff < minDiff) {
                minDiff = diff;
                idx = i;
            }
        }
        return idx;
    }

    private static double estimateBestFocusFromSmoothCurves(double[] zFine, double[][] smoothCurvesNorm) {
        if (zFine == null || smoothCurvesNorm == null || zFine.length == 0 || smoothCurvesNorm.length == 0) {
            return Double.NaN;
        }

        int nMetrics = smoothCurvesNorm[0].length;
        double[] bestZ = new double[nMetrics];
        int validCount = 0;

        for (int m = 0; m < nMetrics; m++) {
            double bestValue = Double.NEGATIVE_INFINITY;
            int bestIdx = -1;
            for (int i = 0; i < zFine.length; i++) {
                double v = smoothCurvesNorm[i][m];
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    continue;
                }
                if (v > bestValue) {
                    bestValue = v;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0) {
                bestZ[m] = zFine[bestIdx];
                validCount++;
            } else {
                bestZ[m] = Double.NaN;
            }
        }

        if (validCount == 0) {
            return Double.NaN;
        }

        double sum = 0;
        for (int m = 0; m < nMetrics; m++) {
            if (!Double.isNaN(bestZ[m]) && !Double.isInfinite(bestZ[m])) {
                sum += bestZ[m];
            }
        }
        return sum / validCount;
    }

    private static double[][] normalizeMetricsForPlot(double[][] M) {
        int rows = M.length;
        int cols = M[0].length;
        double[][] Mnorm = new double[rows][cols];

        for (int j = 0; j < cols; j++) {
            double[] col = new double[rows];
            for (int i = 0; i < rows; i++) {
                col[i] = M[i][j];
            }

            boolean[] validMask = new boolean[rows];
            int validCount = 0;
            for (int i = 0; i < rows; i++) {
                validMask[i] = !Double.isNaN(col[i]) && !Double.isInfinite(col[i]);
                if (validMask[i]) validCount++;
            }

            if (validCount == 0) {
                for (int i = 0; i < rows; i++) {
                    Mnorm[i][j] = Double.NaN;
                }
                continue;
            }

            double cmin = Double.MAX_VALUE;
            double cmax = Double.MIN_VALUE;
            for (int i = 0; i < rows; i++) {
                if (validMask[i]) {
                    if (col[i] < cmin) cmin = col[i];
                    if (col[i] > cmax) cmax = col[i];
                }
            }

            double range = cmax - cmin;
            if (Math.abs(range) < 1e-12) {
                for (int i = 0; i < rows; i++) {
                    Mnorm[i][j] = validMask[i] ? 0 : Double.NaN;
                }
            } else {
                for (int i = 0; i < rows; i++) {
                    if (validMask[i]) {
                        Mnorm[i][j] = (col[i] - cmin) / range;
                    } else {
                        Mnorm[i][j] = Double.NaN;
                    }
                }
            }
        }
        return Mnorm;
    }

    private static double calcBrenner(double[][] I) {
        int height = I.length;
        int width = I[0].length;
        if (width < 3) return Double.NaN;

        double sum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width - 2; x++) {
                double diff = I[y][x + 2] - I[y][x];
                sum += diff * diff;
            }
        }
        return sum;
    }

    private static double calcVollathF4(double[][] I) {
        int height = I.length;
        int width = I[0].length;
        if (width < 3) return Double.NaN;

        double term1 = 0;
        double term2 = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width - 1; x++) {
                term1 += I[y][x] * I[y][x + 1];
            }
            for (int x = 0; x < width - 2; x++) {
                term2 += I[y][x] * I[y][x + 2];
            }
        }
        return term1 - term2;
    }

    private static double calcGradientEnergy(double[][] I) {
        int height = I.length;
        int width = I[0].length;

        double[][] Gx = new double[height][width];
        double[][] Gy = new double[height][width];

        // Compute Gx (gradient in x direction) - matches MATLAB gradient function
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x == 0) {
                    Gx[y][x] = I[y][x + 1] - I[y][x]; // forward difference
                } else if (x == width - 1) {
                    Gx[y][x] = I[y][x] - I[y][x - 1]; // backward difference
                } else {
                    Gx[y][x] = (I[y][x + 1] - I[y][x - 1]) / 2.0; // central difference
                }
            }
        }

        // Compute Gy (gradient in y direction) - matches MATLAB gradient function
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (y == 0) {
                    Gy[y][x] = I[y + 1][x] - I[y][x]; // forward difference
                } else if (y == height - 1) {
                    Gy[y][x] = I[y][x] - I[y - 1][x]; // backward difference
                } else {
                    Gy[y][x] = (I[y + 1][x] - I[y - 1][x]) / 2.0; // central difference
                }
            }
        }

        // Sum the squares
        double sum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sum += Gx[y][x] * Gx[y][x] + Gy[y][x] * Gy[y][x];
            }
        }
        return sum;
    }

    private static double calcTenengrad(double[][] I) {
        int height = I.length;
        int width = I[0].length;

        double[][] Gx = new double[height][width];
        double[][] Gy = new double[height][width];

        double[][] sobelX = {
            {-1.0, 0.0, 1.0},
            {-2.0, 0.0, 2.0},
            {-1.0, 0.0, 1.0}
        };
        double[][] sobelY = {
            {-1.0, -2.0, -1.0},
            {0.0, 0.0, 0.0},
            {1.0, 2.0, 1.0}
        };

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double gx = 0.0;
                double gy = 0.0;

                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int yy = Math.min(Math.max(y + ky, 0), height - 1);
                        int xx = Math.min(Math.max(x + kx, 0), width - 1);
                        double value = I[yy][xx];
                        gx += sobelX[ky + 1][kx + 1] * value;
                        gy += sobelY[ky + 1][kx + 1] * value;
                    }
                }

                Gx[y][x] = gx;
                Gy[y][x] = gy;
            }
        }

        double sum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sum += Gx[y][x] * Gx[y][x] + Gy[y][x] * Gy[y][x];
            }
        }
        return sum;
    }

    // Additional helper methods

    private static boolean allNaN(double[] arr) {
        for (double v : arr) {
            if (!Double.isNaN(v)) return false;
        }
        return true;
    }

    private static boolean allNaN(double[][] arr, int col) {
        for (int i = 0; i < arr.length; i++) {
            if (!Double.isNaN(arr[i][col])) return false;
        }
        return true;
    }

    private static double nanmean(double[] arr) {
        double sum = 0;
        int count = 0;
        for (double v : arr) {
            if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                sum += v;
                count++;
            }
        }
        return count > 0 ? sum / count : Double.NaN;
    }

    private static double mean(double[] arr, boolean[] mask) {
        double sum = 0;
        int count = 0;
        for (int i = 0; i < arr.length; i++) {
            if (mask[i] && !Double.isNaN(arr[i]) && !Double.isInfinite(arr[i])) {
                sum += arr[i];
                count++;
            }
        }
        return count > 0 ? sum / count : Double.NaN;
    }

    private static int countTrue(boolean[] arr) {
        int count = 0;
        for (boolean b : arr) {
            if (b) count++;
        }
        return count;
    }

    private static boolean anyTrue(boolean[] arr) {
        for (boolean b : arr) {
            if (b) return true;
        }
        return false;
    }

    private static int[] findClosestIndexAndZ(double[] arr, double target) {
        int idx = 0;
        double minDiff = Math.abs(arr[0] - target);
        for (int i = 1; i < arr.length; i++) {
            double diff = Math.abs(arr[i] - target);
            if (diff < minDiff) {
                minDiff = diff;
                idx = i;
            }
        }
        return new int[]{idx, (int) arr[idx]};
    }

    private static double[] gradient(double[] y, double[] x) {
        double[] dy = new double[y.length];
        double dx = x[1] - x[0]; // Assuming uniform spacing
        dy[0] = (y[1] - y[0]) / dx;
        for (int i = 1; i < y.length - 1; i++) {
            dy[i] = (y[i + 1] - y[i - 1]) / (2 * dx);
        }
        dy[y.length - 1] = (y[y.length - 1] - y[y.length - 2]) / dx;
        return dy;
    }

    private static double[] sign(double[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > 0) {
                result[i] = 1.0;
            } else if (arr[i] < 0) {
                result[i] = -1.0;
            } else {
                result[i] = 0.0;
            }
        }
        return result;
    }

    private static double[] polyfit(double[] x, double[] y, int degree) {
        if (degree != 2) {
            throw new IllegalArgumentException("Only degree 2 polynomial fitting is implemented");
        }

        int n = x.length;
        double sumX = 0, sumY = 0, sumX2 = 0, sumX3 = 0, sumX4 = 0;
        double sumXY = 0, sumX2Y = 0;

        for (int i = 0; i < n; i++) {
            double xi = x[i];
            double yi = y[i];
            double x2 = xi * xi;
            double x3 = x2 * xi;
            double x4 = x3 * xi;

            sumX += xi;
            sumY += yi;
            sumX2 += x2;
            sumX3 += x3;
            sumX4 += x4;
            sumXY += xi * yi;
            sumX2Y += x2 * yi;
        }

        // Solve the normal equations for quadratic: y = a*x^2 + b*x + c
        // Matrix form: [sumX4, sumX3, sumX2] [a]   [sumX2Y]
        //              [sumX3, sumX2, sumX ] [b] = [sumXY ]
        //              [sumX2, sumX,  n    ] [c]   [sumY  ]

        double[][] A = {
            {sumX4, sumX3, sumX2},
            {sumX3, sumX2, sumX},
            {sumX2, sumX, n}
        };

        double[] B = {sumX2Y, sumXY, sumY};

        return solveLinearSystem(A, B);
    }

    private static double[] polyval(double[] coeffs, double[] x) {
        double[] result = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            double val = 0;
            for (int j = 0; j < coeffs.length; j++) {
                val += coeffs[j] * Math.pow(x[i], coeffs.length - 1 - j);
            }
            result[i] = val;
        }
        return result;
    }

    private static double[] solveLinearSystem(double[][] A, double[] B) {
        // Simple Gaussian elimination for 3x3 system
        int n = 3;
        double[][] augmented = new double[n][n + 1];

        // Create augmented matrix
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                augmented[i][j] = A[i][j];
            }
            augmented[i][n] = B[i];
        }

        // Forward elimination
        for (int p = 0; p < n; p++) {
            // Find pivot row
            int max = p;
            for (int i = p + 1; i < n; i++) {
                if (Math.abs(augmented[i][p]) > Math.abs(augmented[max][p])) {
                    max = i;
                }
            }

            // Swap rows
            double[] temp = augmented[p];
            augmented[p] = augmented[max];
            augmented[max] = temp;

            // Eliminate
            for (int i = p + 1; i < n; i++) {
                double alpha = augmented[i][p] / augmented[p][p];
                for (int j = p; j < n + 1; j++) {
                    augmented[i][j] -= alpha * augmented[p][j];
                }
            }
        }

        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0;
            for (int j = i + 1; j < n; j++) {
                sum += augmented[i][j] * x[j];
            }
            x[i] = (augmented[i][n] - sum) / augmented[i][i];
        }

        return x;
    }

    private static double[][] customExtrema(double[] z, double[] y) {
        // Compute gradient
        double[] dy = gradient(y, z);

        // Get sign of gradient
        double[] signDy = sign(dy);

        // Handle zeros by propagating previous sign
        for (int i = 1; i < signDy.length; i++) {
            if (signDy[i] == 0) {
                signDy[i] = signDy[i - 1];
            }
        }

        // Find sign changes
        double[] signChange = diff(signDy);

        // Find maxima (where signChange < 0) and minima (where signChange > 0)
        List<Integer> maxIdxList = new ArrayList<>();
        List<Integer> minIdxList = new ArrayList<>();

        for (int i = 0; i < signChange.length; i++) {
            if (signChange[i] < 0) {
                maxIdxList.add(i + 1); // +1 because diff reduces array length
            } else if (signChange[i] > 0) {
                minIdxList.add(i + 1); // +1 because diff reduces array length
            }
        }

        // Extract locations
        double[] maxLocs = new double[maxIdxList.size()];
        double[] minLocs = new double[minIdxList.size()];
        double[] maxWidths = new double[maxIdxList.size()];
        double[] minWidths = new double[minIdxList.size()];

        for (int i = 0; i < maxIdxList.size(); i++) {
            int idx = maxIdxList.get(i);
            maxLocs[i] = z[idx];
            maxWidths[i] = estimateFWHM(z, y, idx, "max");
        }

        for (int i = 0; i < minIdxList.size(); i++) {
            int idx = minIdxList.get(i);
            minLocs[i] = z[idx];
            minWidths[i] = estimateFWHM(z, y, idx, "min");
        }

        double[][] result = new double[4][];
        result[0] = maxLocs;
        result[1] = minLocs;
        result[2] = maxWidths;
        result[3] = minWidths;
        return result;
    }

    private static boolean isTrueExtremum(double[] z, double[] y, double z0, String type, double width, Settings settings) {
        boolean isTrue = false;
        System.out.println("Checking " + type + " extremum at z = " + z0 + " with width = " + width);
        // Check width validity
        if (Double.isNaN(width) || width < settings.minExtremumWidth_um) {
            return false;
        }

        // Find index closest to z0
        int idx0 = 0;
        double minDiff = Math.abs(z[0] - z0);
        for (int i = 1; i < z.length; i++) {
            double diff = Math.abs(z[i] - z0);
            if (diff < minDiff) {
                minDiff = diff;
                idx0 = i;
            }
        }

        // Calculate mean spacing
        double[] dzArr = diff(z);
        double dz = mean(dzArr);

        // Calculate window half-width
        int winHalf = Math.max(3, (int) Math.round(settings.extremumPolyWindowHalf_um / dz));

        // Define local window
        int idxL = Math.max(0, idx0 - winHalf);
        int idxR = Math.min(z.length - 1, idx0 + winHalf);

        // Check if window has enough points
        if (idxR - idxL + 1 < settings.minExtremumFitPoints) {
            return false;
        }

        // Extract local data
        int localSize = idxR - idxL + 1;
        double[] zLocal = new double[localSize];
        double[] yLocal = new double[localSize];
        for (int i = 0; i < localSize; i++) {
            zLocal[i] = z[idxL + i];
            yLocal[i] = y[idxL + i];
        }

        // Fit quadratic polynomial
        double[] p2 = polyfit(zLocal, yLocal, 2);
        double a2 = p2[0]; // coefficient of x^2

        // Check curvature based on type
        if ("max".equalsIgnoreCase(type)) {
            if (a2 >= 0) {
                return false; // Should be negative for maximum (concave down)
            }
        } else if ("min".equalsIgnoreCase(type)) {
            if (a2 <= 0) {
                return false; // Should be positive for minimum (concave up)
            }
        } else {
            return false; // Invalid type
        }

        // Calculate R-squared
        double[] yFit = polyval(p2, zLocal);
        double ssRes = 0;
        double yMean = mean(yLocal);
        double ssTot = 0;

        for (int i = 0; i < localSize; i++) {
            double residual = yLocal[i] - yFit[i];
            ssRes += residual * residual;
            double total = yLocal[i] - yMean;
            ssTot += total * total;
        }

        if (ssTot <= 0) {
            return false;
        }

        double Rsq = 1 - ssRes / ssTot;
        if (Rsq < settings.minExtremumRsq) {
            return false;
        }

        return true;
    }

    private static Extremum buildExtremumStruct(double zLoc, double width, double[] zFine, double[] y, double[] zSampled, UnivariateFunction fit) {
        Extremum ext = new Extremum();
        ext.z = zLoc;
        ext.width = width;
        ext.value = fit.value(zLoc);
        int[] closestFine = findClosestIndexAndZ(zFine, zLoc);
        ext.idxFine = closestFine[0];
        ext.zFineClosest = zFine[closestFine[0]];
        int[] closestSampled = findClosestIndexAndZ(zSampled, zLoc);
        ext.idxSampled = closestSampled[0];
        ext.zSampledClosest = zSampled[closestSampled[0]];
        return ext;
    }

    private static Extremum selectRepresentativeExtremum(List<Extremum> extremaList) {
        if (extremaList.isEmpty()) {
            return null;
        }

        // Get widths
        double[] widths = new double[extremaList.size()];
        for (int i = 0; i < extremaList.size(); i++) {
            widths[i] = extremaList.get(i).width;
        }

        // Find maxWidth
        double maxWidth = Double.NEGATIVE_INFINITY;
        for (double w : widths) {
            if (w > maxWidth) maxWidth = w;
        }

        // Find candidates
        List<Integer> candIdx = new ArrayList<>();
        for (int i = 0; i < widths.length; i++) {
            if (Math.abs(widths[i] - maxWidth) < 1e-12) {
                candIdx.add(i);
            }
        }

        if (candIdx.size() == 1) {
            return extremaList.get(candIdx.get(0));
        } else {
            // Get zVals
            double[] zVals = new double[candIdx.size()];
            for (int i = 0; i < candIdx.size(); i++) {
                zVals[i] = Math.abs(extremaList.get(candIdx.get(i)).z);
            }

            // Find min zVal
            double minZVal = Double.POSITIVE_INFINITY;
            int minIdx = -1;
            for (int i = 0; i < zVals.length; i++) {
                if (zVals[i] < minZVal) {
                    minZVal = zVals[i];
                    minIdx = i;
                }
            }

            return extremaList.get(candIdx.get(minIdx));
        }
    }

    private static boolean[] consensusMask1D(double[] values, double tolerance, int minAgreement, boolean useMedian) {
        boolean[] mask = new boolean[values.length];
        List<Double> validValues = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                validValues.add(values[i]);
            }
        }
        if (validValues.size() < minAgreement) {
            return mask; // All false
        }

        double center = useMedian ? median(validValues) : mean(validValues.stream().mapToDouble(Double::doubleValue).toArray());
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i]) && Math.abs(values[i] - center) <= tolerance) {
                mask[i] = true;
            }
        }
        return mask;
    }

    private static double consensusCenter(double[] values, boolean[] mask, boolean useMedian) {
        List<Double> validValues = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (mask[i] && !Double.isNaN(values[i])) {
                validValues.add(values[i]);
            }
        }
        if (validValues.isEmpty()) return Double.NaN;
        return useMedian ? median(validValues) : mean(validValues.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private static double median(List<Double> values) {
        values.sort(Double::compare);
        int size = values.size();
        if (size % 2 == 0) {
            return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
        } else {
            return values.get(size / 2);
        }
    }

    private static double[] findZeroCrossings(double[] zFine, double[] d2y) {
        List<Double> crossings = new ArrayList<>();
        for (int i = 0; i < d2y.length - 1; i++) {
            if (d2y[i] * d2y[i + 1] < 0) {
                // Linear interpolation
                double frac = -d2y[i] / (d2y[i + 1] - d2y[i]);
                double zCross = zFine[i] + frac * (zFine[i + 1] - zFine[i]);
                crossings.add(zCross);
            }
        }
        return crossings.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static Object[] selectInflectionFromZeroCrossingsDirectional(double[] zeroZ, double refZ, double minDist, double maxDist, String direction) {
        List<Double> candidates = new ArrayList<>();
        for (double z : zeroZ) {
            double dist = Math.abs(z - refZ);
            if (dist >= minDist && dist <= maxDist) {
                if ("after".equals(direction) && z > refZ) {
                    candidates.add(z);
                } else if ("before".equals(direction) && z < refZ) {
                    candidates.add(z);
                }
            }
        }

        double selectedZ = Double.NaN;
        String status = "no_inflection";
        double[] candidateZ = new double[0];

        if (!candidates.isEmpty()) {
            // Select the closest one
            double closest = candidates.get(0);
            double minDistToRef = Math.abs(closest - refZ);
            for (int i = 1; i < candidates.size(); i++) {
                double dist = Math.abs(candidates.get(i) - refZ);
                if (dist < minDistToRef) {
                    minDistToRef = dist;
                    closest = candidates.get(i);
                }
            }
            selectedZ = closest;
            status = "found";
            candidateZ = candidates.stream().mapToDouble(Double::doubleValue).toArray();
        }

        return new Object[]{selectedZ, status, candidateZ};
    }

    private static Object[] selectInflectionBetweenTwoExtrema(double[] zeroZ, double zMax, double zMin) {
        List<Double> candidates = new ArrayList<>();
        for (double z : zeroZ) {
            if (z > zMax && z < zMin) {
                candidates.add(z);
            }
        }

        double selectedZ = Double.NaN;
        String status = "no_inflection";
        double[] candidateZ = new double[0];

        if (!candidates.isEmpty()) {
            // Select the one closest to the midpoint
            double midpoint = (zMax + zMin) / 2;
            double closest = candidates.get(0);
            double minDist = Math.abs(closest - midpoint);
            for (int i = 1; i < candidates.size(); i++) {
                double dist = Math.abs(candidates.get(i) - midpoint);
                if (dist < minDist) {
                    minDist = dist;
                    closest = candidates.get(i);
                }
            }
            selectedZ = closest;
            status = "found";
            candidateZ = candidates.stream().mapToDouble(Double::doubleValue).toArray();
        }

        return new Object[]{selectedZ, status, candidateZ};
    }

    private static Object[] findBestFocusBetweenMaxAndMin_dense_from_fit(UnivariateFunction fit, double zMax, double zMin, double[] zFine, double targetValue, int nDense) {
        double[] zDense = linspace(zMax, zMin, nDense);
        double minDiff = Double.MAX_VALUE;
        double bestZ = Double.NaN;
        int bestIdx = -1;
        double bestVal = Double.NaN;

        for (int i = 0; i < zDense.length; i++) {
            double val = fit.value(zDense[i]);
            double diff = Math.abs(val - targetValue);
            if (diff < minDiff) {
                minDiff = diff;
                bestZ = zDense[i];
                bestIdx = i;
                bestVal = val;
            }
        }

        return new Object[]{bestZ, bestIdx, bestVal};
    }

    private static void saveSmoothCurveAsCSV(double[] z, double[] y, String filename) {
        try (PrintWriter pw = new PrintWriter(new File(filename))) {
            pw.println("z,y");
            for (int i = 0; i < z.length; i++) {
                pw.println(z[i] + "," + y[i]);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static double estimateFWHM(double[] z, double[] y, int idx, String type) {
        double y0 = y[idx];

        double baseline;
        double halfLevel;
        int left;
        int right;

        if ("max".equalsIgnoreCase(type)) {
            baseline = min(y);
            halfLevel = baseline + 0.5 * (y0 - baseline);

            left = idx;
            while (left > 0 && y[left] > halfLevel) {
                left = left - 1;
            }

            right = idx;
            while (right < y.length - 1 && y[right] > halfLevel) {
                right = right + 1;
            }
        } else {
            baseline = max(y);
            halfLevel = baseline - 0.5 * (baseline - y0);

            left = idx;
            while (left > 0 && y[left] < halfLevel) {
                left = left - 1;
            }

            right = idx;
            while (right < y.length - 1 && y[right] < halfLevel) {
                right = right + 1;
            }
        }

        // Clamp indices to valid range
        left = Math.max(0, Math.min(left, z.length - 1));
        right = Math.max(0, Math.min(right, z.length - 1));

        return Math.abs(z[right] - z[left]);
    }

    static Object[] majorityCategory(String[] categories, int minAgreement) {
        Map<String, Integer> countMap = new HashMap<>();
        for (String cat : categories) {
            countMap.put(cat, countMap.getOrDefault(cat, 0) + 1);
        }

        String majority = "";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                majority = entry.getKey();
            }
        }

        boolean[] mask = new boolean[categories.length];
        if (maxCount >= minAgreement) {
            for (int i = 0; i < categories.length; i++) {
                mask[i] = majority.equals(categories[i]);
            }
        }

        return new Object[]{majority, mask, maxCount};
    }

}