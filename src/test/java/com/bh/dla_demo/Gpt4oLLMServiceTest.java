package com.bh.dla_demo;

import com.bh.dla_demo.service.Gpt4oLLMService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.util.List;

@SpringBootTest
public class Gpt4oLLMServiceTest {

    @Autowired
    private OpenAiChatModel aiChatModel;

    @Test
    public void testGpt4oImageProcessing() throws IOException {
        // Load an image as Resource
        Resource imageResource = new ClassPathResource("test.png");
        Media imageMedia = new Media(MimeTypeUtils.IMAGE_PNG, imageResource);

        // Create a simple prompt
        String promptText = "Describe the image attached.";

        UserMessage userMessage = new UserMessage(promptText, List.of(imageMedia));

        // Create OpenAI options specifying the GPT-4O model
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel("gpt-4o")
                .withMaxTokens(4096)
                .build();

        // Create the Prompt
        Prompt prompt = new Prompt(List.of(userMessage), options);

        // Send the prompt to the LLM and get the response
        Generation generation = aiChatModel.call(prompt).getResult();

        if (generation != null && generation.getOutput() != null && generation.getOutput().getContent() != null) {
            String content = generation.getOutput().getContent();
            System.out.println("Test LLM Response: " + content);
        } else {
            System.err.println("LLM returned an incomplete response during the test.");
        }
    }
}
