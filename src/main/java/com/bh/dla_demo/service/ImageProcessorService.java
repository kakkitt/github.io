package com.bh.dla_demo.service;

import com.bh.dla_demo.model.BlankComponent;
import com.bh.dla_demo.model.Coordinates;
import com.bh.dla_demo.model.LLMResponse;
import com.bh.dla_demo.model.SignaturePadText;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

// Add this import
import java.util.List;
import java.util.ArrayList;

@Service
public class ImageProcessorService {

    public List<String> drawBoundingBoxes(List<String> imagePaths, List<LLMResponse> llmResponses) throws IOException {
        List<String> outputImagePaths = new ArrayList<>();

        for (int i = 0; i < imagePaths.size(); i++) {
            String imagePath = imagePaths.get(i);
            LLMResponse llmResponse = llmResponses.get(i);

            // Load the image
            BufferedImage image = ImageIO.read(new File(imagePath));

            // Create a graphics context
            Graphics2D graphics = image.createGraphics();

            // Set rendering hints for better quality (optional)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Set the stroke and color for the bounding boxes
            graphics.setStroke(new BasicStroke(3));
            graphics.setColor(Color.RED);

            // Draw bounding boxes for blankComponents
            for (BlankComponent component : llmResponse.getBlankComponents()) {
                Coordinates coord = component.getCoordinates();
                int x = (int) coord.getX();
                int y = (int) coord.getY();
                int width = (int) coord.getWidth();
                int height = (int) coord.getHeight();
                graphics.drawRect(x, y, width, height);
            }

            // Draw bounding box for signaturePadText if it exists
            SignaturePadText signaturePadText = llmResponse.getSignaturePadText();
            if (signaturePadText != null && signaturePadText.getCoordinates() != null) {
                Coordinates coord = signaturePadText.getCoordinates();
                int x = (int) coord.getX();
                int y = (int) coord.getY();
                int width = (int) coord.getWidth();
                int height = (int) coord.getHeight();
                graphics.drawRect(x, y, width, height);
            }

            // Dispose of the graphics context
            graphics.dispose();

            // Save the new image
            String outputImagePath = imagePath.replace(".png", "_with_boxes.png");
            ImageIO.write(image, "png", new File(outputImagePath));
            outputImagePaths.add(outputImagePath);
        }

        return outputImagePaths;
    }
}
