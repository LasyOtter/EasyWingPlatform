package com.easywing.platform.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {

    private String id;

    private String provider;

    private String name;

    private String version;

    private String description;

    private int contextWindow;

    private int maxOutputTokens;

    private boolean supportsStreaming;

    private boolean supportsTools;

    private boolean supportsVision;

    private boolean supportsThinking;

    private double inputCostPer1MTokens;

    private double outputCostPer1MTokens;

    private String pricingUnit;
}