package com.medicology.assessment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "assessment")
public class AssessmentProperties {

    private String apiLocalServerUrl;
    private String apiProductionServerUrl;
    private String apiTitle;
    private String apiDescription;
    private boolean learningSyncEnabled;
    private String learningContractPath;
    /** Base URL of learning service (e.g. http://localhost:8081) */
    private String learningServiceBaseUrl = "http://localhost:8081";
    /** Shared secret for internal learning ↔ assessment calls */
    private String learningInternalToken = "";
    /** When true, discovery/start require enrollment in learning service */
    private boolean enrollmentCheckEnabled = false;
    /** AI provider name used for short-answer grading (placeholder). */
    private String aiProvider = "google-ai-studio";
    /** AI model used for short-answer grading (placeholder). */
    private String aiModel = "gemini-2.5-flash";
    /** AI API key placeholder. */
    private String aiApiKey = "";
    /** AI endpoint placeholder for Gemini/OpenAI-compatible bridge. */
    private String aiEndpoint = "";
    /** Enable Gemini web grounding via Google Search tool. */
    private boolean aiGroundingEnabled = true;
    /** Min confidence to auto-finalize AI short-answer grading. */
    private double aiConfidenceThreshold = 0.80d;
}
