package org.example;

import java.io.File;
import java.util.Map;

public class FileUtils {

    private static final Map<String, String> sopClassToExtension = Map.ofEntries(
            Map.entry("1.2.840.10008.5.1.4.1.1.104.1", ".pdf")
    );

    public static String getSuffixForSopClass(String sopClass) {
        return sopClassToExtension.getOrDefault(sopClass, ".jpg");
    }


    public static String getFileNameWithoutExtension(File file) {
        String filename = file.getName();
        int end = filename.lastIndexOf(".");
        return end > 0 ? filename.substring(0, end) : filename;
    }
}
