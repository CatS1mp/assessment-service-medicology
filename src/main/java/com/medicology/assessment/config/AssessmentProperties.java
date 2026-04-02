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
}
