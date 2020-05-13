package com.epam.voreporter.service;

import com.epam.voreporter.integration.V1IntegrationService;
import com.epam.voreporter.utils.ReportPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ReportService {

    @Autowired
    private V1IntegrationService v1IntegrationService;
    @Autowired
    private ReportPrinter reportPrinter;

    @Value("${outlook.path}")
    private String outlookPath;
    @Value("${mail.to}")
    private String mailTo;
    @Value("${mail.cc}")
    private String mailCc;

    /**
     * Generates report and opens it in Outlook app.
     */
    public void openReportInOutlook() {
        try {
            Path reportPath = Files.createTempFile("report", "report.html");
            reportPrinter.print(v1IntegrationService.getStories(), reportPath);
            new ProcessBuilder(outlookPath, "/c", "ipm.note",
                "/a", reportPath.toAbsolutePath().toString(),
                "/m", String.format("%s?cc=%s&subject=%s", mailTo, mailCc,
                String.format("Daily Status (%s)", DateTimeFormatter.ofPattern("MM/dd/yyyy").format(LocalDate.now()))))
                .start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
