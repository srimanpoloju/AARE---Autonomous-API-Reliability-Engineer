package com.aare.analyzer.service;

import com.aare.analyzer.model.Incident;
import com.aare.analyzer.model.IncidentEvidence;
import com.aare.analyzer.model.RcaReport;
import com.aare.analyzer.model.RcaStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class OpenAIService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String openaiApiBaseUrl;

    @Value("${openai.api.model:gpt-3.5-turbo}")
    private String openaiApiModel;

    public OpenAIService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, Tracer tracer) {
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.webClient = webClientBuilder
                .baseUrl(openaiApiBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public RcaReport generateRcaReport(Incident incident, List<IncidentEvidence> evidences) {
        Span span = tracer.spanBuilder("generateRcaReport").startSpan();
        try (Scope scope = span.makeCurrent()) {
            RcaReport rcaReport = new RcaReport();
            rcaReport.setIncidentId(incident.getId());
            rcaReport.setCreatedAt(LocalDateTime.now());
            rcaReport.setUpdatedAt(LocalDateTime.now());

            if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
                log.warn("OPENAI_API_KEY is not set. Skipping AI RCA report generation for incident {}", incident.getId());
                rcaReport.setStatus(RcaStatus.SKIPPED_NO_KEY);
                rcaReport.setRootCauseSummary("AI RCA skipped: OpenAI API key not configured.");
                rcaReport.setLikelyTrigger("N/A");
                rcaReport.setRecommendedFixes(List.of("Configure OPENAI_API_KEY to enable AI RCA."));
                rcaReport.setConfidence(BigDecimal.ZERO);
                return rcaReport;
            }

            try {
                String prompt = buildPrompt(incident, evidences);
                log.debug("OpenAI Prompt for incident {}:\n{}", incident.getId(), prompt);

                Map<String, Object> requestBody = Map.of(
                        "model", openaiApiModel,
                        "messages", List.of(
                                Map.of("role", "system", "content",
                                        "You are an expert API reliability engineer. Analyze the provided incident data and generate a root cause analysis report in JSON format."),
                                Map.of("role", "user", "content", prompt)
                        ),
                        "response_format", Map.of("type", "json_object"),
                        "temperature", 0.7
                );

                String response = webClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey)
                        .body(BodyInserters.fromValue(requestBody))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.debug("OpenAI Response for incident {}:\n{}", incident.getId(), response);
                return parseOpenAIResponse(rcaReport, incident, response);

            } catch (Exception e) {
                span.recordException(e);
                log.error("Failed to generate AI RCA report for incident {}: {}", incident.getId(), e.getMessage(), e);
                rcaReport.setStatus(RcaStatus.FAILED);
                rcaReport.setRootCauseSummary("Failed to generate AI RCA report due to internal error: " + e.getMessage());
                rcaReport.setConfidence(BigDecimal.ZERO);
                return rcaReport;
            }
        } finally {
            span.end();
        }
    }

    private String buildPrompt(Incident incident, List<IncidentEvidence> evidences) throws JsonProcessingException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert API reliability engineer. Analyze the provided incident data and evidence to generate a root cause analysis report.\n");
        prompt.append("Focus on the most critical information, such as metrics deltas, schema diffs, and sample errors. The goal is to provide a concise and actionable RCA.\n\n");

        prompt.append("Incident Summary:\n");
        prompt.append("- Incident ID: ").append(incident.getId()).append("\n");
        prompt.append("- Endpoint: ").append(incident.getEndpointId()).append("\n");
        prompt.append("- Type: ").append(incident.getType()).append("\n");
        prompt.append("- Severity: ").append(incident.getSeverity()).append("\n");
        prompt.append("- Detected At: ").append(incident.getDetectedAt()).append("\n");
        prompt.append("- Status: ").append(incident.getStatus()).append("\n\n");

        if (!evidences.isEmpty()) {
            prompt.append("Detailed Evidence:\n");
            for (IncidentEvidence evidence : evidences) {
                prompt.append("--- Evidence Type: ").append(evidence.getEvidenceType())
                        .append(" (Created: ").append(evidence.getCreatedAt()).append(") ---\n");
                if (evidence.getData() != null) {
                    prompt.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence.getData()))
                            .append("\n");
                }
            }
        } else {
            prompt.append("No specific evidence found for this incident.\n");
        }
        prompt.append("\n");

        prompt.append("Based on this information, provide a Root Cause Analysis in the following JSON format:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"root_cause_summary\": \"A concise summary of the root cause.\",\n");
        prompt.append("  \"likely_trigger\": \"The most probable trigger event or condition.\",\n");
        prompt.append("  \"affected_endpoints\": [\"list\", \"of\", \"affected\", \"endpoints\"],\n");
        prompt.append("  \"severity_reason\": \"Explanation of why this incident has the assigned severity.\",\n");
        prompt.append("  \"recommended_fixes\": [\"Ordered list of recommended actions to fix and prevent recurrence\"],\n");
        prompt.append("  \"rollback_vs_patch_recommendation\": \"ROLLBACK\" | \"PATCH\" | \"N/A\",\n");
        prompt.append("  \"confidence\": \"number (0-1, confidence in the analysis)\"\n");
        prompt.append("}\n");
        prompt.append("```\n");
        prompt.append("Ensure the output is valid JSON and strictly adheres to the schema. Do not include any additional text outside the JSON object.");
        return prompt.toString();
    }

    private RcaReport parseOpenAIResponse(RcaReport rcaReport, Incident incident, String response) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(response);
        JsonNode messageNode = rootNode.path("choices").get(0).path("message").path("content");

        if (messageNode.isMissingNode() || !messageNode.isTextual()) {
            throw new RuntimeException("OpenAI response did not contain expected message content.");
        }

        String jsonContent = messageNode.asText();
        if (jsonContent.startsWith("```json") && jsonContent.endsWith("```")) {
            jsonContent = jsonContent.substring(7, jsonContent.length() - 3).trim();
        }

        JsonNode rcaJson = objectMapper.readTree(jsonContent);

        rcaReport.setRootCauseSummary(Optional.ofNullable(rcaJson.get("root_cause_summary")).map(JsonNode::asText).orElse("N/A"));
        rcaReport.setLikelyTrigger(Optional.ofNullable(rcaJson.get("likely_trigger")).map(JsonNode::asText).orElse("N/A"));

        JsonNode affectedEndpointsNode = rcaJson.get("affected_endpoints");
        if (affectedEndpointsNode != null && affectedEndpointsNode.isArray()) {
            List<String> affectedEndpoints = new ArrayList<>();
            for (JsonNode node : affectedEndpointsNode) {
                affectedEndpoints.add(node.asText());
            }
            rcaReport.setAffectedEndpoints(affectedEndpoints);
        } else {
            rcaReport.setAffectedEndpoints(List.of(incident.getEndpointId()));
        }

        rcaReport.setSeverityReason(Optional.ofNullable(rcaJson.get("severity_reason")).map(JsonNode::asText).orElse("N/A"));

        JsonNode recommendedFixesNode = rcaJson.get("recommended_fixes");
        if (recommendedFixesNode != null && recommendedFixesNode.isArray()) {
            List<String> recommendedFixes = new ArrayList<>();
            for (JsonNode node : recommendedFixesNode) {
                recommendedFixes.add(node.asText());
            }
            rcaReport.setRecommendedFixes(recommendedFixes);
        } else {
            rcaReport.setRecommendedFixes(List.of("No recommended fixes provided by AI."));
        }

        rcaReport.setRollbackVsPatchRecommendation(
                Optional.ofNullable(rcaJson.get("rollback_vs_patch_recommendation")).map(JsonNode::asText).orElse("N/A")
        );
        rcaReport.setConfidence(Optional.ofNullable(rcaJson.get("confidence")).map(JsonNode::decimalValue).orElse(BigDecimal.ZERO));

        rcaReport.setStatus(RcaStatus.GENERATED);
        rcaReport.setUpdatedAt(LocalDateTime.now());
        return rcaReport;
    }
}
