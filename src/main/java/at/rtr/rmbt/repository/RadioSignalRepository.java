package at.rtr.rmbt.repository;

import at.rtr.rmbt.response.SignalDTO;
import at.rtr.rmbt.response.SignalGraphItemDTO;
import at.rtr.rmbt.response.SignalValidationRuleDTO;

import java.util.List;
import java.util.UUID;

public interface RadioSignalRepository {

    List<SignalGraphItemDTO> getSignals(UUID openTestUuid, long time);

    List<SignalGraphItemDTO> getSignalsLegacy(UUID openTestUuid, long time);

    List<SignalDTO> getSignalData(UUID openTestUuid);

    List<SignalValidationRuleDTO> getSignalValidationRules();
}
