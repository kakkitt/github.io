package com.bh.dla_demo.service;

import com.bh.dla_demo.model.LLMResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Service("gpt4o")
public class Gpt4oLLMService implements LLMService {

    private final OpenAiChatModel aiChatModel;
    private final String perPagePromptTemplate;
    private final String workflowPromptTemplate;
    private final BeanOutputConverter<LLMResponse> beanOutputConverter;

    @Autowired
    public Gpt4oLLMService(@Qualifier("openAiChatModel") ChatModel chatModel) throws IOException {
        this.aiChatModel = (OpenAiChatModel) chatModel;
        this.beanOutputConverter = new BeanOutputConverter<>(LLMResponse.class);

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

    private String getLLMResponseForPage(String latinPrompt, String ocrLikeData, String imagePath, int pageNumber) {
        try {
            // Read the image file as a Resource
            Resource imageResource = new FileSystemResource(imagePath);
            if (!imageResource.exists()) {
                System.err.println("Image file does not exist: " + imagePath);
                return null;
            }

            // Determine the MIME type based on the file extension
            String mimeType;
            if (imagePath.endsWith(".png")) {
                mimeType = MimeTypeUtils.IMAGE_PNG_VALUE;
            } else if (imagePath.endsWith(".jpg") || imagePath.endsWith(".jpeg")) {
                mimeType = MimeTypeUtils.IMAGE_JPEG_VALUE;
            } else {
                throw new IllegalArgumentException("Unsupported image format");
            }

            // Create the Media object with the image resource
            Media imageMedia = new Media(MimeTypeUtils.parseMimeType(mimeType), imageResource);

            // Read the image to get its dimensions
            BufferedImage image;
            try {
                image = ImageIO.read(imageResource.getInputStream());
                if (image == null) {
                    System.err.println("Failed to read image: " + imagePath);
                    return null;
                }
            } catch (IOException e) {
                System.err.println("IOException while reading image: " + imagePath);
                e.printStackTrace();
                return null;
            }

            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();

            // Log image dimensions
            System.out.println("Image dimensions for page " + pageNumber + ": " + imageWidth + "x" + imageHeight);

            // Format the per-page prompt with actual data
            String formattedPrompt = String.format(perPagePromptTemplate, imageWidth, imageHeight, pageNumber, latinPrompt, ocrLikeData);

            // Generate format instructions from the BeanOutputConverter
            String formatInstructions = beanOutputConverter.getFormat() + "\n\nPlease provide the response strictly in valid JSON format without any additional text or explanations.";

            // Create the full prompt including format instructions
            String template = formattedPrompt + "\n\n" + "{format}";

            // Create PromptTemplate
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("format", formatInstructions);
            PromptTemplate promptTemplate = new PromptTemplate(template, parameters);

            // Create the UserMessage with the prompt text and the Media
            Message message = promptTemplate.createMessage();
            String promptText = message.getContent();

            // Log the promptText for debugging
            System.out.println("Prompt for page " + pageNumber + ": " + promptText);

            UserMessage userMessage = new UserMessage(promptText, List.of(imageMedia));

            // Create OpenAI options specifying the GPT-4O model
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .withModel("gpt-4o")
                    .withMaxTokens(4096)
                    .build();

            // Create the Prompt
            Prompt prompt = new Prompt(List.of(userMessage), options);

            // Send the prompt to the LLM and get the response
            Generation generation;
            try {
                generation = aiChatModel.call(prompt).getResult();
            } catch (Exception e) {
                System.err.println("Error during LLM call for page " + pageNumber);
                e.printStackTrace();

                // Log the exception message
                System.err.println("Exception message: " + e.getMessage());

                // Log the cause of the exception
                Throwable cause = e.getCause();
                while (cause != null) {
                    System.err.println("Caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
                    cause = cause.getCause();
                }

                return null;
            }

            if (generation == null || generation.getOutput() == null || generation.getOutput().getContent() == null) {
                System.err.println("LLM returned an incomplete response for page " + pageNumber);
                return null;
            }

            String content = generation.getOutput().getContent();
            if (content == null || content.isEmpty()) {
                System.err.println("LLM returned empty content for page " + pageNumber);
                return null;
            }

            // Log the response for debugging
            System.out.println("LLM Response for page " + pageNumber + ": " + content);

            return content;
        } catch (Exception e) {
            System.err.println("Exception during getLLMResponseForPage for page " + pageNumber);
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public String getLLMWorkflowResponse(String combinedDocumentText) throws IOException {
        // Format the workflow prompt with actual data
        String formattedPrompt = String.format(workflowPromptTemplate, combinedDocumentText);

        // Generate format instructions from the BeanOutputConverter
        String formatInstructions = beanOutputConverter.getFormat();

        // Create the full prompt including format instructions
        String template = formattedPrompt + "\n\n" + "{format}";

        // Create PromptTemplate
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("format", formatInstructions);
        PromptTemplate promptTemplate = new PromptTemplate(template, parameters);

        // Create the UserMessage with the prompt text
        Message message = promptTemplate.createMessage();
        String promptText = message.getContent();
        UserMessage userMessage = new UserMessage(promptText);

        // Create OpenAI options specifying the GPT-4O model
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel("gpt-4o")
                .withMaxTokens(4096)
                .build();

        // Create the Prompt
        Prompt prompt = new Prompt(List.of(userMessage), options);

        // Send the prompt to the LLM and get the response
        Generation generation = aiChatModel.call(prompt).getResult();

        if (generation == null || generation.getOutput() == null || generation.getOutput().getContent() == null) {
            System.err.println("LLM returned an incomplete workflow response");
            return null;
        }

        String content = generation.getOutput().getContent();
        if (content == null || content.isEmpty()) {
            System.err.println("LLM returned empty workflow content");
            return null;
        }

        return content;
    }

    @Override
    public LLMResponse convertResponse(String responseStr) throws IOException {
        // Use the BeanOutputConverter to parse the response
        return beanOutputConverter.convert(responseStr);
    }
}
