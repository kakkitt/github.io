package com.bh.dla_demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LLMServiceFactory {

    @Autowired
    private Map<String, LLMService> llmServices;

    public LLMService getLLMService(String modelName) {
        LLMService llmService = llmServices.get(modelName.toLowerCase());
        if (llmService == null) {
            throw new IllegalArgumentException("Unsupported model: " + modelName);
        }
        return llmService;
    }
}
