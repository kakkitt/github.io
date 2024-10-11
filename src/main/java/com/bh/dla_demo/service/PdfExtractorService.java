package com.bh.dla_demo.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileNotFoundException;

@Service
public class PdfExtractorService {

    public String extractData(String pdfPath) throws IOException, InterruptedException {
        File pdfFile = new File(pdfPath);
        String outputDir = pdfFile.getParent();
        String extractorPath = Paths.get(System.getProperty("user.dir"), "src", "main", "exe", "pdf-data-extractor-2.exe").toString();


        ProcessBuilder processBuilder = new ProcessBuilder(extractorPath, pdfPath);
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("PDF extraction failed with exit code: " + exitCode);
        }

        String baseName = pdfFile.getName().replaceFirst("[.][^.]+$", "");
        Path preprocessedJsonPath = Paths.get(outputDir, baseName + "___preprocessed.json");
        Path objectsJsonPath = Paths.get(outputDir, baseName + "___objects.json");

        if (!preprocessedJsonPath.toFile().exists()) {
            throw new FileNotFoundException("Expected preprocessed output file not found: " + preprocessedJsonPath);
        }

        if (!objectsJsonPath.toFile().exists()) {
            throw new FileNotFoundException("Expected objects output file not found: " + objectsJsonPath);
        }

        return preprocessedJsonPath.toString();
    }
}