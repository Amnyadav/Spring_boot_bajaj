package com.example.webhooksolver;

import com.example.webhooksolver.dto.SubmitRequest;
import com.example.webhooksolver.dto.WebhookResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class WebhookSolverApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebhookSolverApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(WebhookSolverApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Bean
    public CommandLineRunner runner(Environment env, RestTemplate restTemplate) {
        return args -> {
            final String generateUrl = env.getProperty("generate.url", "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA");
            final String testUrl = env.getProperty("test.url", "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA");

            final String name = env.getProperty("user.name", "Your Name");
            final String regNo = env.getProperty("user.regno", "000000");
            final String email = env.getProperty("user.email", "your.email@example.com");

            final boolean dryRun = isTrue(env.getProperty("DRY_RUN", System.getenv("DRY_RUN")));
            final boolean downloadPdf = isTrue(env.getProperty("DOWNLOAD_PDF", System.getenv("DOWNLOAD_PDF")));

            logger.info("Starting Webhook Solver (even regNo - using Question 2)");
            logger.info("Question 2 PDF: https://drive.google.com/file/d/143MR5cLFrlNEuHzzWJ5RHnEWuijuM9X/view?usp=sharing");

            if (downloadPdf) {
                tryDownloadQuestionPdf();
            }

            WebhookResponse webhookResponse = null;

            Map<String, String> payload = new HashMap<>();
            payload.put("name", name);
            payload.put("regNo", regNo);
            payload.put("email", email);

            int maxAttempts = 3;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    logger.info("Requesting webhook (attempt {}/{}): {}", attempt, maxAttempts, generateUrl);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);

                    ResponseEntity<WebhookResponse> responseEntity = restTemplate.exchange(
                            generateUrl,
                            HttpMethod.POST,
                            request,
                            WebhookResponse.class
                    );

                    if (responseEntity.getStatusCode().is2xxSuccessful()) {
                        webhookResponse = responseEntity.getBody();
                        logger.info("GenerateWebhook responded with status {}", responseEntity.getStatusCode());
                        break;
                    } else {
                        logger.error("GenerateWebhook failed with status {}", responseEntity.getStatusCode());
                    }
                } catch (RestClientException ex) {
                    logger.error("GenerateWebhook request failed on attempt {}: {}", attempt, ex.getMessage());
                }

                if (attempt < maxAttempts) {
                    try {
                        long sleepMs = attempt * 1000L;
                        logger.info("Backing off for {} ms before retry...", sleepMs);
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (webhookResponse == null) {
                logger.error("Failed to generate webhook after {} attempts. Exiting with code 3.", maxAttempts);
                System.exit(3);
                return;
            }

            if (!StringUtils.hasText(webhookResponse.getWebhook())) {
                logger.error("Missing 'webhook' in response. Exiting with code 4.");
                System.exit(4);
                return;
            }

            if (!StringUtils.hasText(webhookResponse.getAccessToken())) {
                logger.error("Missing 'accessToken' in response. Exiting with code 5.");
                System.exit(5);
                return;
            }

            logger.info("Received webhook: {}", webhookResponse.getWebhook());
            logger.info("Received accessToken (redacted length={}): {}", webhookResponse.getAccessToken().length(), redactToken(webhookResponse.getAccessToken()));

            String finalQuery = getFinalQuery(env);
            if (!StringUtils.hasText(finalQuery)) {
                logger.error("FINAL_QUERY not set. Set environment variable FINAL_QUERY or system property -Dfinal.query. Exiting with code 2.");
                System.exit(2);
                return;
            }

            SubmitRequest submitRequest = new SubmitRequest(finalQuery);

            if (dryRun) {
                logger.info("DRY_RUN=true - not submitting to remote endpoint");
                logger.info("Would POST to {} with headers Authorization=[{}] and body: {}", testUrl, webhookResponse.getAccessToken(), toJson(submitRequest));
                System.exit(0);
                return;
            }

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                // Per requirements: use the token exactly as returned, do not prefix with Bearer
                headers.set("Authorization", webhookResponse.getAccessToken());
                HttpEntity<SubmitRequest> entity = new HttpEntity<>(submitRequest, headers);

                logger.info("Submitting final query to {}", testUrl);
                ResponseEntity<String> submitResponse = restTemplate.exchange(testUrl, HttpMethod.POST, entity, String.class);

                logger.info("Submission response status: {}", submitResponse.getStatusCode());
                logger.info("Submission response body: {}", submitResponse.getBody());
                System.exit(0);
            } catch (RestClientException ex) {
                logger.error("Submission failed: {}", ex.getMessage());
                System.exit(6);
            }
        };
    }

    private static String getFinalQuery(Environment env) {
        String fromEnv = System.getenv("FINAL_QUERY");
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv;
        }
        return env.getProperty("final.query", System.getProperty("final.query"));
    }

    private static String redactToken(String token) {
        if (token == null) return "";
        int len = token.length();
        if (len <= 6) return "***";
        return token.substring(0, 3) + "***" + token.substring(len - 3);
    }

    private static String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private static boolean isTrue(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase();
        return v.equals("1") || v.equals("true") || v.equals("yes");
    }

    private static void tryDownloadQuestionPdf() {
        String url = "https://drive.google.com/uc?export=download&id=143MR5cLFrlNEuHzzWJ5RHnEWuijuM9X";
        Path output = Path.of("downloads", "question2.pdf");
        try {
            Files.createDirectories(output.getParent());
            logger.info("Downloading Question 2 PDF to {}", output.toAbsolutePath());
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Files.write(output, response.body());
                logger.info("Downloaded PDF ({} bytes)", response.body().length);
            } else {
                logger.warn("PDF download failed with status {} - continuing without blocking", response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            logger.warn("PDF download failed: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}


