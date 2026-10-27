package autopilot.core;

import autopilot.core.AdaptiveCruise.Decision;
import autopilot.core.AdaptiveCruise.Mode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Naming convention: methodName_stateUnderTest_expectedBehavior
class AdaptiveCruiseTest {

    private static final double DELTA = 1e-9;
    private static final double TARGET_SPEED_KMH = 100.0;
    // At 80 km/h: safe distance 40 m, emergency distance 20 m, release distance 48 m.
    private static final double SPEED_KMH = 80.0;
    private static final double EMERGENCY_DISTANCE_METERS = 20.0;
    // Between the emergency and safe distances, so a first reading here starts ordinary braking.
    private static final double BRAKING_DISTANCE_METERS = 30.0;
    private static final double INSIDE_HYSTERESIS_BAND_METERS = 44.0;
    private static final double FREE_ROAD_METERS = 200.0;

    private AdaptiveCruise cruise;

    @BeforeEach
    void setUp() {
        cruise = new AdaptiveCruise(TARGET_SPEED_KMH);
    }

    @Test
    void safeDistanceFor_speed120_returns60Meters() {
        assertEquals(60.0, cruise.safeDistanceFor(120.0), DELTA);
    }

    @Test
    void safeDistanceFor_standstill_returnsZero() {
        assertEquals(0.0, cruise.safeDistanceFor(0.0), DELTA);
    }

    @ParameterizedTest(name = "speed {0} km/h")
    @ValueSource(doubles = {-0.1, -50.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void safeDistanceFor_invalidSpeed_throwsIllegalArgument(double invalidSpeedKmh) {
        assertThrows(IllegalArgumentException.class, () -> cruise.safeDistanceFor(invalidSpeedKmh));
    }

    @ParameterizedTest(name = "{0} km/h, {1} m -> {2} {3}")
    @CsvFileSource(resources = "/adaptive-cruise-decisions.csv", numLinesToSkip = 1)
    void update_firstReading_returnsExpectedDecision(
            double speedKmh, double distanceMeters, Mode expectedMode, double expectedAmount) {
        Decision decision = cruise.update(speedKmh, distanceMeters);

        assertEquals(expectedMode, decision.mode());
        assertEquals(expectedAmount, decision.amount(), DELTA);
    }

    @Test
    void update_distanceInsideHysteresisBandWithoutPriorBraking_accelerates() {
        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(Mode.ACCELERATE, decision.mode());
    }

    @ParameterizedTest(name = "braking, then {0} m -> {1} {2}")
    @CsvFileSource(resources = "/adaptive-cruise-decisions-after-braking.csv", numLinesToSkip = 1)
    void update_readingAfterBraking_returnsExpectedDecision(
            double distanceMeters, Mode expectedMode, double expectedAmount) {
        cruise.update(SPEED_KMH, BRAKING_DISTANCE_METERS);

        Decision decision = cruise.update(SPEED_KMH, distanceMeters);

        assertEquals(expectedMode, decision.mode());
        assertEquals(expectedAmount, decision.amount(), DELTA);
    }

    @Test
    void update_distanceInsideHysteresisBandAfterEmergencyBraking_keepsBraking() {
        cruise.update(SPEED_KMH, EMERGENCY_DISTANCE_METERS);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(Mode.BRAKE, decision.mode());
    }

    @Test
    void update_afterBrakingIsReleased_laterInBandReadingAccelerates() {
        cruise.update(SPEED_KMH, BRAKING_DISTANCE_METERS);
        cruise.update(SPEED_KMH, FREE_ROAD_METERS);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(Mode.ACCELERATE, decision.mode());
    }

    @ParameterizedTest(name = "{0} km/h, {1} m")
    @CsvFileSource(resources = "/adaptive-cruise-invalid-readings.csv", numLinesToSkip = 1)
    void update_invalidSpeedOrDistance_throwsIllegalArgument(double speedKmh, double distanceMeters) {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(speedKmh, distanceMeters));
    }
}
