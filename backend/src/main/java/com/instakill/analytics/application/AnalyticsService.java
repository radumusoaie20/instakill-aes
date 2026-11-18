package com.instakill.analytics.application;

import com.instakill.analytics.domain.AnalyticsRepository;
import com.instakill.analytics.domain.UserAnalytics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    public UserAnalytics getUserAnalytics(UUID userId) {
        return analyticsRepository.getUserAnalytics(userId);
    }
}
