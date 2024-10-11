package com.bh.dla_demo.service;

import com.bh.dla_demo.model.LLMResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

@Service("claude")
public class ClaudeLLMService implements LLMService {

    private final ChatModel chatModel;
    private final String perPagePromptTemplate;
    private final String workflowPromptTemplate;

    @Autowired
    public ClaudeLLMService(@Qualifier("anthropicChatModel") ChatModel chatModel) throws IOException {
        this.chatModel = chatModel;

        // Load the per-page prompt template
        Resource perPageResource = new ClassPathResource("perPagePromptTemplate.txt");
        try (InputStream perPageInputStream = perPageResource.getInputStream()) {
            byte[] perPageBytes = perPageInputStream.readAllBytes();
            this.perPagePromptTemplate = new String(perPageBytes, StandardCharsets.UTF_8);
        }

        // Load the workflow prompt template
        Resource workflowResource = new ClassPathResource("workflowPromptTemplate.txt");
        try (InputStream workflowInputStream = workflowResource.getInputStream()) {
            byte[] workflowBytes = workflowInputStream.readAllBytes();
            this.workflowPromptTemplate = new String(workflowBytes, StandardCharsets.UTF_8);
        }
    }

    @Override
    public List<String> getLLMResponsesPerPage(List<String> latinPrompts, List<String> ocrLikeDatas, List<String> imagePaths) throws IOException {
        List<String> responses = new ArrayList<>();
        int pageCount = latinPrompts.size();

        // Use a thread pool for parallel execution
        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(pageCount, 5)); // Limit to 5 threads
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < pageCount; i++) {
            final int index = i;
            futures.add(executorService.submit(() -> {
                try {
                    return getLLMResponseForPage(latinPrompts.get(index), ocrLikeDatas.get(index), imagePaths.get(index), index + 1);
                } catch (Exception e) { // Catch all exceptions
                    System.err.println("Exception during LLM call for page " + (index + 1));
                    e.printStackTrace();
                    return null;
                }
            }));
        }

        // Collect responses
        for (Future<String> future : futures) {
            try {
                responses.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                responses.add(null);
            }
        }

        executorService.shutdown();

        return responses;
    }

    private String getLLMResponseForPage(String latinPrompt, String ocrLikeData, String imagePath, int pageNumber) throws IOException {
        try {
            // Read the image data from the file
            byte[] imageData = Files.readAllBytes(Paths.get(imagePath));

            // Determine the MIME type based on the file extension
            String mimeType;
            if (imagePath.endsWith(".png")) {
                mimeType = MimeTypeUtils.IMAGE_PNG_VALUE;
            } else if (imagePath.endsWith(".jpg") || imagePath.endsWith(".jpeg")) {
                mimeType = MimeTypeUtils.IMAGE_JPEG_VALUE;
            } else {
                throw new IllegalArgumentException("Unsupported image format");
            }

            // Create the Media object with the image data
            Media imageMedia = new Media(MimeTypeUtils.parseMimeType(mimeType), imageData);

            // Read the image to get its dimensions
            BufferedImage image = ImageIO.read(new File(imagePath));
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            // Format the per-page prompt with actual data
            String formattedPrompt = String.format(perPagePromptTemplate, imageWidth, imageHeight, pageNumber, latinPrompt, ocrLikeData);

            // Create the UserMessage with the formatted prompt and the Media
            UserMessage userMessage = new UserMessage(formattedPrompt, List.of(imageMedia));

            // Create the Prompt with the UserMessage
            Prompt prompt = new Prompt(List.of(userMessage));

            // Send the prompt to the LLM and get the response
            ChatResponse response = chatModel.call(prompt);

            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                System.err.println("LLM returned an incomplete response for page " + pageNumber);
                return null;
            }

            String content = response.getResult().getOutput().getContent();
            if (content == null || content.isEmpty()) {
                System.err.println("LLM returned empty content for page " + pageNumber);
                return null;
            }

            return content;
        } catch (Exception e) {
            System.err.println("Exception during getLLMResponseForPage for page " + pageNumber);
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public String getLLMWorkflowResponse(String combinedDocumentText) throws IOException {
        // Format the workflow prompt with the combined document text
        String formattedPrompt = String.format(workflowPromptTemplate, combinedDocumentText);

        // Create the UserMessage with the formatted prompt
        UserMessage userMessage = new UserMessage(formattedPrompt);

        // Create the Prompt with the UserMessage
        Prompt prompt = new Prompt(List.of(userMessage));

        // Send the prompt to the LLM and get the response
        ChatResponse response = chatModel.call(prompt);

        // Return the content of the assistant's response
        return response.getResult().getOutput().getContent();
    }

    @Override
    public LLMResponse convertResponse(String responseStr) throws IOException {
        // Use ObjectMapper to parse the JSON response into LLMResponse
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(responseStr, LLMResponse.class);
    }
}
