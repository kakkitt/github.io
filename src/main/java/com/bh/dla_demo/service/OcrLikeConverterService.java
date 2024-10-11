package com.bh.dla_demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
public class OcrLikeConverterService {

    private static final Logger logger = LoggerFactory.getLogger(OcrLikeConverterService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String processFile(String jsonPath, String outputDir) throws IOException {
        logger.info("Processing file: {}", jsonPath);
        File jsonFile = new File(jsonPath);
        String pdfName = jsonFile.getName().replace("___preprocessed.json", "");

        // Corrected: Use the same directory as the JSON file for PNG images
        String pngDir = jsonFile.getParent();
        String outputPath = outputDir + File.separator + "ocr_like";
        new File(outputPath).mkdirs();

        logger.info("PNG directory: {}", pngDir);
        logger.info("Output path: {}", outputPath);

        try {
            convertAndSortJsonMultiPage(jsonPath, outputPath, pngDir);
        } catch (Exception e) {
            logger.error("Error in convertAndSortJsonMultiPage", e);
            throw e;
        }

        String resultPath = outputPath + File.separator + pdfName + "_ocr_like.json";
        logger.info("Result file path: {}", resultPath);
        return resultPath;
    }

    public void convertAndSortJsonMultiPage(String inputFile, String outputDirectory, String imageDirectory) throws IOException {
        logger.info("Converting and sorting JSON: {}", inputFile);
        Map<String, Object> originalData = objectMapper.readValue(new File(inputFile), Map.class);
        String pdfName = new File(inputFile).getName().replace("___preprocessed.json", "");

        List<Map<String, Object>> pages = (List<Map<String, Object>>) originalData.get("pages");
        List<Map<String, Object>> outputPages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            logger.info("Processing page {}", pageIndex + 1);
            Map<String, Object> pageData = pages.get(pageIndex);

            // Adjusted: Find the image for the specific page
            String imagePath = findMatchingImage(imageDirectory, pdfName, pageIndex);

            if (imagePath == null) {
                logger.warn("Warning: Image not found for {} page {}", pdfName, pageIndex + 1);
                continue;
            }

            BufferedImage image = ImageIO.read(new File(imagePath));
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            Map<String, Double> bounds = (Map<String, Double>) pageData.get("bounds");
            double docWidth = bounds.get("right") - bounds.get("left");
            double docHeight = bounds.get("bottom") - bounds.get("top");

            double xRatio = imageWidth / docWidth;
            double yRatio = imageHeight / docHeight;

            List<Map<String, Object>> newData = new ArrayList<>();

            List<Map<String, Object>> objects = (List<Map<String, Object>>) pageData.get("objects");
            for (Map<String, Object> obj : objects) {
                String objType = obj.keySet().iterator().next();
                Map<String, Object> objData = (Map<String, Object>) ((Map<String, Object>) obj.get(objType)).get("source");
                Map<String, Double> bbox = (Map<String, Double>) objData.get("bounding_box");

                double left = (bbox.get("left") - bounds.get("left")) * xRatio;
                double top = (bbox.get("top") - bounds.get("top")) * yRatio;
                double right = (bbox.get("right") - bounds.get("left")) * xRatio;
                double bottom = (bbox.get("bottom") - bounds.get("top")) * yRatio;

                Map<String, Object> newObj = new HashMap<>();
                newObj.put("type", objType);
                newObj.put("text", objData.getOrDefault("text", ""));
                Map<String, Double> newBbox = new HashMap<>();
                newBbox.put("x", left);
                newBbox.put("y", top);
                newBbox.put("w", right - left);
                newBbox.put("h", bottom - top);
                newObj.put("bounding_box", newBbox);

                newData.add(newObj);
            }

            // Sort objects by their 'y' coordinate
            newData.sort(Comparator.comparingDouble(a -> ((Map<String, Double>) a.get("bounding_box")).get("y")));

            // Add the page data to outputPages
            Map<String, Object> pageOutput = new HashMap<>();
            pageOutput.put("page_number", pageIndex + 1);
            pageOutput.put("objects", newData);
            outputPages.add(pageOutput);

            logger.info("Processed page {}", pageIndex + 1);
        }

        if (!outputPages.isEmpty()) {
            String outputFile = outputDirectory + File.separator + pdfName + "_ocr_like.json";
            Map<String, Object> finalOutput = new HashMap<>();
            finalOutput.put("pages", outputPages);
            objectMapper.writeValue(new File(outputFile), finalOutput);
            logger.info("Processed {} and saved to {}", pdfName, outputFile);
        } else {
            logger.warn("No data to write for {}", pdfName);
        }
    }


    private String findMatchingImage(String imageDirectory, String pdfName, int pageIndex) {
        logger.info("Finding matching image for {} page {} in {}", pdfName, pageIndex + 1, imageDirectory);
        File dir = new File(imageDirectory);
        String pageIndicator = "___page___" + pageIndex;
        File[] files = dir.listFiles((d, name) -> name.startsWith(pdfName) && name.contains(pageIndicator) && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")));
        if (files != null && files.length > 0) {
            logger.info("Found matching image: {}", files[0].getAbsolutePath());
            return files[0].getAbsolutePath();
        } else {
            logger.warn("No matching image found for {} page {}", pdfName, pageIndex + 1);
            return null;
        }
    }


    public void processDirectory(String inputDir) {
        logger.info("Processing directory: {}", inputDir);

        // Adjusted: Use the same directory as inputDir for PNG images
        String pngDir = inputDir;
        String outputDir = inputDir + File.separator + "ocr_like";

        File pngDirFile = new File(pngDir);
        if (!pngDirFile.exists()) {
            logger.error("Error: PNG directory not found: {}", pngDir);
            return;
        }

        new File(outputDir).mkdirs();

        File inputDirFile = new File(inputDir);
        File[] files = inputDirFile.listFiles((d, name) -> name.endsWith("___preprocessed.json"));
        if (files != null) {
            for (File file : files) {
                try {
                    logger.info("Processing file: {}", file.getAbsolutePath());
                    convertAndSortJsonMultiPage(file.getAbsolutePath(), outputDir, pngDir);
                } catch (IOException e) {
                    logger.error("Error processing file: {}", file.getAbsolutePath(), e);
                }
            }
        } else {
            logger.warn("No preprocessed JSON files found in {}", inputDir);
        }
    }
}
