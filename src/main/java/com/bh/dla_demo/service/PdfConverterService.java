package com.bh.dla_demo.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


@Service
public class PdfConverterService {

    public List<String> convertToImages(String pdfPath) throws IOException {
        File pdfFile = new File(pdfPath);
        String outputDir = pdfFile.getParent();
        String pdfName = pdfFile.getName().replaceFirst("[.][^.]+$", "");

        List<String> imagePaths = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, 300);
                String outputPath = Paths.get(outputDir, pdfName + "___page___" + pageIndex + ".png").toString();
                ImageIO.write(image, "PNG", new File(outputPath));
                imagePaths.add(outputPath);
            }
        }

        return imagePaths;
    }
}