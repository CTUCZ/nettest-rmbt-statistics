package at.rtr.rmbt.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.StringJoiner;

import lombok.Data;
import lombok.Setter;

/**
 * @author David Furmanek
 */
@Data
public class SignalDTO {

    @JsonProperty("time")
    private String time;
    @JsonProperty("location_id")
    private Integer locationId;
    @JsonProperty("area_code")
    private Integer areaCode;
    @JsonProperty("primary_scrambling_code")
    private Integer primaryScramblingCode;
    @JsonProperty("channel_number")
    private Integer channelNumber;
    @JsonProperty("lte_rsrp")
    private Integer lteRsrp;
    @JsonProperty("lte_rsrq")
    private Integer lterRsrq;
    @JsonProperty("signal_strength")
    private Integer signalStrength;
    @JsonProperty("timing_advance")
    private Integer timingAdvance;
    @JsonProperty("network_type")
    private String networkType;
    @JsonProperty("network_technology")
    private String networkTechnology;

    @JsonProperty("conditioned_signal_strength")
    public Integer getConditionedSignalStrength() {
        if("4G".equals(networkTechnology)) {
            return lteRsrp;
        } else {
            return signalStrength;
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SignalDTO.class.getSimpleName() + "[", "]")
                .add("time='" + time + "'")
                .add("locationId=" + locationId)
                .add("areaCode=" + areaCode)
                .add("primaryScramblingCode=" + primaryScramblingCode)
                .add("channelNumber=" + channelNumber)
                .add("lteRsrp=" + lteRsrp)
                .add("lterRsrq=" + lterRsrq)
                .add("timingAdvance=" + timingAdvance)
                .add("networkType='" + networkType + "'")
                .add("networkTechnology='" + networkTechnology + "'")
                .toString();
    }
}
