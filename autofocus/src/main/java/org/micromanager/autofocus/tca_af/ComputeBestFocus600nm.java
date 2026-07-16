package org.micromanager.autofocus.tca_af;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ij.process.ImageProcessor;

public class ComputeBestFocus600nm {

    public static class FocusOutput {
        public Results results;
        public List<AcceptedPeakSummary> acceptedPeakSummary;
        public double z_best_focus;
    }

    public static class Results {
        public int nImagesSampled;
        public String[] metricNames;
        public double[][] metricValuesRaw;
        public double[][] metricValuesNorm;
        public double[] zSampled;
        public double[] zFine;
        public double[][] smoothCurvesNorm;
        public double[][] cleanCurvesNorm;
        public List<SplineModel> splineModels;
        public List<SplineModel> fitObjects;
        public List<CleaningSummary> cleaningSummary;
        public List<PerMetric> perMetric;
        public List<AcceptedPeakSummary> acceptedPeakSummary;
        public double[] leftDropZAll;
        public FinalDecision finalDecision;
        public double z_best_focus;
        public double z_ini;
        public double deltaz_samp;
        public double dzActual;
        public int idxZiniClosest;
        public double zAtZiniClosest;
        public Settings settings;
        public List<ThresholdInfo> thresholdInfo;
        public double meanNonZeroPixelValue;
        public double intensityThreshold;
        public double intensityThresholdCoefficient;
    }

    public static class Settings {
        public double smoothingParam = 0.1;
        public int nFinePoints = 5000;
        public int nDenseCrossingPoints = 50000;

        public boolean rejectNarrowExtrema = true;
        public int nSpikeCleanPasses = 2;
        public double narrowSpikeFWHM_um = 10.0;
        public double minSpikeHalfWindow_um = 0.75;

        public double minBroadPeakFWHM_um = 30.0;
        public double leftDropFraction = 0.10;
        public double targetFractionOfPeak = 1.0 - leftDropFraction;

        public boolean showPlots = true;
        public boolean showCleanedCurve = true;
        public boolean showFinalBestFocusLineOnAllPlots = true;

        public double intensityThresholdCoefficient = 1.5;
        public boolean applyIntensityThreshold = true;
        public String thresholdMode = "clip_above";

        public double meanNonZeroPixelValue;
        public double intensityThreshold;
    }

    public static class ThresholdInfo {
        public int imageIndex;
        public double z;
        public boolean thresholdApplied;
        public double thresholdValue;
        public double thresholdCoefficient;
        public double meanNonZeroPixelValue;
        public int numPixelsClipped;
        public double fractionPixelsClipped;
    }

    public static class PerMetric {
        public String metricName = "";
        public String status = "not_used";
        public String message = "";
        public double zPeak = Double.NaN;
        public double peakValue = Double.NaN;
        public double peakFWHM_um = Double.NaN;
        public double peakHalfLevel = Double.NaN;
        public double peakLeftHalfZ = Double.NaN;
        public double peakRightHalfZ = Double.NaN;
        public int idxFinePeak = -1;
        public int idxSampledPeak = -1;
        public double zSampledPeakClosest = Double.NaN;
        public double targetValue = Double.NaN;
        public double zLeftDrop = Double.NaN;
        public int idxFineLeftDropClosest = -1;
        public int idxFineLeftDropClosest_validCurve = -1;
        public int idxSampledLeftDropClosest = -1;
        public double zSampledLeftDropClosest = Double.NaN;
        public boolean usedInFinalPair = false;
        public List<PeakCandidate> allPeakCandidates = new ArrayList<>();
    }

    public static class PeakCandidate {
        public int idx;       // MATLAB-style 1-based index
        public int idx0;      // Java-style 0-based index
        public double z;
        public double value;
        public double fwhm_um;
        public double halfLevel;
        public double leftHalfZ;
        public double rightHalfZ;
    }

    public static class AcceptedPeakSummary {
        public String metricName;
        public String status;
        public String message;
        public double zPeak;
        public double peakValue;
        public double peakFWHM_um;
        public double targetValue_90PercentPeak;
        public double zLeftDrop;
        public int idxFinePeak;
        public int idxSampledPeak;
        public int idxSampledLeftDropClosest;
        public double zSampledLeftDropClosest;
        public boolean usedInFinalPair;
        public int nNarrowExtremaRemoved;
    }

    public static class CleaningSummary {
        public List<RemovedExtremum> removedExtrema = new ArrayList<>();
        public int nRemoved;
    }

    public static class RemovedExtremum {
        public String type;
        public double z;
        public int idx;       // MATLAB-style 1-based index
        public double width_um;
        public double replaceLeftZ;
        public double replaceRightZ;
    }

    public static class FinalDecision {
        public String caseUsed;
        public String note;
        public double[] zVals;
        public boolean[] validMask;
        public boolean[] usedMask;
        public String[] metricNames;
        public double z_best_focus;
    }

    public static class SplineModel {
        public String kind;
        public double[] coeff;
        public PP pp;
        public double[] x;
        public double[] y;
        public double[] yfit;
        public double p;
        public double[] h;
        public double[][] Q;
        public double[][] R;
    }

    public static class PP {
        public String form = "pp";
        public double[] breaks;
        public double[][] coefs;
        public int pieces;
        public int order = 4;
        public int dim = 1;
    }

    public static class FitResult {
        public SplineModel model;
    }

    public static class ExtremaResult {
        public int[] maxIdx;
        public int[] minIdx;
        public double[] maxWidths;
        public double[] minWidths;
    }

    public static class FwhmResult {
        public double w;
        public double halfLevel;
        public double leftHalfZ;
        public double rightHalfZ;
    }

    public static class CrossingResult {
        public double zCross = Double.NaN;
        public int idxFineClosest = -1;
        public String message = "";
    }

    public static class ClosestResult {
        public int idx;       // MATLAB-style 1-based index
        public double zClosest;
    }

