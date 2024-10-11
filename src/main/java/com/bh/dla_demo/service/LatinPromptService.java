package com.bh.dla_demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import java.io.File;
import java.io.IOException;

@Service
public class LatinPromptService {

    private static final Logger logger = LoggerFactory.getLogger(LatinPromptService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<List<String>> processOcrData(String ocrJsonPath) throws IOException {
        logger.info("Processing OCR data from: {}", ocrJsonPath);

        // Read the OCR-like JSON data
        Map<String, Object> ocrData = objectMapper.readValue(
                new File(ocrJsonPath),
                new TypeReference<Map<String, Object>>() {}
        );

        List<Map<String, Object>> pages = (List<Map<String, Object>>) ocrData.get("pages");
        List<List<String>> allPagesPrompts = new ArrayList<>();

        for (Map<String, Object> page : pages) {
            int pageNumber = (int) page.get("page_number");
            logger.info("Processing page {}", pageNumber);

            List<Map<String, Object>> objects = (List<Map<String, Object>>) page.get("objects");

            // Extract texts and boxes
            List<String> texts = new ArrayList<>();
            List<int[]> boxes = new ArrayList<>();

            for (Map<String, Object> item : objects) {
                String text = (String) item.get("text");
                texts.add(text);

                @SuppressWarnings("unchecked")
                Map<String, Number> bbox = (Map<String, Number>) item.get("bounding_box");
                int x = bbox.get("x").intValue();
                int y = bbox.get("y").intValue();
                int w = bbox.get("w").intValue();
                int h = bbox.get("h").intValue();

                boxes.add(new int[]{x, y, x + w, y + h});
            }

            List<String> pagePrompt = improvedSpaceLayout(texts, boxes);
            allPagesPrompts.add(pagePrompt);
        }

        return allPagesPrompts;
    }


    private List<String> improvedSpaceLayout(List<String> texts, List<int[]> boxes) {
        // Combine texts and boxes into items
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            items.add(new Item(texts.get(i), boxes.get(i)));
        }

        // Sort items by y (top coordinate), then x (left coordinate)
        items.sort(Comparator.comparingInt((Item item) -> item.box[1])
                .thenComparingInt(item -> item.box[0]));

        List<String> result = new ArrayList<>();
        List<Item> currentLine = new ArrayList<>();
        int[] currentBox = null;

        for (Item item : items) {
            if (currentLine.isEmpty() || isSameLine(currentBox, item.box)) {
                currentLine.add(item);
                currentBox = currentBox == null ? item.box : unionBox(currentBox, item.box);
            } else {
                result.add(processLine(currentLine));
                currentLine = new ArrayList<>();
                currentLine.add(item);
                currentBox = item.box;
            }
        }

        if (!currentLine.isEmpty()) {
            result.add(processLine(currentLine));
        }

        // Add empty lines between non-adjacent lines
        List<String> finalResult = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            if (i > 0 && !isAdjacentLine(items.get(i - 1).box, items.get(i).box)) {
                finalResult.add(""); // Add an empty line
            }
            finalResult.add(result.get(i));
        }

        return finalResult;
    }

    private String processLine(List<Item> line) {
        // Sort line items by x (left coordinate)
        line.sort(Comparator.comparingInt(item -> item.box[0]));
        StringBuilder lineText = new StringBuilder();
        int lastEnd = 0;

        for (Item item : line) {
            int spaces = Math.max(0, (item.box[0] - lastEnd) / 10); // Approximate character width is 10
            lineText.append(" ".repeat(spaces)).append(item.text);
            lastEnd = item.box[2];
        }

        return lineText.toString().trim();
    }

    private boolean isSameLine(int[] box1, int[] box2) {
        double box1MidY = (box1[1] + box1[3]) / 2.0;
        double box2MidY = (box2[1] + box2[3]) / 2.0;
        double minHeight = Math.min(box1[3] - box1[1], box2[3] - box2[1]);
        return Math.abs(box1MidY - box2MidY) < minHeight / 2.0;
    }

    private boolean isAdjacentLine(int[] box1, int[] box2) {
        int h1 = box1[3] - box1[1];
        int h2 = box2[3] - box2[1];
        int vDist = Math.min(box2[1], box1[1]) - Math.max(box1[3], box2[3]);
        return vDist > 0 && vDist <= Math.max(h1, h2) / 2;
    }

    private int[] unionBox(int[] box1, int[] box2) {
        return new int[]{
                Math.min(box1[0], box2[0]),
                Math.min(box1[1], box2[1]),
                Math.max(box1[2], box2[2]),
                Math.max(box1[3], box2[3])
        };
    }

    private static class Item {
        String text;
        int[] box;

        Item(String text, int[] box) {
            this.text = text;
            this.box = box;
        }
    }
}
