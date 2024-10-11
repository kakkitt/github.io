package com.bh.dla_demo.util;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Component
public class ExternalProcessRunner {

    public String runProcess(String command) {
        StringBuilder output = new StringBuilder();

        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitVal = process.waitFor();
            if (exitVal == 0) {
                return output.toString();
            } else {
                throw new RuntimeException("External process failed with exit code: " + exitVal);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error running external process", e);
        }
    }
}