    public static Results computeBestFocus(
            List<ImageProcessor> imageArray,
            double z_ini,
            double deltaz_samp,
            double[] zSampledInput
    ) {

        Settings settings = new Settings();

        if (imageArray == null || imageArray.isEmpty()) {
            throw new IllegalArgumentException("imageArray must be a non-empty list.");
        }

        if (zSampledInput == null || zSampledInput.length == 0) {
            throw new IllegalArgumentException("zSampled must be a non-empty vector.");
        }

        double[] zSampled = zSampledInput.clone();

        if (imageArray.size() != zSampled.length) {
            throw new IllegalArgumentException("imageArray length must match zSampled length.");
        }

        if (zSampled.length < 5) {
            throw new IllegalArgumentException("Too few sampled z points. At least 5 are required.");
        }

        for (double v : zSampled) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("zSampled contains NaN or Inf.");
            }
        }

        for (int i = 0; i < zSampled.length - 1; i++) {
            if (zSampled[i + 1] <= zSampled[i]) {
                throw new IllegalArgumentException("zSampled must be strictly increasing. Check image sorting / z assignment.");
            }
        }

        if (!Double.isFinite(settings.intensityThresholdCoefficient)) {
            throw new IllegalArgumentException("settings.intensityThresholdCoefficient must be a finite numeric scalar.");
        }

        double meanNonZeroPixelValue = findMeanNonZeroPixelValueInImageArray(imageArray);
        double intensityThreshold = settings.intensityThresholdCoefficient * meanNonZeroPixelValue;
        settings.meanNonZeroPixelValue = meanNonZeroPixelValue;
        settings.intensityThreshold = intensityThreshold;

        int nImagesSampled = zSampled.length;
        double dzActual = nImagesSampled > 1 ? mean(diff(zSampled)) : Double.NaN;

        int idxZiniClosest0 = argMinAbs(zSampled, z_ini);
        int idxZiniClosest = idxZiniClosest0 + 1;
        double zAtZiniClosest = zSampled[idxZiniClosest0];

        String[] metricNames = new String[]{"Variance", "Normalized Variance", "Vollath F5"};
        int nMetrics = metricNames.length;
        double[][] metricValuesRaw = fillMatrix(nImagesSampled, nMetrics, Double.NaN);
        List<ThresholdInfo> thresholdInfo = new ArrayList<>();

        for (int i = 0; i < nImagesSampled; i++) {
            double[][] I = imageProcessorToDoubleArray(imageArray.get(i));

            ThresholdInfo info;
            if (settings.applyIntensityThreshold) {
                ThresholdApplyResult tr = applyIntensityThresholdToImage(
                        I,
                        intensityThreshold,
                        settings.intensityThresholdCoefficient,
                        meanNonZeroPixelValue,
                        i + 1,
                        zSampled[i]
                );
                I = tr.image;
                info = tr.info;
            } else {
                info = new ThresholdInfo();
                info.imageIndex = i + 1;
                info.z = zSampled[i];
                info.thresholdApplied = false;
                info.thresholdValue = Double.NaN;
                info.thresholdCoefficient = Double.NaN;
                info.meanNonZeroPixelValue = Double.NaN;
                info.numPixelsClipped = 0;
                info.fractionPixelsClipped = 0;
            }

            thresholdInfo.add(info);

            metricValuesRaw[i][0] = calcVarianceMetric(I);
            metricValuesRaw[i][1] = calcNormalizedVarianceMetric(I);
            metricValuesRaw[i][2] = calcVollathF5(I);
        }

        double[][] metricValuesNorm = normalizeMetricsForPlot(metricValuesRaw);

        double[] zFine = linspace(min(zSampled), max(zSampled), settings.nFinePoints);
        double[][] smoothCurvesNorm = fillMatrix(zFine.length, nMetrics, Double.NaN);
        double[][] cleanCurvesNorm = fillMatrix(zFine.length, nMetrics, Double.NaN);

        List<SplineModel> splineModels = new ArrayList<>();
        List<CleaningSummary> cleaningSummary = new ArrayList<>();
        for (int i = 0; i < nMetrics; i++) {
            splineModels.add(null);
            cleaningSummary.add(null);
        }

        for (int m = 0; m < nMetrics; m++) {
            double[] y = getColumn(metricValuesNorm, m);
            boolean[] validMask = finiteMask(y, zSampled);

            if (countTrue(validMask) < 4) {
                System.err.println("Warning: Metric " + metricNames[m] + " has too few valid points for smoothing.");
                CleaningSummary cs = new CleaningSummary();
                cs.nRemoved = -1;
                cleaningSummary.set(m, cs);
                continue;
            }

            double[] xv = filterByMask(zSampled, validMask);
            double[] yv = filterByMask(y, validMask);
            FitResult fr = localSmoothingSplineFit(xv, yv, settings.smoothingParam);
            splineModels.set(m, fr.model);

            double[] ySmooth = ppvalOrPoly(fr.model, zFine);
            setColumn(smoothCurvesNorm, m, ySmooth);

            double[] yClean;
            CleaningSummary cleanInfo;
            if (settings.rejectNarrowExtrema) {
                RemoveResult rr = removeNarrowSpikesAndDips(zFine, ySmooth, settings);
                yClean = rr.yClean;
                cleanInfo = rr.cleanInfo;
            } else {
                yClean = ySmooth.clone();
                cleanInfo = new CleaningSummary();
                cleanInfo.nRemoved = 0;
            }

            setColumn(cleanCurvesNorm, m, yClean);
            cleaningSummary.set(m, cleanInfo);
        }

        List<PerMetric> perMetric = new ArrayList<>();
        double[] leftDropZAll = fillArray(nMetrics, Double.NaN);

        for (int m = 0; m < nMetrics; m++) {
            PerMetric pm = emptyPerMetricStruct();
            pm.metricName = metricNames[m];

            double[] yClean = getColumn(cleanCurvesNorm, m);

            if (allNotFinite(yClean)) {
                pm.status = "invalid_curve";
                pm.message = "No valid smoothed / cleaned curve.";
                perMetric.add(pm);
                continue;
            }

            PerMetric peakInfo = findBroadPeakAndLeftDrop(zFine, yClean, z_ini, settings);
            copyPerMetricFields(peakInfo, pm);

            if (Double.isFinite(pm.zPeak)) {
                ClosestResult cr = findClosestIndexAndZ(zSampled, pm.zPeak);
                pm.idxSampledPeak = cr.idx;
                pm.zSampledPeakClosest = cr.zClosest;
            }

            if (Double.isFinite(pm.zLeftDrop)) {
                ClosestResult cr = findClosestIndexAndZ(zSampled, pm.zLeftDrop);
                pm.idxSampledLeftDropClosest = cr.idx;
                pm.zSampledLeftDropClosest = cr.zClosest;
            }

            if (pm.status.equalsIgnoreCase("used") && Double.isFinite(pm.zLeftDrop)) {
                leftDropZAll[m] = pm.zLeftDrop;
            }

            perMetric.add(pm);
        }

        ChooseResult choose = chooseClosestPair(leftDropZAll, metricNames);
        double z_best_focus = choose.zBest;
        FinalDecision finalDecision = choose.decision;

        for (int m = 0; m < nMetrics; m++) {
            perMetric.get(m).usedInFinalPair = finalDecision.usedMask[m];
        }

        List<AcceptedPeakSummary> acceptedPeakSummary = new ArrayList<>();
        for (int m = 0; m < nMetrics; m++) {
            PerMetric pm = perMetric.get(m);
            AcceptedPeakSummary s = new AcceptedPeakSummary();
            s.metricName = pm.metricName;
            s.status = pm.status;
            s.message = pm.message;
            s.zPeak = pm.zPeak;
            s.peakValue = pm.peakValue;
            s.peakFWHM_um = pm.peakFWHM_um;
            s.targetValue_90PercentPeak = pm.targetValue;
            s.zLeftDrop = pm.zLeftDrop;
            s.idxFinePeak = pm.idxFinePeak;
            s.idxSampledPeak = pm.idxSampledPeak;
            s.idxSampledLeftDropClosest = pm.idxSampledLeftDropClosest;
            s.zSampledLeftDropClosest = pm.zSampledLeftDropClosest;
            s.usedInFinalPair = pm.usedInFinalPair;

            CleaningSummary cs = cleaningSummary.get(m);
            s.nNarrowExtremaRemoved = cs == null ? -1 : cs.nRemoved;

            acceptedPeakSummary.add(s);
        }

        if (settings.showPlots) {
            showPlotsIfPossible(zSampled, metricValuesNorm, zFine, smoothCurvesNorm, cleanCurvesNorm,
                    perMetric, z_ini, z_best_focus, settings);
        }

        Results results = new Results();
        results.metricNames = metricNames;
        results.metricValuesRaw = metricValuesRaw;
        results.metricValuesNorm = metricValuesNorm;
        results.zSampled = zSampled;
        results.zFine = zFine;
        results.smoothCurvesNorm = smoothCurvesNorm;
        results.cleanCurvesNorm = cleanCurvesNorm;
        results.splineModels = splineModels;
        results.cleaningSummary = cleaningSummary;
        results.perMetric = perMetric;
        results.acceptedPeakSummary = acceptedPeakSummary;
        results.leftDropZAll = leftDropZAll;
        results.finalDecision = finalDecision;
        results.z_best_focus = z_best_focus;
        results.z_ini = z_ini;
        results.deltaz_samp = deltaz_samp;
        results.dzActual = dzActual;
        results.idxZiniClosest = idxZiniClosest;
        results.zAtZiniClosest = zAtZiniClosest;
        results.settings = settings;
        results.thresholdInfo = thresholdInfo;
        results.meanNonZeroPixelValue = meanNonZeroPixelValue;
        results.intensityThreshold = intensityThreshold;
        results.intensityThresholdCoefficient = settings.intensityThresholdCoefficient;

        results.nImagesSampled = nImagesSampled;
        results.fitObjects = splineModels;
        return results;
    }

    public static FocusOutput best_focus_from_sampled_images_for_600nm(
            List<ImageProcessor> imageArray,
            double z_ini,
            double deltaz_samp,
            double[] zSampledInput
    ) {
        Results results = computeBestFocus(imageArray, z_ini, deltaz_samp, zSampledInput);

        FocusOutput out = new FocusOutput();
        out.results = results;
        out.acceptedPeakSummary = results.acceptedPeakSummary;
        out.z_best_focus = results.z_best_focus;
        return out;
    }

    private static double findMeanNonZeroPixelValueInImageArray(List<ImageProcessor> imageArray) {
        double totalSum = 0;
        long totalCount = 0;

        for (ImageProcessor ip : imageArray) {
            if (ip == null) continue;
            double[][] I = imageProcessorToDoubleArray(ip);
            for (double[] row : I) {
                for (double v : row) {
                    if (Double.isFinite(v) && v != 0) {
                        totalSum += v;
                        totalCount++;
                    }
                }
            }
        }

        if (totalCount == 0) {
            throw new IllegalArgumentException("Could not determine mean non-zero pixel value from imageArray because no finite non-zero pixels were found.");
        }

        return totalSum / totalCount;
    }

    private static double[][] imageProcessorToDoubleArray(ImageProcessor ip) {
        if (ip == null) {
            return new double[0][0];
        }

        int width = ip.getWidth();
        int height = ip.getHeight();
        double[][] out = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y][x] = ip.getPixelValue(x, y);
            }
        }
        return out;
    }

    private static class ThresholdApplyResult {
        double[][] image;
        ThresholdInfo info;
    }

    private static ThresholdApplyResult applyIntensityThresholdToImage(
            double[][] I,
            double intensityThreshold,
            double thresholdCoefficient,
            double meanNonZeroPixelValue,
            int imageIndex,
            double zValue
    ) {
        double[][] Iout = copyImage(I);
        int numClipped = 0;
        int nPix = 0;

        for (int r = 0; r < Iout.length; r++) {
            for (int c = 0; c < Iout[r].length; c++) {
                nPix++;
                if (Iout[r][c] > intensityThreshold) {
                    Iout[r][c] = intensityThreshold;
                    numClipped++;
                }
            }
        }

        ThresholdInfo info = new ThresholdInfo();
        info.imageIndex = imageIndex;
        info.z = zValue;
        info.thresholdApplied = true;
        info.thresholdValue = intensityThreshold;
        info.thresholdCoefficient = thresholdCoefficient;
        info.meanNonZeroPixelValue = meanNonZeroPixelValue;
        info.numPixelsClipped = numClipped;
        info.fractionPixelsClipped = (double) numClipped / nPix;

        ThresholdApplyResult out = new ThresholdApplyResult();
        out.image = Iout;
        out.info = info;
        return out;
    }

    private static double calcVarianceMetric(double[][] I) {
        int n = 0;
        double sum = 0;
        for (double[] row : I) {
            for (double v : row) {
                sum += v;
                n++;
            }
        }
        double mu = sum / n;
        double ss = 0;
        for (double[] row : I) {
            for (double v : row) {
                double d = v - mu;
                ss += d * d;
            }
        }
        return ss / n;
    }

    private static double calcNormalizedVarianceMetric(double[][] I) {
        double epsVal = 1e-12;
        int n = 0;
        double sum = 0;
        for (double[] row : I) {
            for (double v : row) {
                sum += v;
                n++;
            }
        }
        double mu = sum / n;
        double varI = calcVarianceMetric(I);
        return varI / (mu + epsVal);
    }

    private static double calcVollathF5(double[][] I) {
        int rows = I.length;
        int cols = rows > 0 ? I[0].length : 0;

        if (cols < 2) return Double.NaN;

        int n = rows * cols;
        double sum = 0;
        for (double[] row : I) {
            for (double v : row) sum += v;
        }
        double mu = sum / n;

        double term1 = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                term1 += I[r][c] * I[r][c + 1];
            }
        }

        return term1 - n * mu * mu;
    }

    private static double[][] normalizeMetricsForPlot(double[][] M) {
        int rows = M.length;
        int cols = M[0].length;
        double[][] Mnorm = fillMatrix(rows, cols, Double.NaN);

        for (int j = 0; j < cols; j++) {
            double cmin = Double.POSITIVE_INFINITY;
            double cmax = Double.NEGATIVE_INFINITY;
            boolean any = false;

            for (int i = 0; i < rows; i++) {
                double v = M[i][j];
                if (Double.isFinite(v)) {
                    any = true;
                    if (v < cmin) cmin = v;
                    if (v > cmax) cmax = v;
                }
            }

            if (!any) continue;

            if (Math.abs(cmax - cmin) < 1e-12) {
                for (int i = 0; i < rows; i++) {
                    Mnorm[i][j] = Double.isFinite(M[i][j]) ? 0 : Double.NaN;
                }
            } else {
                for (int i = 0; i < rows; i++) {
                    if (Double.isFinite(M[i][j])) {
                        Mnorm[i][j] = (M[i][j] - cmin) / (cmax - cmin);
                    } else {
                        Mnorm[i][j] = Double.NaN;
                    }
                }
            }
        }

        return Mnorm;
    }

    private static class RemoveResult {
        double[] yClean;
        CleaningSummary cleanInfo;
    }

    private static RemoveResult removeNarrowSpikesAndDips(double[] z, double[] y, Settings settings) {
        double[] yClean = y.clone();
        CleaningSummary cleanInfo = new CleaningSummary();

        for (int pass = 0; pass < settings.nSpikeCleanPasses; pass++) {
            ExtremaResult er = customExtremaIndices(z, yClean);

            List<Integer> candidateIdx = new ArrayList<>();
            List<String> candidateType = new ArrayList<>();
            List<Double> candidateWidth = new ArrayList<>();

            for (int k = 0; k < er.maxIdx.length; k++) {
                if (Double.isFinite(er.maxWidths[k]) && er.maxWidths[k] < settings.narrowSpikeFWHM_um) {
                    candidateIdx.add(er.maxIdx[k]);
                    candidateType.add("max");
                    candidateWidth.add(er.maxWidths[k]);
                }
            }

            for (int k = 0; k < er.minIdx.length; k++) {
                if (Double.isFinite(er.minWidths[k]) && er.minWidths[k] < settings.narrowSpikeFWHM_um) {
                    candidateIdx.add(er.minIdx[k]);
                    candidateType.add("min");
                    candidateWidth.add(er.minWidths[k]);
                }
            }

            if (candidateIdx.isEmpty()) break;

            Integer[] order = new Integer[candidateIdx.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Integer.compare(candidateIdx.get(a), candidateIdx.get(b)));

            List<Integer> sortedIdx = new ArrayList<>();
            List<String> sortedType = new ArrayList<>();
            List<Double> sortedWidth = new ArrayList<>();
            for (int oi : order) {
                sortedIdx.add(candidateIdx.get(oi));
                sortedType.add(candidateType.get(oi));
                sortedWidth.add(candidateWidth.get(oi));
            }

            for (int c = 0; c < sortedIdx.size(); c++) {
                int idx0 = sortedIdx.get(c);

                if (idx0 <= 2 || idx0 >= z.length - 2) continue;

                double halfWin = Math.max(settings.minSpikeHalfWindow_um, 0.75 * sortedWidth.get(c));

                int idxL = -1;
                for (int i = 0; i < z.length; i++) {
                    if (z[i] <= z[idx0] - halfWin) idxL = i;
                }

                int idxR = -1;
                for (int i = 0; i < z.length; i++) {
                    if (z[i] >= z[idx0] + halfWin) {
                        idxR = i;
                        break;
                    }
                }

                if (idxL < 0 || idxR < 0 || idxL >= idxR) continue;

                int idxAnchorL = Math.max(0, idxL - 1);
                int idxAnchorR = Math.min(z.length - 1, idxR + 1);

                if (idxAnchorL >= idxAnchorR) continue;

                for (int i = idxL; i <= idxR; i++) {
                    yClean[i] = linearInterpolateTwoPoints(
                            z[i],
                            z[idxAnchorL], yClean[idxAnchorL],
                            z[idxAnchorR], yClean[idxAnchorR]
                    );
                }

                RemovedExtremum re = new RemovedExtremum();
                re.type = sortedType.get(c);
                re.z = z[idx0];
                re.idx = idx0 + 1;
                re.width_um = sortedWidth.get(c);
                re.replaceLeftZ = z[idxL];
                re.replaceRightZ = z[idxR];
                cleanInfo.removedExtrema.add(re);
            }
        }

        cleanInfo.nRemoved = cleanInfo.removedExtrema.size();

        RemoveResult out = new RemoveResult();
        out.yClean = yClean;
        out.cleanInfo = cleanInfo;
        return out;
    }

    private static PerMetric findBroadPeakAndLeftDrop(double[] zFine, double[] yClean, double z_ini, Settings settings) {
        PerMetric peakInfo = emptyPerMetricStruct();

        boolean[] validCurve = finiteMask(zFine, yClean);
        if (countTrue(validCurve) < 5) {
            peakInfo.status = "invalid_curve";
            peakInfo.message = "Too few finite points in cleaned curve.";
            return peakInfo;
        }

        double[] z = filterByMask(zFine, validCurve);
        double[] y = filterByMask(yClean, validCurve);

        ExtremaResult er = customExtremaIndices(z, y);

        if (er.maxIdx.length == 0) {
            peakInfo.status = "no_peak";
            peakInfo.message = "No local maximum was found on the cleaned smoothed curve.";
            return peakInfo;
        }

        List<PeakCandidate> peakCandidates = new ArrayList<>();
        for (int idx0 : er.maxIdx) {
            FwhmResult fw = estimateFWHM(z, y, idx0, "max");
            PeakCandidate pc = new PeakCandidate();
            pc.idx = idx0 + 1;
            pc.idx0 = idx0;
            pc.z = z[idx0];
            pc.value = y[idx0];
            pc.fwhm_um = fw.w;
            pc.halfLevel = fw.halfLevel;
            pc.leftHalfZ = fw.leftHalfZ;
            pc.rightHalfZ = fw.rightHalfZ;
            peakCandidates.add(pc);
        }

        peakInfo.allPeakCandidates = peakCandidates;

        List<Integer> broadIdxList = new ArrayList<>();
        for (int i = 0; i < peakCandidates.size(); i++) {
            double w = peakCandidates.get(i).fwhm_um;
            if (Double.isFinite(w) && w >= settings.minBroadPeakFWHM_um) {
                broadIdxList.add(i);
            }
        }

        if (broadIdxList.isEmpty()) {
            peakInfo.status = "no_broad_peak";
            peakInfo.message = String.format("No peak with FWHM >= %.3f um was found.", settings.minBroadPeakFWHM_um);
            return peakInfo;
        }

        double maxVal = Double.NEGATIVE_INFINITY;
        for (int idx : broadIdxList) {
            double v = peakCandidates.get(idx).value;
            if (v > maxVal) maxVal = v;
        }

        List<Integer> ties = new ArrayList<>();
        for (int idx : broadIdxList) {
            if (Math.abs(peakCandidates.get(idx).value - maxVal) < 1e-12) {
                ties.add(idx);
            }
        }

        int chosenIdxInCandidates;
        if (ties.size() > 1) {
            double bestDist = Double.POSITIVE_INFINITY;
            chosenIdxInCandidates = ties.get(0);
            for (int idx : ties) {
                double d = Math.abs(peakCandidates.get(idx).z - z_ini);
                if (d < bestDist) {
                    bestDist = d;
                    chosenIdxInCandidates = idx;
                }
            }
        } else {
            chosenIdxInCandidates = ties.get(0);
        }

        PeakCandidate chosenPeak = peakCandidates.get(chosenIdxInCandidates);
        double zPeak = chosenPeak.z;
        double peakValue = chosenPeak.value;
        double targetValue = settings.targetFractionOfPeak * peakValue;

        CrossingResult cr = findLeftTargetCrossingDense(z, y, zPeak, targetValue, settings.nDenseCrossingPoints);

        if (!Double.isFinite(cr.zCross)) {
            peakInfo.status = "no_left_drop_crossing";
            peakInfo.message = cr.message;
            peakInfo.zPeak = zPeak;
            peakInfo.peakValue = peakValue;
            peakInfo.peakFWHM_um = chosenPeak.fwhm_um;
            peakInfo.peakHalfLevel = chosenPeak.halfLevel;
            peakInfo.peakLeftHalfZ = chosenPeak.leftHalfZ;
            peakInfo.peakRightHalfZ = chosenPeak.rightHalfZ;
            peakInfo.idxFinePeak = chosenPeak.idx;
            peakInfo.targetValue = targetValue;
            return peakInfo;
        }

        int idxFinePeakOriginal = argMinAbs(zFine, zPeak) + 1;
        int idxFineLeftOriginal = argMinAbs(zFine, cr.zCross) + 1;

        peakInfo.status = "used";
        peakInfo.message = "Used broad-peak left-side 10% drop rule.";
        peakInfo.zPeak = zPeak;
        peakInfo.peakValue = peakValue;
        peakInfo.peakFWHM_um = chosenPeak.fwhm_um;
        peakInfo.peakHalfLevel = chosenPeak.halfLevel;
        peakInfo.peakLeftHalfZ = chosenPeak.leftHalfZ;
        peakInfo.peakRightHalfZ = chosenPeak.rightHalfZ;
        peakInfo.idxFinePeak = idxFinePeakOriginal;
        peakInfo.targetValue = targetValue;
        peakInfo.zLeftDrop = cr.zCross;
        peakInfo.idxFineLeftDropClosest = idxFineLeftOriginal;
        peakInfo.idxSampledPeak = -1;
        peakInfo.zSampledPeakClosest = Double.NaN;
        peakInfo.idxSampledLeftDropClosest = -1;
        peakInfo.zSampledLeftDropClosest = Double.NaN;
        peakInfo.idxFineLeftDropClosest_validCurve = cr.idxFineClosest;

        return peakInfo;
    }

    private static class ChooseResult {
        double zBest;
        FinalDecision decision;
    }

    private static ChooseResult chooseClosestPair(double[] zVals, String[] metricNames) {
        boolean[] validMask = new boolean[zVals.length];
        boolean[] usedMask = new boolean[zVals.length];
        int nValid = 0;
        for (int i = 0; i < zVals.length; i++) {
            validMask[i] = Double.isFinite(zVals[i]);
            if (validMask[i]) nValid++;
        }

        double zBest = Double.NaN;
        String caseUsed;
        String note;

        if (nValid >= 3) {
            int[] validIdx = new int[nValid];
            int q = 0;
            for (int i = 0; i < validMask.length; i++) if (validMask[i]) validIdx[q++] = i;

            double minDist = Double.POSITIVE_INFINITY;
            int bestA = validIdx[0];
            int bestB = validIdx[1];

            for (int i = 0; i < validIdx.length; i++) {
                for (int j = i + 1; j < validIdx.length; j++) {
                    double d = Math.abs(zVals[validIdx[i]] - zVals[validIdx[j]]);
                    if (d < minDist) {
                        minDist = d;
                        bestA = validIdx[i];
                        bestB = validIdx[j];
                    }
                }
            }

            usedMask[bestA] = true;
            usedMask[bestB] = true;
            zBest = 0.5 * (zVals[bestA] + zVals[bestB]);

            caseUsed = "closest_pair_from_three_metrics";
            note = String.format("Used closest pair: %s and %s. Pair distance = %.4f um.",
                    metricNames[bestA], metricNames[bestB], minDist);

        } else if (nValid == 2) {
            double sum = 0;
            int[] idx = new int[2];
            int q = 0;
            for (int i = 0; i < zVals.length; i++) {
                if (validMask[i]) {
                    usedMask[i] = true;
                    sum += zVals[i];
                    idx[q++] = i;
                }
            }
            zBest = sum / 2.0;
            caseUsed = "two_valid_metrics_average";
            note = String.format("Only two metrics were valid: %s and %s. Used their average.",
                    metricNames[idx[0]], metricNames[idx[1]]);

        } else if (nValid == 1) {
            int idx = -1;
            for (int i = 0; i < zVals.length; i++) {
                if (validMask[i]) {
                    idx = i;
                    break;
                }
            }
            usedMask[idx] = true;
            zBest = zVals[idx];
            caseUsed = "single_valid_metric_fallback";
            note = String.format("Only one metric was valid: %s. Returned that value.", metricNames[idx]);
            System.err.println("Warning: " + note);

        } else {
            caseUsed = "no_valid_metric";
            note = "No metric produced a valid broad-peak left-drop z estimate.";
            System.err.println("Warning: " + note);
        }

        FinalDecision decision = new FinalDecision();
        decision.caseUsed = caseUsed;
        decision.note = note;
        decision.zVals = zVals.clone();
        decision.validMask = validMask;
        decision.usedMask = usedMask;
        decision.metricNames = metricNames.clone();
        decision.z_best_focus = zBest;

        ChooseResult out = new ChooseResult();
        out.zBest = zBest;
        out.decision = decision;
        return out;
    }

    private static CrossingResult findLeftTargetCrossingDense(
            double[] z,
            double[] y,
            double zPeak,
            double targetValue,
            int nDense
    ) {
        CrossingResult out = new CrossingResult();

        if (!Double.isFinite(zPeak) || !Double.isFinite(targetValue)) {
            out.message = "Invalid peak z or target value.";
            return out;
        }

        int leftCount = 0;
        for (double v : z) if (v <= zPeak) leftCount++;

        if (leftCount < 2) {
            out.message = "Not enough curve points on the left side of the peak.";
            return out;
        }

        double[] zLeft = new double[leftCount];
        double[] yLeft = new double[leftCount];
        int q = 0;
        for (int i = 0; i < z.length; i++) {
            if (z[i] <= zPeak) {
                zLeft[q] = z[i];
                yLeft[q] = y[i];
                q++;
            }
        }

        if (zLeft[zLeft.length - 1] <= zLeft[0]) {
            out.message = "Invalid left-side z range.";
            return out;
        }

        int nDenseHere = Math.max(1000, Math.min(nDense, 200000));
        double[] zDense = linspace(zLeft[0], zLeft[zLeft.length - 1], nDenseHere);
        double[] yDense = pchipInterpolate(zLeft, yLeft, zDense);

        ArrayList<Double> zv = new ArrayList<>();
        ArrayList<Double> yv = new ArrayList<>();
        for (int i = 0; i < zDense.length; i++) {
            if (Double.isFinite(yDense[i])) {
                zv.add(zDense[i]);
                yv.add(yDense[i]);
            }
        }

        if (zv.size() < 2) {
            out.message = "Dense left-side curve evaluation failed.";
            return out;
        }

        zDense = toDoubleArray(zv);
        yDense = toDoubleArray(yv);

        double[] f = new double[yDense.length];
        for (int i = 0; i < yDense.length; i++) f[i] = yDense[i] - targetValue;

        int exactIdx = -1;
        for (int i = 0; i < f.length; i++) {
            if (Math.abs(f[i]) < 1e-10) exactIdx = i;
        }

        if (exactIdx >= 0) {
            out.zCross = zDense[exactIdx];
            out.idxFineClosest = argMinAbs(z, out.zCross) + 1;
            out.message = "Found exact target hit on left side of peak.";
            return out;
        }

        int crossIdx = -1;
        for (int i = 0; i < f.length - 1; i++) {
            if (f[i] * f[i + 1] < 0) crossIdx = i;
        }

        if (crossIdx >= 0) {
            int k = crossIdx;
            double z1 = zDense[k];
            double z2 = zDense[k + 1];
            double f1 = f[k];
            double f2 = f[k + 1];

            out.zCross = z1 - f1 * (z2 - z1) / (f2 - f1);
            out.idxFineClosest = argMinAbs(z, out.zCross) + 1;
            out.message = "Found interpolated target crossing on left side of peak.";
            return out;
        }

        boolean allGreater = true;
        boolean allLess = true;
        for (double v : yDense) {
            if (!(v > targetValue)) allGreater = false;
            if (!(v < targetValue)) allLess = false;
        }

        if (allGreater) {
            out.message = "The left side of the peak never drops below 0.90*peak within the sampled z range.";
        } else if (allLess) {
            out.message = "The left side of the curve never reaches 0.90*peak before the detected peak.";
        } else {
            out.message = "No valid left-side target crossing could be determined.";
        }

        return out;
    }

    private static ExtremaResult customExtremaIndices(double[] zInput, double[] yInput) {
        boolean[] valid = finiteMask(zInput, yInput);
        double[] z = filterByMask(zInput, valid);
        double[] y = filterByMask(yInput, valid);

        ExtremaResult out = new ExtremaResult();
        if (z.length < 3) {
            out.maxIdx = new int[0];
            out.minIdx = new int[0];
            out.maxWidths = new double[0];
            out.minWidths = new double[0];
            return out;
        }

        double[] dy = gradient(y, z);
        double[] signDy = new double[dy.length];
        for (int i = 0; i < dy.length; i++) signDy[i] = Math.signum(dy[i]);

        for (int i = 1; i < signDy.length; i++) {
            if (signDy[i] == 0) signDy[i] = signDy[i - 1];
        }

        for (int i = signDy.length - 2; i >= 0; i--) {
            if (signDy[i] == 0) signDy[i] = signDy[i + 1];
        }

        List<Integer> maxIdxList = new ArrayList<>();
        List<Integer> minIdxList = new ArrayList<>();

        for (int i = 0; i < signDy.length - 1; i++) {
            double signChange = signDy[i + 1] - signDy[i];
            if (signChange < 0) maxIdxList.add(i + 1);
            if (signChange > 0) minIdxList.add(i + 1);
        }

        if (y[0] > y[1]) maxIdxList.add(0, 0);
        if (y[y.length - 1] > y[y.length - 2]) maxIdxList.add(y.length - 1);
        if (y[0] < y[1]) minIdxList.add(0, 0);
        if (y[y.length - 1] < y[y.length - 2]) minIdxList.add(y.length - 1);

        maxIdxList = uniqueStable(maxIdxList);
        minIdxList = uniqueStable(minIdxList);

        out.maxIdx = toIntArray(maxIdxList);
        out.minIdx = toIntArray(minIdxList);
        out.maxWidths = new double[out.maxIdx.length];
        out.minWidths = new double[out.minIdx.length];

        for (int i = 0; i < out.maxIdx.length; i++) {
            out.maxWidths[i] = estimateFWHM(z, y, out.maxIdx[i], "max").w;
        }

        for (int i = 0; i < out.minIdx.length; i++) {
            out.minWidths[i] = estimateFWHM(z, y, out.minIdx[i], "min").w;
        }

        return out;
    }

    private static FwhmResult estimateFWHM(double[] z, double[] y, int idx, String type) {
        int i0 = Math.max(0, Math.min(idx, z.length - 1));
        double y0 = y[i0];

        double halfLevel;
        int left = i0;
        int right = i0;

        if (type.equalsIgnoreCase("max")) {
            double baseline = min(y);
            halfLevel = baseline + 0.5 * (y0 - baseline);

            while (left > 1 && y[left] > halfLevel) left--;
            while (right < y.length - 1 && y[right] > halfLevel) right++;
        } else {
            double baseline = max(y);
            halfLevel = baseline - 0.5 * (baseline - y0);

            while (left > 1 && y[left] < halfLevel) left--;
            while (right < y.length - 1 && y[right] < halfLevel) right++;
        }

        left = Math.max(0, Math.min(left, z.length - 1));
        right = Math.max(0, Math.min(right, z.length - 1));

        FwhmResult out = new FwhmResult();
        out.leftHalfZ = z[left];
        out.rightHalfZ = z[right];
        out.w = Math.abs(out.rightHalfZ - out.leftHalfZ);
        out.halfLevel = halfLevel;
        return out;
    }

    private static FitResult localSmoothingSplineFit(double[] xInput, double[] yInput, double p) {
        boolean[] valid = finiteMask(xInput, yInput);
        double[] x = filterByMask(xInput, valid);
        double[] y = filterByMask(yInput, valid);

        if (x.length < 2) {
            throw new IllegalArgumentException("localSmoothingSplineFit requires at least 2 valid points.");
        }

        sortXYByX(x, y);
        UniqueXY unique = uniqueXAverageY(x, y);
        x = unique.x;
        y = unique.y;

        int n = x.length;
        SplineModel model = new SplineModel();

        if (n == 2) {
            double slope = (y[1] - y[0]) / (x[1] - x[0]);
            double intercept = y[0] - slope * x[0];
            model.kind = "line_two_point";
            model.coeff = new double[]{slope, intercept};
            model.x = x;
            model.y = y;
            FitResult out = new FitResult();
            out.model = model;
            return out;
        }

        p = Math.max(0, Math.min(1, p));

        if (p <= 0) {
            double[] coeff = polyfitLine(x, y);
            model.kind = "least_squares_line";
            model.coeff = coeff;
            model.x = x;
            model.y = y;
            model.p = p;
            FitResult out = new FitResult();
            out.model = model;
            return out;
        }

        if (p >= 1) {
            PP pp = naturalCubicSplinePP(x, y);
            model.kind = "natural_cubic_interpolant";
            model.pp = pp;
            model.x = x;
            model.y = y;
            model.yfit = y.clone();
            model.p = p;
            FitResult out = new FitResult();
            out.model = model;
            return out;
        }

        double[] h = diff(x);
        for (double v : h) {
            if (v <= 0) throw new IllegalArgumentException("x must be strictly increasing after duplicate removal.");
        }

        double[][] Q = new double[n][n - 2];
        for (int k = 0; k < n - 2; k++) {
            Q[k][k] = 1.0 / h[k];
            Q[k + 1][k] = -1.0 / h[k] - 1.0 / h[k + 1];
            Q[k + 2][k] = 1.0 / h[k + 1];
        }

        double[] mainDiag = new double[n - 2];
        for (int i = 0; i < n - 2; i++) {
            mainDiag[i] = (h[i] + h[i + 1]) / 3.0;
        }

        double[][] R = new double[n - 2][n - 2];
        for (int i = 0; i < n - 2; i++) R[i][i] = mainDiag[i];
        for (int i = 0; i < n - 3; i++) {
            double off = h[i + 1] / 6.0;
            R[i][i + 1] = off;
            R[i + 1][i] = off;
        }

        double[][] RinvtQT = solveMatrixMultiple(R, transpose(Q));
        double[][] K = multiply(Q, RinvtQT);

        double[][] A = new double[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                A[r][c] = (1 - p) * K[r][c];
            }
            A[r][r] += p;
            A[r][r] += 1e-12;
        }

        double[] b = new double[n];
        for (int i = 0; i < n; i++) b[i] = p * y[i];

        double[] yfit = solveLinearSystem(A, b);
        PP pp = naturalCubicSplinePP(x, yfit);

        model.kind = "local_penalized_natural_cubic_smoothing_spline";
        model.pp = pp;
        model.x = x;
        model.y = y;
        model.yfit = yfit;
        model.p = p;
        model.h = h;
        model.Q = Q;
        model.R = R;

        FitResult out = new FitResult();
        out.model = model;
        return out;
    }

    private static PP naturalCubicSplinePP(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) throw new IllegalArgumentException("naturalCubicSplinePP requires at least two points.");

        PP pp = new PP();
        pp.breaks = x.clone();

        if (n == 2) {
            double h = x[1] - x[0];
            double slope = (y[1] - y[0]) / h;
            pp.coefs = new double[][]{{0, 0, slope, y[0]}};
            pp.pieces = 1;
            return pp;
        }

        double[] h = diff(x);
        for (double v : h) {
            if (v <= 0) throw new IllegalArgumentException("x must be strictly increasing.");
        }

        double[][] A = new double[n][n];
        double[] rhs = new double[n];

        A[0][0] = 1;
        A[n - 1][n - 1] = 1;

        for (int i = 1; i < n - 1; i++) {
            A[i][i - 1] = h[i - 1];
            A[i][i] = 2 * (h[i - 1] + h[i]);
            A[i][i + 1] = h[i];
            rhs[i] = 6 * ((y[i + 1] - y[i]) / h[i] - (y[i] - y[i - 1]) / h[i - 1]);
        }

        double[] m = solveLinearSystem(A, rhs);
        double[][] coefs = new double[n - 1][4];

        for (int i = 0; i < n - 1; i++) {
            double hi = h[i];
            double a = (m[i + 1] - m[i]) / (6 * hi);
            double b = m[i] / 2;
            double c = (y[i + 1] - y[i]) / hi - hi * (2 * m[i] + m[i + 1]) / 6;
            double d = y[i];
            coefs[i][0] = a;
            coefs[i][1] = b;
            coefs[i][2] = c;
            coefs[i][3] = d;
        }

        pp.coefs = coefs;
        pp.pieces = n - 1;
        return pp;
    }

    private static double[] ppvalLocal(PP pp, double[] xx) {
        double[] out = new double[xx.length];
        double[] breaks = pp.breaks;
        double[][] coefs = pp.coefs;
        int pieces = pp.pieces;

        for (int i = 0; i < xx.length; i++) {
            double xval = xx[i];
            int bin;
            if (pieces == 1) {
                bin = 0;
            } else {
                bin = pieces - 1;
                for (int k = 0; k < pieces - 1; k++) {
                    if (xval < breaks[k + 1]) {
                        bin = k;
                        break;
                    }
                }
                if (bin < 0) bin = 0;
                if (bin > pieces - 1) bin = pieces - 1;
            }
            double dx = xval - breaks[bin];
            double[] c = coefs[bin];
            out[i] = ((c[0] * dx + c[1]) * dx + c[2]) * dx + c[3];
        }
        return out;
    }

    private static double[] ppvalOrPoly(SplineModel model, double[] xx) {
        if (model.pp != null) {
            return ppvalLocal(model.pp, xx);
        }
        double[] out = new double[xx.length];
        double a = model.coeff[0];
        double b = model.coeff[1];
        for (int i = 0; i < xx.length; i++) out[i] = a * xx[i] + b;
        return out;
    }

    private static double[] pchipInterpolate(double[] x, double[] y, double[] xq) {
        int n = x.length;
        double[] out = new double[xq.length];

        if (n == 2) {
            for (int i = 0; i < xq.length; i++) {
                out[i] = linearInterpolateTwoPoints(xq[i], x[0], y[0], x[1], y[1]);
            }
            return out;
        }

        double[] h = new double[n - 1];
        double[] delta = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
            delta[i] = (y[i + 1] - y[i]) / h[i];
        }

        double[] d = new double[n];

        for (int k = 1; k <= n - 2; k++) {
            if (delta[k - 1] == 0 || delta[k] == 0 || Math.signum(delta[k - 1]) != Math.signum(delta[k])) {
                d[k] = 0;
            } else {
                double w1 = 2 * h[k] + h[k - 1];
                double w2 = h[k] + 2 * h[k - 1];
                d[k] = (w1 + w2) / (w1 / delta[k - 1] + w2 / delta[k]);
            }
        }

        d[0] = pchipEndSlope(h[0], h[1], delta[0], delta[1]);
        d[n - 1] = pchipEndSlope(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3]);

        for (int qi = 0; qi < xq.length; qi++) {
            double xx = xq[qi];
            int k;
            if (xx <= x[0]) {
                k = 0;
            } else if (xx >= x[n - 1]) {
                k = n - 2;
            } else {
                k = Arrays.binarySearch(x, xx);
                if (k >= 0) {
                    if (k == n - 1) k = n - 2;
                } else {
                    k = -k - 2;
                    if (k < 0) k = 0;
                    if (k > n - 2) k = n - 2;
                }
            }

            double hk = h[k];
            double t = (xx - x[k]) / hk;
            double h00 = (2 * t * t * t - 3 * t * t + 1);
            double h10 = (t * t * t - 2 * t * t + t);
            double h01 = (-2 * t * t * t + 3 * t * t);
            double h11 = (t * t * t - t * t);
            out[qi] = h00 * y[k] + h10 * hk * d[k] + h01 * y[k + 1] + h11 * hk * d[k + 1];
        }

        return out;
    }

    private static double pchipEndSlope(double h1, double h2, double del1, double del2) {
        double d = ((2 * h1 + h2) * del1 - h1 * del2) / (h1 + h2);
        if (Math.signum(d) != Math.signum(del1)) {
            d = 0;
        } else if (Math.signum(del1) != Math.signum(del2) && Math.abs(d) > Math.abs(3 * del1)) {
            d = 3 * del1;
        }
        return d;
    }

    private static PerMetric emptyPerMetricStruct() {
        return new PerMetric();
    }

    private static ClosestResult findClosestIndexAndZ(double[] zVector, double zValue) {
        int idx0 = argMinAbs(zVector, zValue);
        ClosestResult out = new ClosestResult();
        out.idx = idx0 + 1;
        out.zClosest = zVector[idx0];
        return out;
    }

    private static void copyPerMetricFields(PerMetric src, PerMetric dst) {
        dst.status = src.status;
        dst.message = src.message;
        dst.zPeak = src.zPeak;
        dst.peakValue = src.peakValue;
        dst.peakFWHM_um = src.peakFWHM_um;
        dst.peakHalfLevel = src.peakHalfLevel;
        dst.peakLeftHalfZ = src.peakLeftHalfZ;
        dst.peakRightHalfZ = src.peakRightHalfZ;
        dst.idxFinePeak = src.idxFinePeak;
        dst.idxSampledPeak = src.idxSampledPeak;
        dst.zSampledPeakClosest = src.zSampledPeakClosest;
        dst.targetValue = src.targetValue;
        dst.zLeftDrop = src.zLeftDrop;
        dst.idxFineLeftDropClosest = src.idxFineLeftDropClosest;
        dst.idxFineLeftDropClosest_validCurve = src.idxFineLeftDropClosest_validCurve;
        dst.idxSampledLeftDropClosest = src.idxSampledLeftDropClosest;
        dst.zSampledLeftDropClosest = src.zSampledLeftDropClosest;
        dst.usedInFinalPair = src.usedInFinalPair;
        dst.allPeakCandidates = src.allPeakCandidates;
    }

    private static void showPlotsIfPossible(
            double[] zSampled,
            double[][] metricValuesNorm,
            double[] zFine,
            double[][] smoothCurvesNorm,
            double[][] cleanCurvesNorm,
            List<PerMetric> perMetric,
            double z_ini,
            double z_best_focus,
            Settings settings
    ) {
        if (GraphicsEnvironment.isHeadless()) return;
        try {
            JFrame frame = new JFrame("Best focus metrics");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new PlotPanel(zSampled, metricValuesNorm, zFine, smoothCurvesNorm,
                    cleanCurvesNorm, perMetric, z_ini, z_best_focus, settings));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        } catch (Exception ignored) {
        }
    }

    private static class PlotPanel extends JPanel {
        final double[] zSampled;
        final double[][] metricValuesNorm;
        final double[] zFine;
        final double[][] smoothCurvesNorm;
        final double[][] cleanCurvesNorm;
        final List<PerMetric> perMetric;
        final double z_ini;
        final double z_best_focus;
        final Settings settings;

        PlotPanel(double[] zSampled, double[][] metricValuesNorm, double[] zFine,
                  double[][] smoothCurvesNorm, double[][] cleanCurvesNorm,
                  List<PerMetric> perMetric, double z_ini, double z_best_focus, Settings settings) {
            this.zSampled = zSampled;
            this.metricValuesNorm = metricValuesNorm;
            this.zFine = zFine;
            this.smoothCurvesNorm = smoothCurvesNorm;
            this.cleanCurvesNorm = cleanCurvesNorm;
            this.perMetric = perMetric;
            this.z_ini = z_ini;
            this.z_best_focus = z_best_focus;
            this.settings = settings;
            setPreferredSize(new Dimension(1450, 850));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int margin = 55;
            int gap = 30;
            int plotW = (w - 2 * margin - 2 * gap) / 3;
            int plotH = h - 2 * margin;

            for (int m = 0; m < 3; m++) {
                int x0 = margin + m * (plotW + gap);
                int y0 = margin;
                drawOne(g2, m, x0, y0, plotW, plotH);
            }
        }

        private void drawOne(Graphics2D g2, int m, int x0, int y0, int plotW, int plotH) {
            double xmin = min(zFine);
            double xmax = max(zFine);
            double ymin = -0.05;
            double ymax = 1.05;

            g2.setColor(Color.WHITE);
            g2.fillRect(x0, y0, plotW, plotH);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRect(x0, y0, plotW, plotH);

            g2.setColor(new Color(220, 220, 220));
            for (int i = 1; i < 5; i++) {
                int xx = x0 + i * plotW / 5;
                int yy = y0 + i * plotH / 5;
                g2.drawLine(xx, y0, xx, y0 + plotH);
                g2.drawLine(x0, yy, x0 + plotW, yy);
            }

            drawVerticalLine(g2, z_ini, xmin, xmax, ymin, ymax, x0, y0, plotW, plotH, new Color(100, 100, 100), true, 1.2f);

            drawLine(g2, zFine, getColumn(smoothCurvesNorm, m), xmin, xmax, ymin, ymax, x0, y0, plotW, plotH, Color.BLUE, 1.4f);
            if (settings.showCleanedCurve) {
                drawLine(g2, zFine, getColumn(cleanCurvesNorm, m), xmin, xmax, ymin, ymax, x0, y0, plotW, plotH, Color.RED, 1.8f);
            }

            g2.setColor(Color.BLACK);
            double[] ySample = getColumn(metricValuesNorm, m);
            for (int i = 0; i < zSampled.length; i++) {
                if (!Double.isFinite(ySample[i])) continue;
                int px = mapX(zSampled[i], xmin, xmax, x0, plotW);
                int py = mapY(ySample[i], ymin, ymax, y0, plotH);
                g2.fillOval(px - 3, py - 3, 6, 6);
            }

            PerMetric pm = perMetric.get(m);
            if (pm.status.equalsIgnoreCase("used")) {
                drawVerticalLine(g2, pm.zLeftDrop, xmin, xmax, ymin, ymax, x0, y0, plotW, plotH, Color.MAGENTA, true, 1.2f);
                drawHorizontalLine(g2, pm.targetValue, xmin, xmax, ymin, ymax, x0, y0, plotW, plotH, Color.MAGENTA, true, 1.2f);
                int px = mapX(pm.zPeak, xmin, xmax, x0, plotW);
                int py = mapY(pm.peakValue, ymin, ymax, y0, plotH);
                g2.setColor(Color.RED);
                int[] xs = {px, px - 6, px + 6};
                int[] ys = {py - 8, py + 6, py + 6};
                g2.fillPolygon(xs, ys, 3);
            }

            if (settings.showFinalBestFocusLineOnAllPlots && Double.isFinite(z_best_focus)) {
                drawVerticalLine(g2, z_best_focus, xmin, xmax, ymin, ymax, x0, y0, plotW, plotH, Color.BLACK, false, 1.6f);
            }
        }

        private void drawLine(Graphics2D g2, double[] xs, double[] ys, double xmin, double xmax, double ymin, double ymax,
                              int x0, int y0, int plotW, int plotH, Color color, float stroke) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(stroke));
            int lastX = 0, lastY = 0;
            boolean hasLast = false;
            for (int i = 0; i < xs.length; i++) {
                if (!Double.isFinite(xs[i]) || !Double.isFinite(ys[i])) {
                    hasLast = false;
                    continue;
                }
                int px = mapX(xs[i], xmin, xmax, x0, plotW);
                int py = mapY(ys[i], ymin, ymax, y0, plotH);
                if (hasLast) g2.drawLine(lastX, lastY, px, py);
                lastX = px;
                lastY = py;
                hasLast = true;
            }
        }

        private void drawVerticalLine(Graphics2D g2, double x, double xmin, double xmax, double ymin, double ymax,
                                      int x0, int y0, int plotW, int plotH, Color color, boolean dashed, float stroke) {
            int px = mapX(x, xmin, xmax, x0, plotW);
            g2.setColor(color);
            g2.setStroke(dashed ? new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{8, 5}, 0) : new BasicStroke(stroke));
            g2.drawLine(px, y0, px, y0 + plotH);
        }

        private void drawHorizontalLine(Graphics2D g2, double y, double xmin, double xmax, double ymin, double ymax,
                                        int x0, int y0, int plotW, int plotH, Color color, boolean dashed, float stroke) {
            int py = mapY(y, ymin, ymax, y0, plotH);
            g2.setColor(color);
            g2.setStroke(dashed ? new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3, 5}, 0) : new BasicStroke(stroke));
            g2.drawLine(x0, py, x0 + plotW, py);
        }

        private int mapX(double x, double xmin, double xmax, int x0, int plotW) {
            return x0 + (int) Math.round((x - xmin) / (xmax - xmin) * plotW);
        }

        private int mapY(double y, double ymin, double ymax, int y0, int plotH) {
            return y0 + plotH - (int) Math.round((y - ymin) / (ymax - ymin) * plotH);
        }
    }

    private static double[][] copyImage(double[][] I) {
        double[][] out = new double[I.length][];
        for (int r = 0; r < I.length; r++) out[r] = I[r].clone();
        return out;
    }

    private static double[][] fillMatrix(int rows, int cols, double value) {
        double[][] out = new double[rows][cols];
        for (int r = 0; r < rows; r++) Arrays.fill(out[r], value);
        return out;
    }

    private static double[] fillArray(int n, double value) {
        double[] out = new double[n];
        Arrays.fill(out, value);
        return out;
    }

    private static double[] linspace(double a, double b, int n) {
        double[] out = new double[n];
        if (n == 1) {
            out[0] = a;
            return out;
        }
        double step = (b - a) / (n - 1);
        for (int i = 0; i < n; i++) out[i] = a + i * step;
        out[n - 1] = b;
        return out;
    }

    private static double[] diff(double[] x) {
        double[] d = new double[x.length - 1];
        for (int i = 0; i < d.length; i++) d[i] = x[i + 1] - x[i];
        return d;
    }

    private static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    private static double min(double[] x) {
        double m = Double.POSITIVE_INFINITY;
        for (double v : x) if (Double.isFinite(v) && v < m) m = v;
        return m;
    }

    private static double max(double[] x) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : x) if (Double.isFinite(v) && v > m) m = v;
        return m;
    }

    private static int argMinAbs(double[] x, double value) {
        int best = 0;
        double dBest = Math.abs(x[0] - value);
        for (int i = 1; i < x.length; i++) {
            double d = Math.abs(x[i] - value);
            if (d < dBest) {
                dBest = d;
                best = i;
            }
        }
        return best;
    }

    private static double[] getColumn(double[][] M, int col) {
        double[] out = new double[M.length];
        for (int i = 0; i < M.length; i++) out[i] = M[i][col];
        return out;
    }

    private static void setColumn(double[][] M, int col, double[] values) {
        for (int i = 0; i < M.length; i++) M[i][col] = values[i];
    }

    private static boolean[] finiteMask(double[] a, double[] b) {
        boolean[] out = new boolean[a.length];
        for (int i = 0; i < a.length; i++) out[i] = Double.isFinite(a[i]) && Double.isFinite(b[i]);
        return out;
    }

    private static int countTrue(boolean[] mask) {
        int n = 0;
        for (boolean v : mask) if (v) n++;
        return n;
    }

    private static double[] filterByMask(double[] x, boolean[] mask) {
        double[] out = new double[countTrue(mask)];
        int q = 0;
        for (int i = 0; i < x.length; i++) if (mask[i]) out[q++] = x[i];
        return out;
    }

    private static boolean allNotFinite(double[] x) {
        for (double v : x) if (Double.isFinite(v)) return false;
        return true;
    }

    private static double linearInterpolateTwoPoints(double x, double x1, double y1, double x2, double y2) {
        if (x2 == x1) return y1;
        return y1 + (x - x1) * (y2 - y1) / (x2 - x1);
    }

    private static double[] gradient(double[] y, double[] x) {
        int n = y.length;
        double[] g = new double[n];
        if (n == 1) {
            g[0] = 0;
            return g;
        }
        g[0] = (y[1] - y[0]) / (x[1] - x[0]);
        g[n - 1] = (y[n - 1] - y[n - 2]) / (x[n - 1] - x[n - 2]);
        for (int i = 1; i < n - 1; i++) {
            g[i] = (y[i + 1] - y[i - 1]) / (x[i + 1] - x[i - 1]);
        }
        return g;
    }

    private static ArrayList<Integer> uniqueStable(List<Integer> input) {
        ArrayList<Integer> out = new ArrayList<>();
        for (Integer v : input) if (!out.contains(v)) out.add(v);
        return out;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    private static double[] toDoubleArray(List<Double> list) {
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    private static void sortXYByX(double[] x, double[] y) {
        Integer[] order = new Integer[x.length];
        for (int i = 0; i < x.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(x[a], x[b]));
        double[] xs = x.clone();
        double[] ys = y.clone();
        for (int i = 0; i < order.length; i++) {
            x[i] = xs[order[i]];
            y[i] = ys[order[i]];
        }
    }

    private static class UniqueXY {
        double[] x;
        double[] y;
    }

    private static UniqueXY uniqueXAverageY(double[] x, double[] y) {
        ArrayList<Double> xu = new ArrayList<>();
        ArrayList<Double> yu = new ArrayList<>();
        int i = 0;
        while (i < x.length) {
            double currentX = x[i];
            double sum = 0;
            int count = 0;
            while (i < x.length && x[i] == currentX) {
                sum += y[i];
                count++;
                i++;
            }
            xu.add(currentX);
            yu.add(sum / count);
        }
        UniqueXY out = new UniqueXY();
        out.x = toDoubleArray(xu);
        out.y = toDoubleArray(yu);
        return out;
    }

    private static double[] polyfitLine(double[] x, double[] y) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            sx += x[i];
            sy += y[i];
            sxx += x[i] * x[i];
            sxy += x[i] * y[i];
        }
        double denom = n * sxx - sx * sx;
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{slope, intercept};
    }

    private static double[][] transpose(double[][] A) {
        int rows = A.length;
        int cols = A[0].length;
        double[][] T = new double[cols][rows];
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) T[c][r] = A[r][c];
        return T;
    }

    private static double[][] multiply(double[][] A, double[][] B) {
        int rows = A.length;
        int inner = A[0].length;
        int cols = B[0].length;
        double[][] C = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int k = 0; k < inner; k++) {
                double aik = A[i][k];
                for (int j = 0; j < cols; j++) {
                    C[i][j] += aik * B[k][j];
                }
            }
        }
        return C;
    }

    private static double[][] solveMatrixMultiple(double[][] A, double[][] B) {
        double[][] out = new double[B.length][B[0].length];
        for (int col = 0; col < B[0].length; col++) {
            double[] b = new double[B.length];
            for (int r = 0; r < B.length; r++) b[r] = B[r][col];
            double[] x = solveLinearSystem(A, b);
            for (int r = 0; r < x.length; r++) out[r][col] = x[r];
        }
        return out;
    }

    private static double[] solveLinearSystem(double[][] Ainput, double[] binput) {
        int n = binput.length;
        double[][] A = new double[n][n];
        double[] b = binput.clone();
        for (int i = 0; i < n; i++) A[i] = Ainput[i].clone();

        for (int k = 0; k < n; k++) {
            int pivot = k;
            double maxAbs = Math.abs(A[k][k]);
            for (int i = k + 1; i < n; i++) {
                double v = Math.abs(A[i][k]);
                if (v > maxAbs) {
                    maxAbs = v;
                    pivot = i;
                }
            }
            if (maxAbs < 1e-15) {
                throw new IllegalArgumentException("Singular or nearly singular matrix in linear solve.");
            }
            if (pivot != k) {
                double[] tmp = A[k]; A[k] = A[pivot]; A[pivot] = tmp;
                double tb = b[k]; b[k] = b[pivot]; b[pivot] = tb;
            }

            for (int i = k + 1; i < n; i++) {
                double factor = A[i][k] / A[k][k];
                A[i][k] = 0;
                for (int j = k + 1; j < n; j++) {
                    A[i][j] -= factor * A[k][j];
                }
                b[i] -= factor * b[k];
            }
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = b[i];
            for (int j = i + 1; j < n; j++) sum -= A[i][j] * x[j];
            x[i] = sum / A[i][i];
        }
        return x;
    }
}
