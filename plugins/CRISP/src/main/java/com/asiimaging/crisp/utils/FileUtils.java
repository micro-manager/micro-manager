/**
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Utilities for interacting with the file system.
 */
public class FileUtils {

    public static Path initPath(final String root, final String fileName, String ext) {
        int count = 0;
        ext = ext.toLowerCase();
        Path path = Paths.get(root, fileName + "." + ext);
        while (Files.exists(path)) {
            path = Paths.get(root, fileName + count + "." + ext);
            count++;
        }
        return path;
    }

    public static void createDirectory(final String root) throws Exception {
        final File directory = new File(root);
        if (!directory.exists()) {
            if (!directory.mkdir()) {
                throw new Exception();
            }
        }
    }

    public static List<String> readFile(final File file) throws IOException {
        return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
    }

    public static void saveTextFile(final List<String> file, final String filePath) {
        // TODO: implement this feature to make saving a focus curve possible
    }
}
