package com.instakill.analytics.domain;

import java.util.UUID;

public interface AnalyticsRepository {
    UserAnalytics getUserAnalytics(UUID userId);
}
