package com.bh.dla_demo.controller;

import com.bh.dla_demo.model.LLMResponse;
import com.bh.dla_demo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Controller
public class PdfController {

    @Autowired
    private PdfExtractorService pdfExtractorService;

    @Autowired
    private PdfConverterService pdfConverterService;

    @Autowired
    private OcrLikeConverterService ocrLikeConverterService;

    @Autowired
    private LatinPromptService latinPromptService;

    @Autowired
    private ImageProcessorService imageProcessorService;

    // Inject the LLMServiceFactory
    @Autowired
    private LLMServiceFactory llmServiceFactory;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/upload")
    public String showUploadForm() {
        return "upload";
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file,
                                   @RequestParam("model") String modelName,
                                   Model modelAttribute) {
        if (file.isEmpty()) {
            modelAttribute.addAttribute("message", "Please select a file to upload");
            return "upload";
        }

        try {
            // Start time measurement
            long startTime = System.currentTimeMillis();

            // Upload directory setup
            String uploadDir = System.getProperty("user.dir") + File.separator + "uploads";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Save file
            String fileName = file.getOriginalFilename();
            File savedFile = new File(dir.getAbsolutePath() + File.separator + fileName);
            file.transferTo(savedFile);

            // PDF data extraction
            String jsonPath = pdfExtractorService.extractData(savedFile.getAbsolutePath());

            // Convert PDF pages to images
            List<String> imagePaths = pdfConverterService.convertToImages(savedFile.getAbsolutePath());

            // OCR-like conversion
            String ocrLikePath = ocrLikeConverterService.processFile(jsonPath, uploadDir);

            // Read the OCR-like JSON data
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> ocrLikeData = objectMapper.readValue(new File(ocrLikePath), Map.class);
            List<Map<String, Object>> pages = (List<Map<String, Object>>) ocrLikeData.get("pages");

            // Generate LATIN prompts per page and collect OCR data per page
            List<List<String>> latinPromptPages = latinPromptService.processOcrData(ocrLikePath);
            List<String> latinPrompts = new ArrayList<>();
            List<String> ocrLikeDatas = new ArrayList<>();

            for (int i = 0; i < pages.size(); i++) {
                List<String> pagePrompt = latinPromptPages.get(i);
                String pageLatinPrompt = String.join("\n", pagePrompt);
                latinPrompts.add(pageLatinPrompt);

                // Convert the objects list to JSON string
                String pageOcrLikeData = objectMapper.writeValueAsString(pages.get(i).get("objects"));
                ocrLikeDatas.add(pageOcrLikeData);
            }

            // Ensure the number of images and LATIN prompts match
            if (imagePaths.size() != latinPrompts.size()) {
                throw new RuntimeException("Mismatch between number of images and LATIN prompts");
            }

            // Get the appropriate LLMService based on the selected model
            LLMService llmService = llmServiceFactory.getLLMService(modelName.toLowerCase());

            if (llmService == null) {
                modelAttribute.addAttribute("message", "Unsupported model selected.");
                return "upload";
            }

            // Get per-page responses from LLM
            List<String> llmResponsesStr = llmService.getLLMResponsesPerPage(latinPrompts, ocrLikeDatas, imagePaths);

            // Deserialize per-page LLM responses
            List<LLMResponse> llmResponses = new ArrayList<>();
            for (String responseStr : llmResponsesStr) {
                if (responseStr != null) {
                    try {
                        LLMResponse llmResponse = llmService.convertResponse(responseStr);
                        llmResponses.add(llmResponse);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException("Failed to parse LLM response: " + responseStr, e);
                    }
                } else {
                    // Log detailed error message
                    System.err.println("Received null LLM response for one of the pages.");
                    throw new RuntimeException("LLM response is null for one of the pages");
                }
            }


            // Combine LATIN-prompts for workflow
            StringBuilder combinedDocumentText = new StringBuilder();
            for (String pageLatinPrompt : latinPrompts) {
                combinedDocumentText.append(pageLatinPrompt).append("\n");
            }

            // Get workflow response from LLM
            String workflowResponseStr = llmService.getLLMWorkflowResponse(combinedDocumentText.toString());

            // Deserialize the workflow response
            Map<String, Object> workflowResponse = objectMapper.readValue(workflowResponseStr, Map.class);

            // Draw bounding boxes on images
            List<String> imageWithBoxesPaths = imageProcessorService.drawBoundingBoxes(imagePaths, llmResponses);

            // Convert image paths to web-accessible URLs
            List<String> imageUrls = new ArrayList<>();
            for (String imagePath : imageWithBoxesPaths) {
                String imageUrl = "/uploads/" + new File(imagePath).getName();
                imageUrls.add(imageUrl);
            }

            // End time measurement
            long endTime = System.currentTimeMillis();

            // Calculate total latency
            long totalLatency = endTime - startTime; // in milliseconds
            double totalLatencySeconds = totalLatency / 1000.0;

            // Add attributes to the model
            modelAttribute.addAttribute("jsonPath", jsonPath);
            modelAttribute.addAttribute("imagePaths", imageUrls);
            modelAttribute.addAttribute("ocrLikePath", ocrLikePath);
            modelAttribute.addAttribute("latinPrompts", latinPrompts);
            modelAttribute.addAttribute("llmResponses", llmResponsesStr); // Per-page LLM responses
            modelAttribute.addAttribute("workflowResponse", workflowResponseStr); // Workflow response
            modelAttribute.addAttribute("totalLatency", totalLatencySeconds);

            return "result";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(); // Print stack trace to console
            modelAttribute.addAttribute("message", "Failed to process the file: " + e.getMessage());
            return "upload";
        }
    }
}
