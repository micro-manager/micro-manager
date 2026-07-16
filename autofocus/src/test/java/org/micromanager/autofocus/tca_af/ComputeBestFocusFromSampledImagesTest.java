package org.micromanager.autofocus.tca_af;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ImageProcessor;

public class ComputeBestFocusFromSampledImagesTest {

    //@Test
    public void testLocalSmoothingSplineFit() {
        double[] x = {0.0, 1.0, 2.0, 3.0, 4.0};
        double[] y = {0.0, 1.0, 0.0, -1.0, 0.0};
        System.out.println("Running testLocalSmoothingSplineFit...");
        LocalSmoothingSplineFit.FitResult result = LocalSmoothingSplineFit.fit(x, y, 0.1);

        for (double xx : x) {
            System.out.println("Local Smoothing (" + xx + ") = " + result.fitFunction.value(xx));
        }
    }

    //@Test
    public void testBestFocusFromFolderImages460() throws IOException {
         
        System.out.println("Running testBestFocusFromFolderImages for 460nm...");
        String folderName = System.getProperty("user.home") + "\\EMI Dropbox\\_Dept_Convergence_Systems_Research"
                            + "\\Project_Cell Analyzer Imaging Platform\\02_Data\\04_Tcell_analyzer\\20260306_and_20260310_ZStacks\\"
                            + "460nm\\-80to50\\C8-cells-P6\\460nm";
        Path folderPath = Paths.get(folderName);
        System.out.println("Folder exists? " + Files.exists(folderPath) + ", path: " + folderPath.toAbsolutePath());
        Assume.assumeTrue("Test folder does not exist: " + folderName, Files.exists(folderPath));
        // Create synthetic test images with known z-positions
        List<ImageProcessor> imageArray = new ArrayList<>();

        double z_ini = 0.0;
        double deltaZ = 1.0;
        // Read images from the folder into List:
        ReadZStackSampledImages.ReadResult readResult = ReadZStackSampledImages.readZStackSampledImages(
                folderName, -80.0, 50.0, z_ini, deltaZ); 
                
        imageArray = readResult.imageArray;
        
        System.out.println("Loaded images into array, num images: " + imageArray.size());

        double[] zSampled = readResult.sampleInfo.zSampled;
        
        System.out.println("Computing best focus...");
        ComputeBestFocus460nm.Results results =
            ComputeBestFocus460nm.computeBestFocus(imageArray, z_ini, deltaZ, zSampled);

        Assert.assertNotNull("Results should not be null", results);
        Assert.assertFalse("z_best_focus should not be NaN", Double.isNaN(results.z_best_focus));

        // expected target from the user story
        Assert.assertEquals("Best focus should be around -1.6868", -1.6868, results.z_best_focus, 0.15);
        // system printout for reference
        
        System.out.println("Estimated best focus: " + results.z_best_focus);
    }

 
    //@Test
    public void testBestFocusFromFolderImagesNADH_rel() throws IOException {
        System.out.println("Running testBestFocusFromFolderImages for NADH...");
        String folderName = System.getProperty("user.home") + "\\EMI Dropbox\\_Dept_Convergence_Systems_Research"
                + "\\Project_Cell Analyzer Imaging Platform\\02_Data\\04_Tcell_analyzer\\20260306_and_20260310_ZStacks\\"
                + "NADH\\-90 to 40\\C8-cells-4\\NADH";

        Path folderPath = Paths.get(folderName);       
        System.out.println("Folder exists? " + Files.exists(folderPath)+ ", path: " + folderPath.toAbsolutePath());
        Assume.assumeTrue("Test folder does not exist: " + folderName,Files.exists(folderPath));

        ReadZStackSampledImages.ReadResult readResult = ReadZStackSampledImages.readZStackSampledImages(folderName, -90.0, 40.0, 5.0, 1.0);

        List<ImageProcessor> imageArray = readResult.imageArray;

        System.out.println("Loaded images into array, num images: " + imageArray.size());

        double[] zSampled = readResult.sampleInfo.zSampled;

        System.out.println("Computing best focus...");

        ComputeBestFocusNADH.Results results = ComputeBestFocusNADH.compute(imageArray, 5.0, 1.0, zSampled);

        Assert.assertNotNull("Results should not be null", results);

        Assert.assertFalse( "zBestFocus should not be NaN",Double.isNaN(results.zBestFocus));

        Assert.assertEquals("Best focus should be around 1.6833", 1.6833, results.zBestFocus, 0.15);

        System.out.println("Estimated best focus: " + results.zBestFocus);

    }

