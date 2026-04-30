package com.wayt.service;

import com.wayt.domain.EtaRefreshPolicy;
import com.wayt.domain.Participant;
import com.wayt.domain.SubscriptionTier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class EtaPolicyService {
    private static final Duration TRAVEL_MODE_CHANGE_COOLDOWN = Duration.ofMinutes(5);

    public boolean shouldCallEtaApi(Participant participant, boolean travelModeChanged, boolean nearLateBoundary) {
        return shouldCallEtaApi(participant, travelModeChanged, nearLateBoundary, OffsetDateTime.now());
    }

    public boolean shouldCallEtaApi(Participant participant, boolean travelModeChanged, boolean nearLateBoundary, OffsetDateTime now) {
        if (travelModeChanged) {
            OffsetDateTime calculatedAt = participant.getEtaCalculatedAt();
            if (calculatedAt != null && now.isBefore(calculatedAt.plus(TRAVEL_MODE_CHANGE_COOLDOWN))) {
                return false;
            }
            if (participant.getUserAccount().getSubscriptionTier() == SubscriptionTier.FREE) {
                return participant.getEtaApiCallCount() < 2;
            }
        }

        SubscriptionTier tier = participant.getUserAccount().getSubscriptionTier();
        if (tier == SubscriptionTier.PLUS || tier == SubscriptionTier.PRO) {
            OffsetDateTime nextEligibleAt = participant.getEtaNextEligibleAt();
            return nextEligibleAt == null || !now.isBefore(nextEligibleAt);
        }

        return participant.getEtaApiCallCount() == 0 || (nearLateBoundary && participant.getEtaApiCallCount() < 2);
    }

    public EtaRefreshPolicy policyFor(Participant participant) {
        return switch (participant.getUserAccount().getSubscriptionTier()) {
            case PLUS -> EtaRefreshPolicy.PLUS_EVERY_10_MINUTES;
            case PRO -> EtaRefreshPolicy.PRO_EVERY_3_MINUTES;
            case FREE -> EtaRefreshPolicy.FREE_ONE_TIME_WITH_EDGE_CHECK;
        };
    }

    public OffsetDateTime nextEligibleAt(Participant participant, OffsetDateTime calculatedAt) {
        return switch (participant.getUserAccount().getSubscriptionTier()) {
            case PLUS -> calculatedAt.plusMinutes(10);
            case PRO -> calculatedAt.plusMinutes(3);
            case FREE -> null;
        };
    }
}
