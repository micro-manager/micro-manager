package org.micromanager.autofocus.tca_af;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.stat.StatUtils;

import ij.process.ImageProcessor;

/**
 * Java port of compute_best_focus_from_sampled_images_for_NADH.m
 *
 * Computes the best focus z-position from a set of sampled images using
 * multiple focus metrics (Brenner, Vollath F4, Gradient Energy, Tenengrad).
 */
public class ComputeBestFocusFAD {

    // =========================================================================
    // Settings (mirrors MATLAB settings struct)
    // =========================================================================
    public static class Settings {
        public double zRangeUm = 20.0;
        public Integer skipFactor = null;

        public double thresholdFraction = 0.24;
        public double assumedMaxMinDistance_um = 27.0;
        public double maxOnlyShiftRight_um = 5.8;
        public double minOnlyShiftLeft_um = 17;
        public double maxOnlyEdgeThreshold_um = 10.0;
        public double minOnlyEdgeThreshold_um = 8.0;
        public double maxOnlyInflectionAlpha = 0.5;
        public double minOnlyInflectionBeta = 0.68;

        // 0 = min-with-inflection, 1 = original max-min, 2 = max-with-inflection
        public int bothWithInflectionMode = 0;
        public double bothMaxMinDistanceThreshold_um = 24.0;

        public double maxOnlyInflectionMinDist_um = 12.0;
        public double maxOnlyInflectionMaxDist_um = 19.0;
        public double minOnlyInflectionMinDist_um = 5.0;
        public double minOnlyInflectionMaxDist_um = 11.0;

        public int nFinePoints = 5000;
        public double smoothingParam = 0.1; //change
        public int nDenseCrossingPoints = 50000;

        public double minExtremumWidth_um = 8.0; //change
        public double extremumPolyWindowHalf_um = 4.0;
        public int minExtremumFitPoints = 7;
        public double minExtremumRsq = 0.55;

        public boolean showFinalBestFocusLineOnAllPlots = true;

        public double outlierTolerance_um = 1.0;
        public int minAgreementCount = 3; //change
        public boolean useMedianForConsensusCenter = true;

        public double metricDisagreementThreshold_um = 1.0;
        public String[] restrictedDecisionMetricNames = {"Brenner", "Vollath F4", "Gradient Energy"};
    }

    // =========================================================================
    // Data structures
    // =========================================================================
    public static class ExtremumInfo {
        public double z;
        public double width;
        public double value;
        public int idxFine;
        public double zFineClosest;
        public int idxSampled;
        public double zSampledClosest;
    }

    public static class ExtremumResult {
        public String metricName;
        public List<ExtremumInfo> max = new ArrayList<>();
        public List<ExtremumInfo> min = new ArrayList<>();
        public ExtremumInfo representativeMax = null;
        public ExtremumInfo representativeMin = null;
        public String detectionStatus = "none";
        public String decisionStatus = "none";
    }

    public static class InflectionPerMetric {
        public String metricName = "";
        public String referenceType = "";
        public double[] referenceZ = null;
        public double[] zeroCrossingsZ = null;
        public double[] candidateInflectionZ = null;
        public Double selectedInflectionZ = null;
        public Integer selectedInflectionIdxFine = null;
        public Double selectedInflectionValueOnSmoothCurve = null;
        public Integer selectedInflectionIdxSampled = null;
        public Double selectedInflectionZSampledClosest = null;
        public double[] selectionDistanceRange_um = null;
        public String status = "no_inflection";
        public boolean isConsensusKept = false;
    }

    public static class InflectionSummary {
        public Double avgInflectionZ = null;
        public Integer closestSampledIndex = null;
        public Double closestSampledZ = null;
        public InflectionPerMetric[] perMetric;
        public String status = "no_inflection";
    }

