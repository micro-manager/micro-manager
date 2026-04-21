package org.micromanager.autofocus.tca_af;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class ComputeBestFocusFromSampledImagesTest {

    //@Test
    public void testBestFocusFromSyntheticImages() throws IOException {
        System.out.println("Running testBestFocusFromSyntheticImages...");  
        // Create synthetic test images with known z-positions
        List<ImageProcessor> imageProcessors = new ArrayList<>();
        List<Double> zSampledList = new ArrayList<>();
        
        // Create images at different z-positions: -5, -2, 0, 2, 5, 8, 11, 14, 17, 20
        double[] zPositions = {-5, -2, 0, 2, 5, 8, 11, 14, 17, 20};
        
        for (double z : zPositions) {
            // Create a synthetic image with focus quality that peaks at z=11.66
            // Use a Gaussian blur that decreases as we approach the best focus
            ImageProcessor ip = createSyntheticImage(z);
            imageProcessors.add(ip);
            zSampledList.add(z);
        }
        
        double z_ini = -5.0;
        double deltaz_samp = 3.0;
        double[] zSampled = zSampledList.stream().mapToDouble(Double::doubleValue).toArray();

        ComputeBestFocusFromSampledImages.Results results =
            ComputeBestFocusFromSampledImages.computeBestFocus(imageProcessors, z_ini, deltaz_samp, zSampled);

        Assert.assertNotNull("Results should not be null", results);
        Assert.assertFalse("z_best_focus should not be NaN", Double.isNaN(results.z_best_focus));

        // expected target from the user story
        Assert.assertEquals("Best focus should be around 11.66", 11.66, results.z_best_focus, 0.15);
        // system printout for reference
        System.out.println("Estimated best focus: " + results.z_best_focus);
    }


    @Test
    public void testBestFocusFromFolderImages() throws IOException {
        System.out.println("Running testBestFocusFromFolderImages...");
        String folderName = "C:\\Users\\AndreyAndreev\\EMI Dropbox\\_Dept_Convergence_Systems_Research"
                            + "\\Project_TCell_Analyzer\\02_Data\\04_Tcell_analyzer\\202603xx_ZStacks\\"
                            + "460nm\\-80to40\\C8-cells-P6\\460nm-crop";
        Path folderPath = Paths.get(folderName);
        Assume.assumeTrue("Test folder does not exist: " + folderName, Files.exists(folderPath));
        // Create synthetic test images with known z-positions
        List<ImageProcessor> imageProcessors = new ArrayList<>();
        // Read images from the folder into List:
        Files.list(folderPath)
            .filter(Files::isRegularFile)
            .filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".tif") || name.endsWith(".tiff") || name.endsWith(".png") || name.endsWith(".jpg");
            })
            .sorted(Comparator.comparing(p -> parseZFromFileName(p.getFileName().toString()).orElse(Double.NaN)))
            .forEach(p -> {
                try {
                    ImagePlus imp = IJ.openImage(p.toString());
                    if (imp != null) {
                        imageProcessors.add(imp.getProcessor());
                    } else {
                        System.err.println("Failed to open image: " + p);
                    }
                } catch (Exception ex) {
                    System.err.println("Error processing file " + p + ": " + ex.getMessage());
                }
            });
        System.out.println("Loaded images into array, num images: " + imageProcessors.size());

        List<Double> zSampledList = new ArrayList<>();
        double deltaz = 0.1;

        // create list of z positions with equal spacing based on number of images and assumed deltaz_samp
        if (!imageProcessors.isEmpty()) {
            double z_min = -80.0;
            double z_max = 40.0;
            int numImages = imageProcessors.size();
            deltaz = (z_max - z_min) / (numImages - 1);
            for (int i = 0; i < numImages; i++) {
                zSampledList.add(z_min + i * deltaz);
            }
        }
        System.out.println("Created Z positions list, deltaZ = " + deltaz);

        double z_ini = 0.0;
        double deltaz_samp = deltaz;
        double[] zSampled = zSampledList.stream().mapToDouble(Double::doubleValue).toArray();

        System.out.println("Computing best focus...");
        ComputeBestFocusFromSampledImages.Results results =
            ComputeBestFocusFromSampledImages.computeBestFocus(imageProcessors, z_ini, deltaz_samp, zSampled);

        Assert.assertNotNull("Results should not be null", results);
        Assert.assertFalse("z_best_focus should not be NaN", Double.isNaN(results.z_best_focus));

        // expected target from the user story
        Assert.assertEquals("Best focus should be around -1.6868", -1.6868, results.z_best_focus, 0.15);
        // system printout for reference
        System.out.println("Estimated best focus: " + results.z_best_focus);
    }
    
    private ImageProcessor createSyntheticImage(double z) {
        // Create a 100x100 synthetic image with focus quality that peaks at z=11.66
        // The focus quality decreases with distance from optimal focus
        int width = 100;
        int height = 100;
        ij.process.ByteProcessor ip = new ij.process.ByteProcessor(width, height);
        
        // Distance from optimal focus (11.66)
        double distanceFromFocus = Math.abs(z - 11.66);
        
        // Blur sigma increases with distance from focus
        double sigma = distanceFromFocus * 0.4;
        
        // Create a simple pattern (alternating squares)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Create a checkerboard pattern
                int value = ((x / 10) + (y / 10)) % 2 == 0 ? 255 : 0;
                ip.set(x, y, value);
            }
        }
        
        // Apply Gaussian blur to simulate defocus
        if (sigma > 0) {
            ij.plugin.filter.GaussianBlur gb = new ij.plugin.filter.GaussianBlur();
            gb.blur(ip, sigma);
        }
        
        return ip;
    }

    private OptionalDouble parseZFromFileName(String fileName) {
        // find first floating numeric token in the file name
        String normalized = fileName.replaceAll("[^0-9+\\-\\.,eE]", " ");
        String[] tokens = normalized.trim().split("\\s+");
        for (String token : tokens) {
            try {
                if (token.endsWith(".")) {
                    token = token.substring(0, token.length()-1);
                }
                double val = Double.parseDouble(token);
                return OptionalDouble.of(val);
            } catch (NumberFormatException ex) {
                // skip
            }
        }
        return OptionalDouble.empty();
    }
}
