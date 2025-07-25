package at.rtr.rmbt.service.export.validator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import at.rtr.rmbt.response.OpenTestDetailsDTO;
import at.rtr.rmbt.response.SignalDTO;
import at.rtr.rmbt.response.SignalValidationRuleDTO;
import at.rtr.rmbt.response.opentest.OpenTestDTO;
import lombok.Getter;

public class MobileCertifiedMeasurementValidator {

    private static final Integer NUM_OF_TESTS = 6;
    private static final Integer MAX_DISTANCE_METERS = 1;
    private static final Long NOT_ROAMING_TYPE = 5L;//0 -> not roaming
    private static final Integer MAX_TEST_TIME = 60; // minutes
    private static final String VALID_MCC = "230"; // cz

    private static final List<String> NO_VALIDATION_SIGNAL_TECHNOLOGIES = Arrays.asList("3G", "WIFI");

    private final List<OpenTestDetailsDTO> testDetails;

    private final List<SignalValidationRuleDTO> signalRules;

    @Getter
    private final Map<String, Object> validationResults = new HashMap<>();

    private final Logger logger = Logger.getLogger(MobileCertifiedMeasurementValidator.class.getName());

    public MobileCertifiedMeasurementValidator(List<OpenTestDTO> testResults, List<SignalValidationRuleDTO> signalRules) {
        this.testDetails = testResults.stream()
                .filter(t -> "FINISHED".equals(t.getStatus()))
                .map(OpenTestDetailsDTO.class::cast)
                .collect(Collectors.toList());
        this.signalRules = signalRules;
    }

    public void validate() {
        if(testDetails.isEmpty()) {
            validateNumberOfMeasurements();
            logger.info(() -> "Test results empty");
        } else {
            validateNumberOfMeasurements();
            validateMeasurementsTime();
            validateNetworkType();
            validateSignal();
            validateLocationExists();
            if(validationResults.get("invalidNetwork") == null) {
                validateMcc();
                validateSimEqualsOperator();
                validateTestsInSameNetwork();
            }
            logger.info(() -> "Validation complete");
        }
        logger.info(() -> "Validation results: "+ validationResults.toString());
    }

    private void validateDistance() {
        logger.info(() -> "Validating distance");
        OpenTestDetailsDTO firstTest = testDetails.get(0);
        Double startLat = firstTest.getLatitude();
        Double startLong = firstTest.getLongitude();
        //todo zakomponovat přesnost, nějaky limit


        if(startLat == null || startLong == null) {
            return;
        }

        validationResults.put("validDistanceLocSrc", firstTest.getLocSrc());// gps/network
        List<DistanceResult> distanceResult = new ArrayList<>();
        for (int i = 1; i < testDetails.size(); i++) {
            OpenTestDetailsDTO test = testDetails.get(i);
            Double testLat = test.getLatitude();
            Double testLong = test.getLongitude();

            if(testLat != null && testLong != null) {
                double distance = distFrom(startLat, startLong, testLat, testLong);
                if (distance > MAX_DISTANCE_METERS) {
                    distanceResult.add(new DistanceResult(i + 1, Math.round(distance), test.getLocSrc(), test.getLocAccuracy()));
                }
            } else {
                distanceResult.add(new DistanceResult(i + 1, Long.MAX_VALUE, test.getLocSrc(), test.getLocAccuracy()));
            }
        }

        if(!distanceResult.isEmpty()) {
            validationResults.put("maxValidDistance", MAX_DISTANCE_METERS);
            validationResults.put("invalidDistance", distanceResult.stream()
                    .map(DistanceResult::getMap)
                    .collect(Collectors.toList())
            );
        }
    }

    private void validateNumberOfMeasurements() {
        logger.info(() -> "Validating number of tests");
        if(NUM_OF_TESTS != testDetails.size()) {
            validationResults.put("invalidNumOfTests", true);
            validationResults.put("numOfFinishedTests", testDetails.size());
        }
    }