    public static class BestPerMetric {
        public String metricName = "";
        public String caseType = "none";
        public String decisionStatus = "none";
        public String status = "not_used";
        public String message = "";
        public Double zMax = null;
        public Integer idxFineMax = null;
        public Double Fmax = null;
        public Double zMin = null;
        public Integer idxFineMin = null;
        public Double Fmin = null;
        public Double zInflection = null;
        public Integer idxFineInflection = null;
        public Double targetValue = null;
        public Double deltaZToEdge = null;
        public Double minInflDeltaz = null;
        public Double halfRemainder = null;
        public Double bestFocusZOnSmoothCurve = null;
        public Integer bestFocusIdxFine = null;
        public Double bestFocusValueOnSmoothCurve = null;
        public Integer bestFocusIdxSampled = null;
        public Double bestFocusZSampledClosest = null;
        public boolean isConsensusKept = false;
        public boolean ignoredByExtremaAvailabilityRule = false;
        public boolean ignoredByFinalDisagreementRule = false;
    }

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
        public double[][] secondDerivativeCurves;
        public ExtremumResult[] extremaResults;
        public InflectionSummary inflectionSummary;
        public BestPerMetric[] bestFocusSummaryPerMetric;
        public double zBestFocus = Double.NaN;
        public double zIni;
        public double deltazSamp;
        public Settings settings;
    }

    // =========================================================================
    // Main entry point
    // =========================================================================

    /**
     * @param imageArray  2D pixel arrays, one per z position (grayscale float)
     * @param zIni        initial z center used for sweep
     * @param deltazSamp  sampling step
     * @param zSampled    z values for each image
     * @return Results struct
     */

    public static Results compute(List<ImageProcessor> imageArray, double zIni, double deltazSamp, double[] zSampled) {


        Settings s = new Settings();
        // --- Input checks ---
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
        // --- Basic info ---
        int n = zSampled.length;
        double dzActual = n > 1 ? mean(diff(zSampled)) : Double.NaN;

        int idxZiniClosest = argMinAbs(zSampled, zIni);
        double zAtZiniClosest = zSampled[idxZiniClosest];

        // --- Metrics ---
        String[] metricNames = {"Brenner", "Vollath F4", "Gradient Energy", "Tenengrad"};
        int nMetrics = metricNames.length;
        double[][] metricValuesRaw = new double[n][nMetrics];

        for (int i = 0; i < n; i++) {
            ImageProcessor I = imageArray.get(i);
            if (I.getNChannels() == 3) {
                I = I.convertToByte(true).convertToRGB();
                // Convert to grayscale - simple average for now
                I = I.convertToByte(true);
            }
            double[][] img = ipToDoubleArray(I);
            double minPix = Double.POSITIVE_INFINITY;
            double maxPix = Double.NEGATIVE_INFINITY;
            double sumPix = 0.0;
            int pixCount = 0;

            for (int yy = 0; yy < img.length; yy++) {
                for (int xx = 0; xx < img[0].length; xx++) {
                    double v = img[yy][xx];
                    minPix = Math.min(minPix, v);
                    maxPix = Math.max(maxPix, v);
                    sumPix += v;
                    pixCount++;
                }
            }

            metricValuesRaw[i][0] = calcBrenner(img);
            metricValuesRaw[i][1] = calcVollathF4(img);
            metricValuesRaw[i][2] = calcGradientEnergy(img);
            metricValuesRaw[i][3] = calcTenengrad(img);
        }

        // --- Normalize ---
        double[][] metricValuesNorm = normalizeMetrics(metricValuesRaw);

        // --- Fine z grid ---
        double zMin = min(zSampled), zMax = max(zSampled);
        double[] zFine = linspace(zMin, zMax, s.nFinePoints);

        // --- Smooth curves (local smoothing spline approximation) ---
        double[][] smoothCurvesNorm = new double[s.nFinePoints][nMetrics];
        UnivariateFunction[] fitObjects = new UnivariateFunction[nMetrics];

        for (int m = 0; m < nMetrics; m++) {

            double[] y = col(metricValuesNorm, m);
            boolean[] valid = validMask(y);

            if (countTrue(valid) < 4) {
                System.out.println("Metric: " + metricNames[m] + " has too few valid points.");
                Arrays.fill(col(smoothCurvesNorm, m), Double.NaN);
                fitObjects[m] = null;
                continue;
            }

            double[] xv = filter(zSampled, valid);
            double[] yv = filter(y, valid);

            LocalSmoothingSplineFit.FitResult result = LocalSmoothingSplineFit.fit(xv, yv, s.smoothingParam);

            fitObjects[m] = result.fitFunction;

            for (int i = 0; i < zFine.length; i++) {
                smoothCurvesNorm[i][m] = result.fitFunction.value(zFine[i]);
            }

            // save smoothCurvesNorm in original scale for debugging as CSV file:
            saveSmoothCurveToCSV(zFine, col(smoothCurvesNorm, m), metricNames[m] + "_smooth_curve.csv");

        }

        // --- Detect extrema ---
        ExtremumResult[] extremaResults = new ExtremumResult[nMetrics];
        for (int m = 0; m < nMetrics; m++) {
            extremaResults[m] = new ExtremumResult();
            extremaResults[m].metricName = metricNames[m];

            if (fitObjects[m] == null) continue;

            double[] yf = colFromMatrix(smoothCurvesNorm, m, s.nFinePoints);
            double[][] extrema = customExtrema(zFine, yf);
            double[] maxLocs = extrema[0], minLocs = extrema[1],
                    maxWidths = extrema[2], minWidths = extrema[3];
            System.out.println("Metric: " + metricNames[m] + ", Detected max locations: " + Arrays.toString(maxLocs) + ", Detected min locations: " + Arrays.toString(minLocs));

            for (int k = 0; k < maxLocs.length; k++) {
                if (isTrueExtremum(zFine, yf, maxLocs[k], "max", maxWidths[k], s)) {
                    extremaResults[m].max.add(buildExtremumStruct(maxLocs[k], maxWidths[k], zFine, yf, zSampled));
                }
            }
            for (int k = 0; k < minLocs.length; k++) {
                if (isTrueExtremum(zFine, yf, minLocs[k], "min", minWidths[k], s)) {
                    extremaResults[m].min.add(buildExtremumStruct(minLocs[k], minWidths[k], zFine, yf, zSampled));
                }
            }
            extremaResults[m].representativeMax = selectRepresentativeExtremum(extremaResults[m].max);
            extremaResults[m].representativeMin = selectRepresentativeExtremum(extremaResults[m].min);

            boolean hasMax = extremaResults[m].representativeMax != null;
            boolean hasMin = extremaResults[m].representativeMin != null;
            

            if (hasMax && hasMin) {
                if (extremaResults[m].representativeMax.z < extremaResults[m].representativeMin.z)
                    extremaResults[m].detectionStatus = "both";
                else
                    extremaResults[m].detectionStatus = "invalid_order";
            } else if (hasMax) {
                extremaResults[m].detectionStatus = "max_only";
            } else if (hasMin) {
                extremaResults[m].detectionStatus = "min_only";
            }
        }

        // --- Extrema availability dominance rule ---
        boolean[] extremaAvailabilityMask = new boolean[nMetrics];
        Arrays.fill(extremaAvailabilityMask, true);
        String extremaAvailabilityMode = "mixed";

        int countBoth = 0, countMax = 0, countMin = 0;
        for (ExtremumResult er : extremaResults) {
            if ("both".equals(er.detectionStatus)) countBoth++;
            else if ("max_only".equals(er.detectionStatus)) countMax++;
            else if ("min_only".equals(er.detectionStatus)) countMin++;
        }

        if (countBoth >= 2) {
            for (int m = 0; m < nMetrics; m++)
                extremaAvailabilityMask[m] = "both".equals(extremaResults[m].detectionStatus);
            extremaAvailabilityMode = "both_only";
        } else if (countMax >= 2 && countBoth == 0 && countMin < 2) {
            for (int m = 0; m < nMetrics; m++)
                extremaAvailabilityMask[m] = "max_only".equals(extremaResults[m].detectionStatus);
            extremaAvailabilityMode = "max_only";
        } else if (countMin >= 2 && countBoth == 0 && countMax < 2) {
            for (int m = 0; m < nMetrics; m++)
                extremaAvailabilityMask[m] = "min_only".equals(extremaResults[m].detectionStatus);
            extremaAvailabilityMode = "min_only";
        }

        // --- Consensus for max/min ---
        double[] repMaxZ = new double[nMetrics];
        double[] repMinZ = new double[nMetrics];
        Arrays.fill(repMaxZ, Double.NaN);
        Arrays.fill(repMinZ, Double.NaN);
        for (int m = 0; m < nMetrics; m++) {
            if (extremaResults[m].representativeMax != null) repMaxZ[m] = extremaResults[m].representativeMax.z;
            if (extremaResults[m].representativeMin != null) repMinZ[m] = extremaResults[m].representativeMin.z;
        }

        boolean[] maxConsensusMask = consensusMask1D(repMaxZ, s.outlierTolerance_um,
                s.minAgreementCount, s.useMedianForConsensusCenter);
        boolean[] minConsensusMask = consensusMask1D(repMinZ, s.outlierTolerance_um,
                s.minAgreementCount, s.useMedianForConsensusCenter);

        int maxConsensusCount = countTrue(maxConsensusMask);
        int minConsensusCount = countTrue(minConsensusMask);

        double avgMaxZ;
        System.out.println("Max Z values: " + Arrays.toString(repMaxZ) + ", Max consensus mask: " + Arrays.toString(maxConsensusMask));
        if (anyTrue(maxConsensusMask)) {
            avgMaxZ = nanMean(maskValues(repMaxZ, maxConsensusMask));
        } else {
            avgMaxZ = nanMean(repMaxZ);
        }

        double avgMinZ;
        if (anyTrue(minConsensusMask)) {
            avgMinZ = nanMean(maskValues(repMinZ, minConsensusMask));
        } else {
            avgMinZ = nanMean(repMinZ);
        }

        Integer idxAvgMaxSampled = null;
        Double zAvgMaxSampledClosest = null;
        if (!Double.isNaN(avgMaxZ) && Double.isFinite(avgMaxZ)) {
            int[] closest = findClosestIndexAndZ(zSampled, avgMaxZ);
            idxAvgMaxSampled = closest[0];
            zAvgMaxSampledClosest = zSampled[closest[0]];
        }

        Integer idxAvgMinSampled = null;
        Double zAvgMinSampledClosest = null;
        if (!Double.isNaN(avgMinZ) && Double.isFinite(avgMinZ)) {
            int[] closest = findClosestIndexAndZ(zSampled, avgMinZ);
            idxAvgMinSampled = closest[0];
            zAvgMinSampledClosest = zSampled[closest[0]];
        }

        // --- Second derivatives & inflections ---
        double[][] secondDerivativeCurves = new double[s.nFinePoints][nMetrics];
        InflectionSummary inflectionSummary = new InflectionSummary();
        inflectionSummary.perMetric = new InflectionPerMetric[nMetrics];

        double[] inflectionZAll = new double[nMetrics];
        Arrays.fill(inflectionZAll, Double.NaN);

        for (int m = 0; m < nMetrics; m++) {
            inflectionSummary.perMetric[m] = new InflectionPerMetric();
            inflectionSummary.perMetric[m].metricName = metricNames[m];

            if (fitObjects[m] == null) continue;

            double[] yf = colFromMatrix(smoothCurvesNorm, m, s.nFinePoints);
            double[] dy = gradient(yf, zFine);
            double[] d2y = gradient(dy, zFine);
            for (int i = 0; i < s.nFinePoints; i++) secondDerivativeCurves[i][m] = d2y[i];

            double[] zeroZ = findZeroCrossings(zFine, d2y);
            System.out.println("Metric: " + metricNames[m] + ", Detected inflection points at Z: " + Arrays.toString(zeroZ));
            inflectionSummary.perMetric[m].zeroCrossingsZ = zeroZ;

            ExtremumInfo repMax = extremaResults[m].representativeMax;
            ExtremumInfo repMin = extremaResults[m].representativeMin;

            if (repMax != null && repMin == null) {
                double refZ = repMax.z;

                Object[] sel = selectInflectionFromZeroCrossingsDirectional(
                    zeroZ, refZ,
                    s.maxOnlyInflectionMinDist_um,
                    s.maxOnlyInflectionMaxDist_um,
                    "after"
                );

                double selectedZ = (Double) sel[0];
                String statusHere = (String) sel[1];
                double[] candidateZ = (double[]) sel[2];

                inflectionSummary.perMetric[m].referenceType = "max";
                inflectionSummary.perMetric[m].referenceZ = new double[]{refZ};
                inflectionSummary.perMetric[m].candidateInflectionZ = candidateZ;
                inflectionSummary.perMetric[m].selectionDistanceRange_um =
                    new double[]{s.maxOnlyInflectionMinDist_um, s.maxOnlyInflectionMaxDist_um};
                inflectionSummary.perMetric[m].selectedInflectionZ = selectedZ;
                inflectionSummary.perMetric[m].status = statusHere;

            } else if (repMax == null && repMin != null) {
                double refZ = repMin.z;

                Object[] sel = selectInflectionFromZeroCrossingsDirectional(
                    zeroZ, refZ,
                    s.minOnlyInflectionMinDist_um,
                    s.minOnlyInflectionMaxDist_um,
                    "before"
                );

                double selectedZ = (Double) sel[0];
                String statusHere = (String) sel[1];
                double[] candidateZ = (double[]) sel[2];

                inflectionSummary.perMetric[m].referenceType = "min";
                inflectionSummary.perMetric[m].referenceZ = new double[]{refZ};
                inflectionSummary.perMetric[m].candidateInflectionZ = candidateZ;
                inflectionSummary.perMetric[m].selectionDistanceRange_um =
                    new double[]{s.minOnlyInflectionMinDist_um, s.minOnlyInflectionMaxDist_um};
                inflectionSummary.perMetric[m].selectedInflectionZ = selectedZ;
                inflectionSummary.perMetric[m].status = statusHere;

            } else if (repMax != null && repMin != null && repMax.z < repMin.z) {

                Object[] sel = selectInflectionBetweenTwoExtrema(
                    zeroZ, repMax.z, repMin.z
                );

                double selectedZ = (Double) sel[0];
                String statusHere = (String) sel[1];
                double[] candidateZ = (double[]) sel[2];

                inflectionSummary.perMetric[m].referenceType = "both";
                inflectionSummary.perMetric[m].referenceZ =
                    new double[]{repMax.z, repMin.z};
                inflectionSummary.perMetric[m].candidateInflectionZ = candidateZ;
                inflectionSummary.perMetric[m].selectionDistanceRange_um =
                    new double[]{repMax.z, repMin.z};
                inflectionSummary.perMetric[m].selectedInflectionZ = selectedZ;
                inflectionSummary.perMetric[m].status = statusHere;
            }

            if (hasFinite(inflectionSummary.perMetric[m].selectedInflectionZ)){
                double zSel = inflectionSummary.perMetric[m].selectedInflectionZ;

                inflectionSummary.perMetric[m].selectedInflectionIdxFine =
                    argMinAbs(zFine, zSel);

                int[] closest = findClosestIndexAndZ(zSampled, zSel);
                inflectionSummary.perMetric[m].selectedInflectionIdxSampled = closest[0];
                inflectionSummary.perMetric[m].selectedInflectionZSampledClosest =
                    zSampled[closest[0]];

                inflectionSummary.perMetric[m].selectedInflectionValueOnSmoothCurve =
                    fitObjects[m].value(zSel);

                inflectionZAll[m] = zSel;
            }
        }

        // Inflection consensus
        boolean[] infConsensusMask = consensusMask1D(inflectionZAll, s.outlierTolerance_um,
                s.minAgreementCount, s.useMedianForConsensusCenter);
        int infConsensusCount = countTrue(infConsensusMask);
        for (int m = 0; m < nMetrics; m++)
            inflectionSummary.perMetric[m].isConsensusKept = infConsensusMask[m];

        if (anyTrue(infConsensusMask)) {
            inflectionSummary.avgInflectionZ = nanMean(maskValues(inflectionZAll, infConsensusMask));
        } else {
            inflectionSummary.avgInflectionZ = nanMean(inflectionZAll);
        }

        if (inflectionSummary.avgInflectionZ != null && Double.isFinite(inflectionSummary.avgInflectionZ)) {
            int[] cl = findClosestIndexAndZ(zSampled, inflectionSummary.avgInflectionZ);
            inflectionSummary.closestSampledIndex = cl[0];
            inflectionSummary.closestSampledZ = zSampled[cl[0]];
            inflectionSummary.status = "found_some";
        }

        

        // --- Best focus per metric ---
        BestPerMetric[] bestPerMetric = new BestPerMetric[nMetrics];
         
        double[] allValidBestZ = new double[nMetrics];
        String[] allCaseTypes = new String[nMetrics];
        Arrays.fill(allValidBestZ, Double.NaN);
        Arrays.fill(allCaseTypes, "");

        for (int m = 0; m < nMetrics; m++) {
            
            bestPerMetric[m] = new BestPerMetric();
            bestPerMetric[m].metricName = metricNames[m];
            

            if (!extremaAvailabilityMask[m]) {
                bestPerMetric[m].caseType = "ignored_by_extrema_availability_rule";
                bestPerMetric[m].decisionStatus = "ignored_by_extrema_availability_rule";
                bestPerMetric[m].status = "ignored";
                bestPerMetric[m].message = "Ignored because other graphs had a stronger shared extrema condition (" + extremaAvailabilityMode + ").";
                bestPerMetric[m].ignoredByExtremaAvailabilityRule = true;
                extremaResults[m].decisionStatus = "ignored_by_extrema_availability_rule";
                continue;
            }

            if (fitObjects[m] == null) {
                bestPerMetric[m].status = "invalid";
                bestPerMetric[m].message = "Fit object is empty.";
                continue;
            }

            ExtremumInfo repMax = extremaResults[m].representativeMax;
            ExtremumInfo repMin = extremaResults[m].representativeMin;
            boolean hasMax = repMax != null, hasMin = repMin != null;

            System.out.println("Metric: " + metricNames[m] + ", Representative max Z: " + (hasMax ? repMax.z : "null") + ", Representative min Z: " + (hasMin ? repMin.z : "null"));
            boolean hasInfl = inflectionSummary.perMetric[m].selectedInflectionZ != null
                    && (inflectionSummary.perMetric[m].isConsensusKept || infConsensusCount < s.minAgreementCount);
            double zInfl = hasInfl ? inflectionSummary.perMetric[m].selectedInflectionZ : Double.NaN;

            if (hasMax) { bestPerMetric[m].zMax = repMax.z; bestPerMetric[m].idxFineMax = repMax.idxFine; bestPerMetric[m].Fmax = repMax.value; }
            if (hasMin) { bestPerMetric[m].zMin = repMin.z; bestPerMetric[m].idxFineMin = repMin.idxFine; bestPerMetric[m].Fmin = repMin.value; }
            if (hasInfl) { bestPerMetric[m].zInflection = zInfl; }

            UnivariateFunction fit = fitObjects[m];
            double[] yf = colFromMatrix(smoothCurvesNorm, m, s.nFinePoints);

            if (hasMax && hasMin && repMax.z < repMin.z) {
                double dzMM = repMin.z - repMax.z;

                if (hasInfl && dzMM < s.bothMaxMinDistanceThreshold_um) {
                    // Original max-min rule regardless of bothWithInflectionMode
                    double target = repMax.value - s.thresholdFraction * (repMax.value - repMin.value);
                    double bestZ = findBestFocusDense(fit, repMax.z, repMin.z, target, s.nDenseCrossingPoints);
                    setBothMaxMin(bestPerMetric[m], bestZ, fit, zFine, zSampled, target, "both max-min",
                            String.format("Both max/min and inflection detected, but max-min distance=%.4f<%.4f, original rule used.", dzMM, s.bothMaxMinDistanceThreshold_um));
                    if (Double.isFinite(bestZ)) { allValidBestZ[m] = bestZ; allCaseTypes[m] = "both max-min"; }
                    continue;
                }

                if (hasInfl && s.bothWithInflectionMode == 1) {
                    double target = repMax.value - s.thresholdFraction * (repMax.value - repMin.value);
                    double bestZ = findBestFocusDense(fit, repMax.z, repMin.z, target, s.nDenseCrossingPoints);
                    setBothMaxMin(bestPerMetric[m], bestZ, fit, zFine, zSampled, target, "both max-min",
                            String.format("bothWithInflectionMode=1; max-min threshold rule applied. dz=%.4f", dzMM));
                    if (Double.isFinite(bestZ)) { allValidBestZ[m] = bestZ; allCaseTypes[m] = "both max-min"; }
                    continue;
                }

                if (hasInfl && s.bothWithInflectionMode == 0) {
                    double minInflDeltaz = repMin.z - zInfl;
                    double halfRemainder = (s.assumedMaxMinDistance_um - minInflDeltaz) / 2.0;
                    double bestZ = zInfl - s.minOnlyInflectionBeta * halfRemainder;
                    bestPerMetric[m].caseType = "both max-min but min-with-inflection used";
                    bestPerMetric[m].decisionStatus = "both max-min but min-with-inflection used";
                    bestPerMetric[m].status = "used";
                    bestPerMetric[m].message = String.format("bothWithInflectionMode=0; min-with-inflection rule, beta=%.4g", s.minOnlyInflectionBeta);
                    bestPerMetric[m].minInflDeltaz = minInflDeltaz;
                    bestPerMetric[m].halfRemainder = halfRemainder;
                    bestPerMetric[m].bestFocusZOnSmoothCurve = bestZ;
                    bestPerMetric[m].bestFocusValueOnSmoothCurve = fit.value(bestZ);
                    bestPerMetric[m].bestFocusIdxFine = argMinAbs(zFine, bestZ);
                    int[] sc = findClosestIndexAndZ(zSampled, bestZ);
                    bestPerMetric[m].bestFocusIdxSampled = sc[0];
                    bestPerMetric[m].bestFocusZSampledClosest = zSampled[sc[0]];
                    allValidBestZ[m] = bestZ; allCaseTypes[m] = "both max-min but min-with-inflection used";
                    continue;
                }

                if (hasInfl && s.bothWithInflectionMode == 2) {
                    double bestZ = repMax.z + s.maxOnlyInflectionAlpha * (zInfl - repMax.z);
                    bestPerMetric[m].caseType = "both max-min but max-with-inflection used";
                    bestPerMetric[m].decisionStatus = "both max-min but max-with-inflection used";
                    bestPerMetric[m].status = "used";
                    bestPerMetric[m].message = String.format("bothWithInflectionMode=2; max-with-inflection rule, alpha=%.4g", s.maxOnlyInflectionAlpha);
                    bestPerMetric[m].bestFocusZOnSmoothCurve = bestZ;
                    bestPerMetric[m].bestFocusValueOnSmoothCurve = fit.value(bestZ);
                    bestPerMetric[m].bestFocusIdxFine = argMinAbs(zFine, bestZ);
                    int[] sc = findClosestIndexAndZ(zSampled, bestZ);
                    bestPerMetric[m].bestFocusIdxSampled = sc[0]; bestPerMetric[m].bestFocusZSampledClosest = zSampled[sc[0]];
                    allValidBestZ[m] = bestZ; allCaseTypes[m] = "both max-min but max-with-inflection used";
                    continue;
                }

                // No inflection - original rule
                double target = repMax.value - s.thresholdFraction * (repMax.value - repMin.value);
                double bestZ = findBestFocusDense(fit, repMax.z, repMin.z, target, s.nDenseCrossingPoints);
                setBothMaxMin(bestPerMetric[m], bestZ, fit, zFine, zSampled, target, "both max-min",
                        "Used threshold-drop rule on dense evaluation of smoothing spline.");
                if (Double.isFinite(bestZ)) { allValidBestZ[m] = bestZ; allCaseTypes[m] = "both max-min"; }
                continue;
            }

            if (hasMax && !hasMin && hasInfl && zInfl > repMax.z) {
                double bestZ = repMax.z + s.maxOnlyInflectionAlpha * (zInfl - repMax.z);
                bestPerMetric[m].caseType = "max only with inflection point";
                bestPerMetric[m].decisionStatus = "max only with inflection point";
                bestPerMetric[m].status = "used";
                bestPerMetric[m].message = "Used alpha rule between max and inflection.";
                bestPerMetric[m].bestFocusZOnSmoothCurve = bestZ;
                bestPerMetric[m].bestFocusValueOnSmoothCurve = fit.value(bestZ);
                bestPerMetric[m].bestFocusIdxFine = argMinAbs(zFine, bestZ);
                int[] sc = findClosestIndexAndZ(zSampled, bestZ);
                bestPerMetric[m].bestFocusIdxSampled = sc[0]; bestPerMetric[m].bestFocusZSampledClosest = zSampled[sc[0]];
                allValidBestZ[m] = bestZ; allCaseTypes[m] = "max only with inflection point";
                continue;
            }

            if (hasMax && !hasMin && !hasInfl) {
                double deltaRight = zFine[zFine.length - 1] - repMax.z;
                bestPerMetric[m].deltaZToEdge = deltaRight;
                bestPerMetric[m].caseType = "max only";
                bestPerMetric[m].decisionStatus = "max only";
                if (deltaRight >= s.maxOnlyEdgeThreshold_um) {
                    double bestZ = repMax.z + s.maxOnlyShiftRight_um;
                    bestPerMetric[m].status = "used";
                    bestPerMetric[m].message = "Used fixed shift right from max.";
                    bestPerMetric[m].bestFocusZOnSmoothCurve = bestZ;
                    bestPerMetric[m].bestFocusValueOnSmoothCurve = fit.value(bestZ);
                    bestPerMetric[m].bestFocusIdxFine = argMinAbs(zFine, bestZ);
                    int[] sc = findClosestIndexAndZ(zSampled, bestZ);
                    bestPerMetric[m].bestFocusIdxSampled = sc[0]; bestPerMetric[m].bestFocusZSampledClosest = zSampled[sc[0]];
                    allValidBestZ[m] = bestZ; allCaseTypes[m] = "max only";
                } else {
                    bestPerMetric[m].status = "too_far";
                    bestPerMetric[m].message = "Too far from actual focus correct the z location";
                }
                continue;
            }

            if (!hasMax && hasMin && hasInfl && zInfl < repMin.z) {
                double minInflDeltaz = repMin.z - zInfl;
                double halfRemainder = (s.assumedMaxMinDistance_um - minInflDeltaz) / 2.0;
                double bestZ = zInfl - s.minOnlyInflectionBeta * halfRemainder;
                bestPerMetric[m].caseType = "min only with inflection point";
                bestPerMetric[m].decisionStatus = "min only with inflection point";
                bestPerMetric[m].status = "used";
                bestPerMetric[m].message = String.format("Used inflection-based estimate with beta=%.4g", s.minOnlyInflectionBeta);
                bestPerMetric[m].minInflDeltaz = minInflDeltaz; bestPerMetric[m].halfRemainder = halfRemainder;
                bestPerMetric[m].bestFocusZOnSmoothCurve = bestZ;
                bestPerMetric[m].bestFocusValueOnSmoothCurve = fit.value(bestZ);
                bestPerMetric[m].bestFocusIdxFine = argMinAbs(zFine, bestZ);
                int[] sc = findClosestIndexAndZ(zSampled, bestZ);
                bestPerMetric[m].bestFocusIdxSampled = sc[0]; bestPerMetric[m].bestFocusZSampledClosest = zSampled[sc[0]];
                allValidBestZ[m] = bestZ; allCaseTypes[m] = "min only with inflection point";
                continue;
            }

            if (!hasMax && hasMin && !hasInfl) {
                double deltaLeft = repMin.z - zFine[0];
                bestPerMetric[m].deltaZToEdge = deltaLeft;
                bestPerMetric[m].caseType = "min only";
                bestPerMetric[m].decisionStatus = "min only";
                if (deltaLeft >= s.minOnlyEdgeThreshold_um) {
                    double bestZ = repMin.z - s.minOnlyShiftLeft_um;
                    bestPerMetric[m].status = "used";
                    bestPerMetric[m].message = "Used fixed shift left from min.";
                    bestPerMetric[m].bestFocusZOnSmoothCurve = bestZ;
                    bestPerMetric[m].bestFocusValueOnSmoothCurve = fit.value(bestZ);
                    bestPerMetric[m].bestFocusIdxFine = argMinAbs(zFine, bestZ);
                    int[] sc = findClosestIndexAndZ(zSampled, bestZ);
                    bestPerMetric[m].bestFocusIdxSampled = sc[0]; bestPerMetric[m].bestFocusZSampledClosest = zSampled[sc[0]];
                    allValidBestZ[m] = bestZ; allCaseTypes[m] = "min only";
                } else {
                    bestPerMetric[m].status = "too_far";
                    bestPerMetric[m].message = "Too far from actual focus correct the z location";
                }
                continue;
            }

            if (hasMax && hasMin) {
                bestPerMetric[m].caseType = "invalid_order";
                bestPerMetric[m].decisionStatus = "invalid_order";
                bestPerMetric[m].status = "ignored";
                bestPerMetric[m].message = "Representative max and min exist but are in invalid order.";
                continue;
            }

            bestPerMetric[m].caseType = "none";
            bestPerMetric[m].decisionStatus = "none";
            bestPerMetric[m].status = "ignored";
            bestPerMetric[m].message = "No valid best-focus case identified.";
        }

        // --- Final best focus ---
        double zBestFocus = Double.NaN;
        System.out.println("Valid best-focus estimates per metric...");
        boolean[] validBestMask = new boolean[nMetrics];
        for (int m = 0; m < nMetrics; m++) {
            System.out.println("Metric: " + metricNames[m] + ", Best focus (smooth Z): " + bestPerMetric[m].bestFocusZOnSmoothCurve);
            validBestMask[m] = Double.isFinite(allValidBestZ[m]);
        }

        String[] validCaseTypes = maskStrings(allCaseTypes, validBestMask);
        String majorityCase = "";
        boolean[] majorityCaseMaskFull = new boolean[nMetrics];
        int majorityCount = 0;

        if (anyTrue(validBestMask)) {
            majorityCase = majorityCategory(validCaseTypes, s.minAgreementCount);
            for (String caseType : validCaseTypes) {
                if (majorityCase.equals(caseType)) {
                    majorityCount++;
                }
            }

            List<Integer> validIndices = new ArrayList<>();
            for (int m = 0; m < nMetrics; m++) {
                if (validBestMask[m]) {
                    validIndices.add(m);
                }
            }

            if (majorityCount >= s.minAgreementCount) {
                for (int i = 0; i < validIndices.size(); i++) {
                    int metricIndex = validIndices.get(i);
                    if (majorityCase.equals(validCaseTypes[i])) {
                        majorityCaseMaskFull[metricIndex] = true;
                    }
                }
            } else {
                for (int m = 0; m < nMetrics; m++) {
                    if (validBestMask[m]) {
                        majorityCaseMaskFull[m] = true;
                    }
                }
            }
        }

        double[] candidateBestZ = Arrays.copyOf(allValidBestZ, nMetrics);
        for (int m = 0; m < nMetrics; m++) {
            if (!majorityCaseMaskFull[m]) {
                candidateBestZ[m] = Double.NaN;
            }
        }

        boolean[] bestConsensusMask = consensusMask1D(candidateBestZ, s.outlierTolerance_um,
                s.minAgreementCount, s.useMedianForConsensusCenter);

        boolean[] finalBestMask;
        if (anyTrue(bestConsensusMask)) {
            finalBestMask = bestConsensusMask;
        } else if (anyTrue(majorityCaseMaskFull)) {
            finalBestMask = majorityCaseMaskFull;
        } else {
            finalBestMask = validBestMask;
        }

        for (int m = 0; m < nMetrics; m++) {
            bestPerMetric[m].isConsensusKept = finalBestMask[m];
        }

        if (anyTrue(finalBestMask)) {
            zBestFocus = nanMeanMasked(allValidBestZ, finalBestMask);
        }

        System.out.println("All best Z:");
        System.out.println(Arrays.toString(allValidBestZ));
        // --- Pack results ---
        System.out.println("Accepted extrema summary");
        Results results = new Results();
        results.nImagesSampled = n;
        results.zSampled = zSampled;
        results.dzActual = dzActual;
        results.idxZiniClosest = idxZiniClosest;
        results.zAtZiniClosest = zAtZiniClosest;
        results.metricNames = metricNames;
        results.metricValuesRaw = metricValuesRaw;
        results.metricValuesNorm = metricValuesNorm;
        results.zFine = zFine;
        results.smoothCurvesNorm = smoothCurvesNorm;
        results.secondDerivativeCurves = secondDerivativeCurves;
        results.extremaResults = extremaResults;
        results.inflectionSummary = inflectionSummary;
        results.bestFocusSummaryPerMetric = bestPerMetric;
        results.zBestFocus = zBestFocus;
        results.zIni = zIni;
        results.deltazSamp = deltazSamp;
        results.settings = s;

        // print out all values in bestPerMetric
        System.out.println("Best focus summary per metric:");
        for (BestPerMetric bpm : bestPerMetric) {
            System.out.println("Metric: " + bpm.metricName);
            System.out.println("  Case type: " + bpm.caseType);
            System.out.println("  Decision status: " + bpm.decisionStatus);
            System.out.println("  Status: " + bpm.status);
            System.out.println("  Message: " + bpm.message);
            System.out.println("  Target value: " + bpm.targetValue);
            System.out.println("  zMax: " + bpm.zMax);
            System.out.println("  Fmax: " + bpm.Fmax);
            System.out.println("  zMin: " + bpm.zMin);
            System.out.println("  Fmin: " + bpm.Fmin);
            System.out.println("  zInflection: " + bpm.zInflection);
            System.out.println("  Best focus Z on smooth curve: " + bpm.bestFocusZOnSmoothCurve);
            System.out.println("  Best focus value on smooth curve: " + bpm.bestFocusValueOnSmoothCurve);
            System.out.println("  Best focus idx fine: " + bpm.bestFocusIdxFine);
            System.out.println("  Best focus idx sampled: " + bpm.bestFocusIdxSampled);
            System.out.println("  Best focus Z sampled closest: " + bpm.bestFocusZSampledClosest);
        }




        return results;
    }

    // =========================================================================
    // Helper: set both max-min result on bestPerMetric
    // =========================================================================
    private static void setBothMaxMin(BestPerMetric bp, double bestZ, UnivariateFunction fit,
                                      double[] zFine, double[] zSampled, double target,
                                      String caseType, String msg) {
        bp.caseType = caseType;
        bp.decisionStatus = caseType;
        bp.status = "used";
        bp.message = msg;
        bp.targetValue = target;
        if (Double.isFinite(bestZ)) {
            bp.bestFocusZOnSmoothCurve = bestZ;
            bp.bestFocusValueOnSmoothCurve = fit.value(bestZ);
            bp.bestFocusIdxFine = argMinAbs(zFine, bestZ);
            int[] sc = findClosestIndexAndZ(zSampled, bestZ);
            bp.bestFocusIdxSampled = sc[0];
            bp.bestFocusZSampledClosest = zSampled[sc[0]];
        }
    }

    private static boolean hasFinite(Double v) {
        return v != null && Double.isFinite(v);
    }

    // =========
    // ================================================================
    // Focus metrics
    // =========================================================================

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


    // =========================================================================
    // Extrema detection
    // =========================================================================
    
    private static double[][] customExtrema(double[] z, double[] y) {
        int n = y.length;
        double[] dy = gradient(y, z);
        int[] signDy = new int[n];
        for (int i = 0; i < n; i++) signDy[i] = (int) Math.signum(dy[i]);
        for (int i = 1; i < n; i++) if (signDy[i] == 0) signDy[i] = signDy[i - 1];

        List<Integer> maxIdx = new ArrayList<>(), minIdx = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            int sc = signDy[i + 1] - signDy[i];
            if (sc < 0) maxIdx.add(i + 1);
            else if (sc > 0) minIdx.add(i + 1);
        }

        double[] maxLocs = new double[maxIdx.size()], maxWidths = new double[maxIdx.size()];
        double[] minLocs = new double[minIdx.size()], minWidths = new double[minIdx.size()];

        for (int k = 0; k < maxIdx.size(); k++) {
            int idx = maxIdx.get(k);
            maxLocs[k] = z[idx];
            maxWidths[k] = estimateFWHM(z, y, idx, "max");
        }
        for (int k = 0; k < minIdx.size(); k++) {
            int idx = minIdx.get(k);
            minLocs[k] = z[idx];
            minWidths[k] = estimateFWHM(z, y, idx, "min");
        }
        // left edge max
        if (y[0] > y[1]) maxIdx.add(0);

        // right edge max
        if (y[n - 1] > y[n - 2]) maxIdx.add(n - 1);
        return new double[][]{maxLocs, minLocs, maxWidths, minWidths};
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

    private static ExtremumInfo buildExtremumStruct(double z0, double width, double[] zFine,
                                                     double[] yFine, double[] zSampled) {
        ExtremumInfo e = new ExtremumInfo();
        e.z = z0;
        e.width = width;
        e.idxFine = argMinAbs(zFine, z0);
        e.zFineClosest = zFine[e.idxFine];
        e.value = interp1(zFine, yFine, z0);
        int[] sc = findClosestIndexAndZ(zSampled, z0);
        e.idxSampled = sc[0];
        e.zSampledClosest = zSampled[sc[0]];
        return e;
    }

    private static ExtremumInfo selectRepresentativeExtremum(List<ExtremumInfo> list) {
        if (list == null || list.isEmpty()) return null;
        // Select the one with the largest absolute value (most prominent)
        ExtremumInfo best = list.get(0);
        for (ExtremumInfo e : list)
            if (Math.abs(e.value) > Math.abs(best.value)) best = e;
        return best;
    }

    // =========================================================================
    // Inflection selection
    // =========================================================================
    // Returns [selectedZ (0 or 1 element), candidates]
    private static Object[] selectInflectionFromZeroCrossingsDirectional(double[] zeroZ, double refZ, double minDist, double maxDist, String direction) {
        double selectedZ = Double.NaN;
        String status = "no_inflection";
        double[] candidateZ = new double[0];

        // Handle edge cases
        if (zeroZ == null || zeroZ.length == 0 || Double.isNaN(refZ)) {
            return new Object[]{selectedZ, status, candidateZ};
        }

        // Validate direction
        if (!("after".equalsIgnoreCase(direction) || "before".equalsIgnoreCase(direction))) {
            throw new IllegalArgumentException("direction must be 'after' or 'before'.");
        }

        List<Double> candidates = new ArrayList<>();
        for (double z : zeroZ) {
            boolean dirMask = ("after".equalsIgnoreCase(direction)) ? (z > refZ) : (z < refZ);
            double dist = Math.abs(z - refZ);
            boolean validMask = dirMask && dist >= minDist && dist <= maxDist;

            if (validMask) {
                candidates.add(z);
            }
        }

        if (candidates.isEmpty()) {
            return new Object[]{selectedZ, status, candidateZ};
        }

        // Sort candidates
        Collections.sort(candidates);

        // Select median element (MATLAB: ceil(numel/2))
        int medianIdx = (int) Math.ceil(candidates.size() / 2.0) - 1;
        selectedZ = candidates.get(medianIdx);
        status = "found";
        candidateZ = candidates.stream().mapToDouble(Double::doubleValue).toArray();

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

    // =========================================================================
    // Dense best-focus search between max and min
    // =========================================================================
    private static double findBestFocusDense(UnivariateFunction fit, double zMaxLoc, double zMinLoc,
                                              double target, int nPoints) {
        double[] zd = linspace(zMaxLoc, zMinLoc, nPoints);
        double bestZ = Double.NaN;
        double bestDiff = Double.MAX_VALUE;
        for (double z : zd) {
            double diff = Math.abs(fit.value(z) - target);
            if (diff < bestDiff) { bestDiff = diff; bestZ = z; }
        }
        return bestZ;
    }

    // =========================================================================
    // Consensus mask
    // =========================================================================
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

    // =========================================================================
    // Math utilities
    // =========================================================================
    private static double[][] normalizeMetrics(double[][] M) {
        int rows = M.length, cols = M[0].length;
        double[][] N = new double[rows][cols];
        for (int j = 0; j < cols; j++) {
            double cmin = Double.MAX_VALUE, cmax = -Double.MAX_VALUE;
            for (int i = 0; i < rows; i++) {
                if (Double.isFinite(M[i][j])) { cmin = Math.min(cmin, M[i][j]); cmax = Math.max(cmax, M[i][j]); }
            }
            for (int i = 0; i < rows; i++)
                N[i][j] = (Math.abs(cmax - cmin) < 1e-12) ? 0.0 : (M[i][j] - cmin) / (cmax - cmin);
        }
        return N;
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

    private static double interp1(double[] x, double[] y, double xi) {
        int n = x.length;
        if (xi <= x[0]) return y[0];
        if (xi >= x[n - 1]) return y[n - 1];
        int lo = argMinAbs(x, xi);
        if (lo >= n - 1) lo = n - 2;
        double t = (xi - x[lo]) / (x[lo + 1] - x[lo]);
        return y[lo] + t * (y[lo + 1] - y[lo]);
    }

    private static double[] linspace(double start, double end, int n) {
        double[] result = new double[n];
        double step = (end - start) / (n - 1);
        for (int i = 0; i < n; i++) {
            result[i] = start + i * step;
        }
        return result;
    }

    private static double[] diff(double[] arr) {
        double[] result = new double[arr.length - 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = arr[i + 1] - arr[i];
        }
        return result;
    }

    private static double mean(double[] arr) {
        return StatUtils.mean(arr);
    }


    private static double median(List<Double> vals) {
        List<Double> sorted = new ArrayList<>(vals);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0 : sorted.get(n / 2);
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

    private static int argMinAbs(double[] arr, double target) {
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

    private static double[] col(double[][] M, int j) {
        double[] c = new double[M.length];
        for (int i = 0; i < M.length; i++) c[i] = M[i][j];
        return c;
    }

    private static double[] colFromMatrix(double[][] M, int j, int rows) {
        double[] c = new double[rows];
        for (int i = 0; i < rows; i++) c[i] = M[i][j];
        return c;
    }

    private static boolean[] validMask(double[] y) {
        boolean[] m = new boolean[y.length];
        for (int i = 0; i < y.length; i++) m[i] = Double.isFinite(y[i]);
        return m;
    }

    private static int countTrue(boolean[] m) {
        int c = 0; for (boolean b : m) if (b) c++; return c;
    }

    private static boolean anyTrue(boolean[] m) {
        for (boolean b : m) if (b) return true; return false;
    }

    private static double[] filter(double[] a, boolean[] mask) {
        int c = countTrue(mask);
        double[] r = new double[c];
        int j = 0;
        for (int i = 0; i < a.length; i++) if (mask[i]) r[j++] = a[i];
        return r;
    }

    private static double[] maskValues(double[] a, boolean[] mask) {
        return filter(a, mask);
    }

    private static String[] maskStrings(String[] a, boolean[] mask) {
        int c = countTrue(mask);
        String[] r = new String[c];
        int j = 0;
        for (int i = 0; i < a.length; i++) if (mask[i]) r[j++] = a[i];
        return r;
    }

    private static Double nanMean(double[] a) {
        if (a == null || a.length == 0) return null;
        // if a is ful of NaNs return null, otherwise return mean of finite values
        if (Arrays.stream(a).allMatch(v -> !Double.isFinite(v))) return Double.NaN;

        double s = 0; int c = 0;
        for (double v : a) if (Double.isFinite(v)) { s += v; c++; }
        return c == 0 ? null : s / c;
    }

    private static double nanMeanMasked(double[] a, boolean[] mask) {
        System.out.println("nanMeanMasked input:");
        System.out.println("a: " + Arrays.toString(a));
        System.out.println("mask: " + Arrays.toString(mask));
        double s = 0; int c = 0;
        for (int i = 0; i < a.length; i++)
            if (mask[i] && Double.isFinite(a[i])) { s += a[i]; c++; }
        return c == 0 ? Double.NaN : s / c;
    }

    private static String majorityCategory(String[] cases, int minCount) {
        if (cases == null || cases.length == 0) return "";
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String c : cases) freq.merge(c, 1, Integer::sum);
        String best = ""; int bestCount = 0;
        for (Map.Entry<String, Integer> e : freq.entrySet())
            if (e.getValue() > bestCount) { bestCount = e.getValue(); best = e.getKey(); }
        return bestCount >= minCount ? best : "";
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

    //saveSmoothCurveToCSV(zFine, col(smoothCurvesNorm, m), metricNames[m] + "_smooth_curve.csv"); 
    private static void saveSmoothCurveToCSV(double[] z, double[] curve, String filename) {
        System.out.println("Saving smooth curve to " + filename);
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (int i = 0; i < z.length; i++) {
                writer.printf("%f,%f%n", z[i], curve[i]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}