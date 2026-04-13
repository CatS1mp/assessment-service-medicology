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
}
