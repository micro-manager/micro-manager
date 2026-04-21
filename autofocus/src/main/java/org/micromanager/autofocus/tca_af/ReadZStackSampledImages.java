package org.micromanager.autofocus.tca_af;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReadZStackSampledImages {
    private static final double Z_RANGE_UM = 20.0;
    private static final String[] SUPPORTED_EXTENSIONS = {".tif", ".tiff", ".png", ".jpg", ".jpeg", ".bmp"};
    private static final Pattern NUMERIC_TOKEN_PATTERN = Pattern.compile("\\d+");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{8}");

    public static class SampleInfo {
        public String folderPath;
        public String date;
        public String FOV;
        public String channel;

        public double zStart;
        public double zEnd;
        public double z_ini;
        public double z_range_um;

        public int nImages;
        public List<Path> files;
        public double[] zAxisAll;
        public double dzOriginal;

        public double zMinReq;
        public double zMaxReq;
        public int[] idxWindow;

        public double deltaz_samp;
        public int skipFactor;
        public int[] idxSampled;
        public double[] zSampled;
    }

    public static class ReadResult {
        public final List<ImageProcessor> imageArray;
        public final SampleInfo sampleInfo;

        public ReadResult(List<ImageProcessor> imageArray, SampleInfo sampleInfo) {
            this.imageArray = imageArray;
            this.sampleInfo = sampleInfo;
        }
    }

    public static ReadResult readZStackSampledImages(String folderPath,
                                                    double zStart,
                                                    double zEnd,
                                                    double z_ini,
                                                    double deltaz_samp) throws IOException {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("folderPath must be a non-empty string.");
        }
        if (deltaz_samp <= 0) {
            throw new IllegalArgumentException("deltaz_samp must be positive.");
        }

        Path folder = Paths.get(folderPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Folder does not exist: " + folderPath);
        }

        List<Path> files = Files.list(folder)
                .filter(Files::isRegularFile)
                .filter(ReadZStackSampledImages::isSupportedImageFile)
                .sorted(Comparator.comparingLong(ReadZStackSampledImages::numericTokenOrFallback))
                .collect(Collectors.toList());

        if (files.isEmpty()) {
            throw new IllegalArgumentException("No supported image files found in: " + folderPath);
        }

        int nImages = files.size();
        if (nImages < 3) {
            throw new IllegalArgumentException("Not enough images in the folder.");
        }

        double[] zAxisAll = linspace(zStart, zEnd, nImages);
        double dzOriginal = mean(diff(zAxisAll));

        double zMinReq = z_ini - Z_RANGE_UM;
        double zMaxReq = z_ini + Z_RANGE_UM;
        List<Integer> idxWindowList = new ArrayList<>();
        for (int i = 0; i < zAxisAll.length; i++) {
            if (zAxisAll[i] >= zMinReq && zAxisAll[i] <= zMaxReq) {
                idxWindowList.add(i);
            }
        }

        if (idxWindowList.size() < 2) {
            throw new IllegalArgumentException(String.format(
                    "Too few images in requested range [%.4f, %.4f] um.", zMinReq, zMaxReq));
        }

        int skipFactor = Math.max(1, (int) Math.round(deltaz_samp / Math.abs(dzOriginal)));

        List<Integer> idxSampledList = new ArrayList<>();
        int startIdx = idxWindowList.get(0);
        int endIdx = idxWindowList.get(idxWindowList.size() - 1);
        for (int i = startIdx; i <= endIdx; i += skipFactor) {
            idxSampledList.add(i);
        }
        if (idxSampledList.isEmpty() || idxSampledList.get(idxSampledList.size() - 1) != endIdx) {
            idxSampledList.add(endIdx);
        }

        int[] idxWindow = idxWindowList.stream().mapToInt(Integer::intValue).toArray();
        int[] idxSampled = idxSampledList.stream().distinct().mapToInt(Integer::intValue).toArray();
        double[] zSampled = new double[idxSampled.length];
        for (int i = 0; i < idxSampled.length; i++) {
            zSampled[i] = zAxisAll[idxSampled[i]];
        }

        List<ImageProcessor> imageArray = new ArrayList<>(idxSampled.length);
        for (int idx : idxSampled) {
            Path imagePath = files.get(idx);
            System.out.println("Loading image: " + imagePath);
            ImagePlus imp = IJ.openImage(imagePath.toString());
            if (imp == null) {
                throw new IOException("Failed to open image: " + imagePath);
            }
            ImageProcessor ip = imp.getProcessor();
            if (ip == null) {
                throw new IOException("Failed to get image processor for: " + imagePath);
            }
            if (ip instanceof ColorProcessor) {
                ip = ip.convertToByte(true);
            }
            imageArray.add(ip);
        }

        SampleInfo sampleInfo = new SampleInfo();
        sampleInfo.folderPath = folderPath;
        FolderMetadata folderMetadata = parseFolderInfo(folder);
        sampleInfo.date = folderMetadata.acqDate;
        sampleInfo.FOV = folderMetadata.fovName;
        sampleInfo.channel = folderMetadata.channelName;

        sampleInfo.zStart = zStart;
        sampleInfo.zEnd = zEnd;
        sampleInfo.z_ini = z_ini;
        sampleInfo.z_range_um = Z_RANGE_UM;

        sampleInfo.nImages = nImages;
        sampleInfo.files = files;
        sampleInfo.zAxisAll = zAxisAll;
        sampleInfo.dzOriginal = dzOriginal;

        sampleInfo.zMinReq = zMinReq;
        sampleInfo.zMaxReq = zMaxReq;
        sampleInfo.idxWindow = idxWindow;

        sampleInfo.deltaz_samp = deltaz_samp;
        sampleInfo.skipFactor = skipFactor;
        sampleInfo.idxSampled = idxSampled;
        sampleInfo.zSampled = zSampled;

        return new ReadResult(imageArray, sampleInfo);
    }

    private static boolean isSupportedImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static long numericTokenOrFallback(Path path) {
        String fileName = path.getFileName().toString();
        long lastNumber = Long.MAX_VALUE;
        Matcher matcher = NUMERIC_TOKEN_PATTERN.matcher(fileName);
        while (matcher.find()) {
            try {
                lastNumber = Long.parseLong(matcher.group());
            } catch (NumberFormatException ex) {
                // ignore invalid numeric token
            }
        }
        if (lastNumber == Long.MAX_VALUE) {
            return fileName.hashCode();
        }
        return lastNumber;
    }

    private static FolderMetadata parseFolderInfo(Path folderPath) {
        String acqDate = "UnknownDate";
        for (Path part : folderPath) {
            Matcher matcher = DATE_PATTERN.matcher(part.toString());
            if (matcher.find()) {
                acqDate = matcher.group();
                break;
            }
        }

        String channelName = Optional.ofNullable(folderPath.getFileName())
                .map(Path::toString)
                .orElse("UnknownChannel");
        String fovName = Optional.ofNullable(folderPath.getParent())
                .map(Path::getFileName)
                .map(Path::toString)
                .orElse("UnknownFOV");

        return new FolderMetadata(acqDate, fovName, channelName);
    }

    private static double[] linspace(double start, double end, int n) {
        double[] result = new double[n];
        double step = (end - start) / (n - 1);
        for (int i = 0; i < n; i++) {
            result[i] = start + i * step;
        }
        return result;
    }

    private static double mean(double[] arr) {
        double sum = 0.0;
        for (double v : arr) {
            sum += v;
        }
        return sum / arr.length;
    }

    private static double[] diff(double[] arr) {
        double[] result = new double[arr.length - 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = arr[i + 1] - arr[i];
        }
        return result;
    }

    private static class FolderMetadata {
        public final String acqDate;
        public final String fovName;
        public final String channelName;

        public FolderMetadata(String acqDate, String fovName, String channelName) {
            this.acqDate = acqDate;
            this.fovName = fovName;
            this.channelName = channelName;
        }
    }
}