    //@Test
    public void testBestFocusFromFolderImages300() throws IOException {
        System.out.println("Running testBestFocusFromFolderImages for 300nm...");
        String folderName = System.getProperty("user.home") + "\\EMI Dropbox\\_Dept_Convergence_Systems_Research"
                            + "\\Project_Cell Analyzer Imaging Platform\\02_Data\\04_Tcell_analyzer\\20260306_and_20260310_ZStacks\\"
                            + "300nm\\-90 to 40\\C8-cells-6\\300nm";
                            
        Path folderPath = Paths.get(folderName);
        System.out.println("Folder exists? " + Files.exists(folderPath) + ", path: " + folderPath.toAbsolutePath());
        Assume.assumeTrue("Test folder does not exist: " + folderName, Files.exists(folderPath));
        // Create synthetic test images with known z-positions
        List<ImageProcessor> imageArray = new ArrayList<>();

        double z_ini = -61;
        double deltaZ = 1.0;
        // Read images from the folder into List:
        ReadZStackSampledImages.ReadResult readResult = ReadZStackSampledImages.readZStackSampledImages(
                folderName, -90.0, 40.0, z_ini, deltaZ); 
                
        imageArray = readResult.imageArray;
        
        System.out.println("Loaded images into array, num images: " + imageArray.size());

        double[] zSampled = readResult.sampleInfo.zSampled;
        
        System.out.println("Computing best focus...");
        ComputeBestFocus300nm.Results results =
            ComputeBestFocus300nm.computeBestFocus(imageArray, z_ini, deltaZ, zSampled);

        Assert.assertNotNull("Results should not be null", results);
        Assert.assertFalse("z_best_focus should not be NaN", Double.isNaN(results.z_best_focus));

        // expected target from the user story
        Assert.assertEquals("Best focus should be around -64", -64, results.z_best_focus, 0.15);
        // system printout for reference
        System.out.println("Estimated best focus: " + results.z_best_focus);
    }