    private void validateMeasurementsTime() {
        logger.info(() -> "Validating measurements time");

        OpenTestDetailsDTO firstTest = testDetails.get(0);
        OpenTestDetailsDTO lastTest = testDetails.get(testDetails.size() - 1);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime firstTestTime = timeFormatter.parse(firstTest.getTime(), LocalDateTime::from);
        LocalDateTime lastTestTime = timeFormatter.parse(lastTest.getTime(), LocalDateTime::from);

        Double firstUploadStart = firstTest.getTimeUlMs();
        Double firstUploadDuration = lastTest.getDurationUploadMs();

        Double lastUploadStart = lastTest.getTimeUlMs();
        Double lastUploadDuration = lastTest.getDurationDownloadMs();

        // todo ověřit že změněný kod funguje jako předtím (bylo potřeba se zbavit jodaTime)
        final var adjustedFirstTestTime = firstTestTime.plus((long) (firstUploadStart + firstUploadDuration), ChronoUnit.MILLIS);
        final var adjustedLastTestTime = lastTestTime.plus((long) (lastUploadStart + lastUploadDuration), ChronoUnit.MILLIS);

        final var testDuration = Duration.between(adjustedFirstTestTime, adjustedLastTestTime);
        if(testDuration.toMinutes() > MAX_TEST_TIME) {
            validationResults.put("invalidTime", testDuration.toMinutes());
        }
    }

    private void validateNetworkType() {
        logger.info(() -> "Validating network type");
        AtomicInteger testNum = new AtomicInteger(1);
        List<NetworkTypeResult> invalidTests = testDetails.stream()
                .map(test -> new NetworkTypeResult(testNum.getAndIncrement(), test.getNetworkType()))
                .filter(entry -> !"MOBILE".equals(entry.getNetworkType()))
                .collect(Collectors.toList());

        if(!invalidTests.isEmpty()) {
            validationResults.put("invalidNetwork", invalidTests);
        }
    }

    private void validateRoaming() {
        logger.info(() -> "Validating roaming");
        AtomicInteger i = new AtomicInteger(1);
        List<HashMap<Object, Object>> roaming = testDetails.stream()
                .filter(t -> !Objects.equals(t.getRoamingType(), NOT_ROAMING_TYPE))
                .map(t -> {
                    HashMap<Object, Object> map = new HashMap<>();
                    map.put("testId", i.getAndIncrement());
                    map.put("roamingType", translateRoaming(t.getRoamingType()));
                    map.put("networkType", t.getNetworkType());
                    return map;
                })
                .collect(Collectors.toList());

        validationResults.put("invalidRoaming", roaming);
    }

    private void validateMcc() {
        logger.info(() -> "Validating mcc");
        AtomicInteger i = new AtomicInteger(1);
        List<MccResult> invalidTests = testDetails.stream()
                .map(test -> new MccResult(i.getAndIncrement(), test.getNetworkMccMnc()))
                .filter(test -> !test.mcc.startsWith(VALID_MCC))
                .collect(Collectors.toList());

        if(!invalidTests.isEmpty())
            validationResults.put("invalidMcc", invalidTests);
    }

    private void validateSimEqualsOperator() {
        logger.info(() -> "Validating sim/operator");
        AtomicInteger i = new AtomicInteger(1);
        List<SimOperatorResult> invalidTests = testDetails.stream()
                .map(test -> new SimOperatorResult(i.getAndIncrement(), test.getNetworkMccMnc(), test.getSimMccMnc()))
                .filter(test -> !Objects.equals(test.networkMccMnc, test.simMccMnc))
                .collect(Collectors.toList());

        if(!invalidTests.isEmpty()) {
            validationResults.put("invalidSimOperator", invalidTests);
        }
    }

    private void validateTestsInSameNetwork() {
        logger.info(() -> "Validating same network");
        String network = testDetails.stream().findFirst().map(OpenTestDetailsDTO::getNetworkMccMnc).orElse("");
        List<OpenTestDetailsDTO> invalidTests = testDetails.stream()
                .filter(test -> !network.equals(test.getNetworkMccMnc()))
                .collect(Collectors.toList());

        if(!invalidTests.isEmpty()) {
            validationResults.put("invalidSameNetwork", invalidTests);
        }
    }

