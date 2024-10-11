package com.bh.dla_demo.service;

import com.bh.dla_demo.model.LLMResponse;
import java.io.IOException;
import java.util.List;

public interface LLMService {
    List<String> getLLMResponsesPerPage(List<String> latinPrompts, List<String> ocrLikeDatas, List<String> imagePaths) throws IOException;
    String getLLMWorkflowResponse(String combinedDocumentText) throws IOException;
    LLMResponse convertResponse(String responseStr) throws IOException; // Add this method
}