    //@Test
    public void testBestFocusFromFolderImagesFAD() throws IOException {
        System.out.println("Running testBestFocusFromFolderImages for FAD...");
        String folderName = System.getProperty("user.home") + "\\EMI Dropbox\\_Dept_Convergence_Systems_Research"
                + "\\Project_Cell Analyzer Imaging Platform\\02_Data\\04_Tcell_analyzer\\20260306_and_20260310_ZStacks\\"
                + "FAD\\-80to50\\C8-cells-P6\\FAD";

        Path folderPath = Paths.get(folderName);       
        System.out.println("Folder exists? " + Files.exists(folderPath)+ ", path: " + folderPath.toAbsolutePath());
        Assume.assumeTrue("Test folder does not exist: " + folderName,Files.exists(folderPath));

        ReadZStackSampledImages.ReadResult readResult = ReadZStackSampledImages.readZStackSampledImages(folderName, -80.0, 50.0, -10.0, 1.0);

        List<ImageProcessor> imageArray = readResult.imageArray;

        System.out.println("Loaded images into array, num images: " + imageArray.size());

        double[] zSampled = readResult.sampleInfo.zSampled;

        System.out.println("Computing best focus...");

        for (int i = 0; i < imageArray.size(); i++) {
            ImageProcessor ip = imageArray.get(i);

            int w = ip.getWidth();
            int h = ip.getHeight();

            float[][] img = new float[h][w];

            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    img[r][c] = ip.getf(c, r);
                }
            }

        }

        ComputeBestFocusFAD.Results results = ComputeBestFocusFAD.compute(imageArray, -10, 1.0, zSampled);

        System.out.println("Estimated best focus: " + results.zBestFocus);

        Assert.assertNotNull("Results should not be null", results);

        Assert.assertFalse( "zBestFocus should not be NaN",Double.isNaN(results.zBestFocus));

        Assert.assertEquals("Best focus should be around -25.9850", -25.9850, results.zBestFocus, 0.15);

        System.out.println("Estimated best focus: " + results.zBestFocus);

    }
    
    @Test
    public void testBestFocusFromFolderImages600nm_rel() throws IOException {
        System.out.println("Running testBestFocusFromFolderImages for 600nm...");
        String folderName = System.getProperty("user.home") + "\\EMI Dropbox\\_Dept_Convergence_Systems_Research\\"
                + "\\Project_Cell Analyzer Imaging Platform\\02_Data\\04_Tcell_analyzer\\20260623-Jurkat-z-stack\\z_stack_600\\D3\\\\D3_1\\Default\\Calbryte";

        Path folderPath = Paths.get(folderName);       
        System.out.println("Folder exists? " + Files.exists(folderPath)+ ", path: " + folderPath.toAbsolutePath());
        Assume.assumeTrue("Test folder does not exist: " + folderName,Files.exists(folderPath));

        ReadZStackSampledImages.ReadResult readResult = ReadZStackSampledImages.readZStackSampledImages(folderName, -70.0, 70.0, 25.0, 1.0, 30.0);

        List<ImageProcessor> imageArray = readResult.imageArray;

        System.out.println("Loaded images into array, num images: " + imageArray.size());

        double[] zSampled = readResult.sampleInfo.zSampled;

        System.out.println("Computing best focus...");

        ComputeBestFocus600nm.Results results = ComputeBestFocus600nm.computeBestFocus(imageArray, 5.0, 1.0, zSampled);

        Assert.assertNotNull("Results should not be null", results);

        Assert.assertFalse( "zBestFocus should not be NaN",Double.isNaN(results.z_best_focus));

        Assert.assertEquals("Best focus should be around 20.0583", 20.0583, results.z_best_focus, 0.15);

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
    //@Test
    public void testReadZStackSampledImagesFromFolder() throws IOException {
        Path tempFolder = Files.createTempDirectory("zstack_sampled_images_test");
        try {
            for (int i = 0; i < 8; i++) {
                ij.process.ByteProcessor ip = new ij.process.ByteProcessor(64, 64);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        ip.set(x, y, (x + y + i) % 256);
                    }
                }
                ImagePlus imp = new ImagePlus("img" + i, ip);
                FileSaver saver = new FileSaver(imp);
                saver.saveAsTiff(tempFolder.resolve(String.format("image_%03d.tif", i)).toString());
            }

            ReadZStackSampledImages.ReadResult result = ReadZStackSampledImages.readZStackSampledImages(
                    tempFolder.toString(),
                    0.0,
                    7.0,
                    3.5,
                    2.0);

            Assert.assertNotNull("Read result should not be null", result);
            Assert.assertNotNull("Image array should not be null", result.imageArray);
            Assert.assertTrue("At least two sampled images should be returned", result.imageArray.size() >= 2);
            Assert.assertEquals("Sampled z positions should match sample count",
                    result.sampleInfo.zSampled.length, result.imageArray.size());
            //Assert.assertEquals("Skip factor should be 1 or greater", 1, result.sampleInfo.skipFactor);
            Assert.assertEquals("Folder path should be preserved",
                    tempFolder.toString(), result.sampleInfo.folderPath);
        } finally {
            try (java.util.stream.Stream<Path> stream = Files.list(tempFolder)) {
                stream.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // ignore
                    }
                });
            }
            Files.deleteIfExists(tempFolder);
        }
    }
    
}