    private void validateLocationExists() {
        logger.info(() -> "Validating location exists");
        AtomicInteger i = new AtomicInteger(1);
        List<Integer> invalidTests = testDetails.stream()
                .map(test -> {
                    int testNum = i.getAndIncrement();
                    if (test.getLatitude() == null || test.getLongitude() == null) {
                        return testNum;
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if(!invalidTests.isEmpty()) {
            validationResults.put("invalidLocation", invalidTests);
        }
    }

    private void validateSignal() {
        logger.info(() -> "Validating signal");
        AtomicReference<Integer> testId = new AtomicReference<>(1);
        List<HashMap<String, Object>> invalidList = testDetails.stream()
                .map(t -> {
                    Integer currentTestId = testId.getAndSet(testId.get() + 1);
                    List<SignalResult> invalidSignals = validateSignalList(t.getSignalList());
                    List<String> invalidSignalsMessages = invalidSignals.stream()
                            .map(SignalResult::getMessage)
                            .distinct()
                            .collect(Collectors.toList());

                    HashMap<String, Object> map = new HashMap<>();
                    if (!invalidSignals.isEmpty()) {
                        map.put("testId", currentTestId);
                        map.put("messages", invalidSignalsMessages);
                    }
                    return map;
                })
                .filter(map -> !map.isEmpty())
                .collect(Collectors.toList());
        if(!invalidList.isEmpty()) {
            validationResults.put("invalidSignal", invalidList);
        }
    }

    private List<SignalResult> validateSignalList(List<SignalDTO> signalList) {
        return signalList.stream()
                .filter(signal -> !NO_VALIDATION_SIGNAL_TECHNOLOGIES.contains(signal.getNetworkTechnology()))
                .map(this::validateSignalLimit)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SignalResult validateSignalLimit(SignalDTO signal) {
        Integer channelNumber = signal.getChannelNumber();

        SignalValidationRuleDTO rule = signalRules.stream()
                .filter(r -> channelNumber >= r.getChannelFrom() && channelNumber <= r.getChannelTo())
                .findFirst()
                .orElse(null);

        if(rule == null) {
            return new SignalResult(signal, String.format("Nebylo možné ověřit limitní hodnotu síly signálu (Channel number %s)", channelNumber));
        }

        if(signal.getLteRsrp() == null) {
            return new SignalResult(signal, "Nebylo možné získat všechny hodnoty síly signálu");
        }

        if(signal.getLteRsrp() < rule.getRsrpLimit()) {
            return new SignalResult(signal, String.format("Byla detekována nedostatečná síla signálu, která mohla ovlivnit výsledek měření (Channel number %s)", channelNumber));
        }

        return null;
    }

    private static double distFrom(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000; //meters
        double dLat = Math.toRadians(lat2-lat1);
        double dLng = Math.toRadians(lng2-lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return earthRadius * c;
    }

    private static String translateRoaming(Long roamingType) {
        if(roamingType == null) {
            return "neznámý";
        }
        if (roamingType == 0) {
            return "není roaming";
        } else if (roamingType == 1) {
            return "vnitrostátní";
        } else if (roamingType == 2) {
            return "mezinárodní";
        }
        return "neznámý";
    }

    public static class SignalResult {
        public final SignalDTO signal;
        @Getter
        public final String message;

        public SignalResult(SignalDTO signal, String message) {
            this.signal = signal;
            this.message = message;
        }
    }

    public static class DistanceResult {
        public final Integer testId;
        public final Long distance;
        public final String locSrc;
        public final Double locAccuracy;

        public DistanceResult(Integer testId, Long distance, String locSrc, Double locAccuracy) {
            this.testId = testId;
            this.distance = distance;
            this.locSrc = locSrc;
            this.locAccuracy = locAccuracy;
        }

        public Map<String, String> getMap() {
            Map<String, String> map = new HashMap<>();
            map.put("testId", getSafeNumberString(testId));
            map.put("distance", getSafeNumberString(distance));
            map.put("locSrc", locSrc);
            map.put("locAccuracy", getSafeNumberString(locAccuracy));
            return map;
        }

        private static String getSafeNumberString(Number number) {
            return Optional.ofNullable(number)
                    .map(Number::toString)
                    .orElse("");
        }
    }

    @Getter
    public static class NetworkTypeResult {
        private final Integer testNum;
        private final String networkType;

        public NetworkTypeResult(Integer testNum, String networkType) {
            this.testNum = testNum;
            this.networkType = networkType;
        }
    }

    @Getter
    public static class MccResult {
        private final Integer testNum;
        private final String mcc;

        public MccResult(Integer testNum, String mcc) {
            this.testNum = testNum;
            this.mcc = Objects.requireNonNullElse(mcc, "");
        }
    }

    @Getter
    public static class SimOperatorResult {
        private final Integer testNum;
        private final String networkMccMnc;
        private final String simMccMnc;

        public SimOperatorResult(Integer testNum, String networkMccMnc, String simMccMnc) {
            this.testNum = testNum;
            this.networkMccMnc = networkMccMnc;
            this.simMccMnc = simMccMnc;
        }
    }
}
