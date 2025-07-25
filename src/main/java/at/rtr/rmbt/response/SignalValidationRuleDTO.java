package at.rtr.rmbt.response;

import lombok.Data;

/**
 * Class representing signal validation rule
 *
 * @author David Furmanek
 */
@Data
public class SignalValidationRuleDTO {

    private Integer band;
    private Integer channelFrom;
    private Integer channelTo;
    private Integer rsrpLimit;
}
