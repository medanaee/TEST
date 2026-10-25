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
    private static final double SAFE_DISTANCE_METERS = 40.0;
    private static final double INSIDE_HYSTERESIS_BAND_METERS = 44.0;
    private static final double RELEASE_DISTANCE_METERS = 48.0;
    private static final double FREE_ROAD_METERS = 200.0;
    private static final double HYSTERESIS_BRAKE_PRESSURE = 10.0;
    private static final double MAX_ACCELERATION_STEP_KMH = 5.0;

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

    @Test
    void update_distanceInsideHysteresisBandAfterBraking_keepsBraking() {
        cruise.update(SPEED_KMH, EMERGENCY_DISTANCE_METERS + 1.0);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(Mode.BRAKE, decision.mode());
    }

    @Test
    void update_distanceInsideHysteresisBandAfterBraking_appliesHysteresisPressure() {
        cruise.update(SPEED_KMH, EMERGENCY_DISTANCE_METERS + 1.0);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(HYSTERESIS_BRAKE_PRESSURE, decision.amount(), DELTA);
    }

    @Test
    void update_distanceInsideHysteresisBandAfterEmergencyBraking_keepsBraking() {
        cruise.update(SPEED_KMH, EMERGENCY_DISTANCE_METERS);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(Mode.BRAKE, decision.mode());
    }

    @Test
    void update_distanceBackBelowSafeDistanceAfterBraking_usesProportionalPressure() {
        cruise.update(SPEED_KMH, EMERGENCY_DISTANCE_METERS + 1.0);

        Decision decision = cruise.update(SPEED_KMH, SAFE_DISTANCE_METERS - 10.0);

        assertEquals(25.0, decision.amount(), DELTA);
    }

    @Test
    void update_distanceReachesReleaseDistanceAfterBraking_stopsBrakingAndAccelerates() {
        cruise.update(SPEED_KMH, EMERGENCY_DISTANCE_METERS + 1.0);

        Decision decision = cruise.update(SPEED_KMH, RELEASE_DISTANCE_METERS);

        assertEquals(Mode.ACCELERATE, decision.mode());
    }

    @Test
    void update_afterBrakingIsReleased_laterInBandReadingAccelerates() {
        cruise.update(SPEED_KMH, EMERGENCY_DISTANCE_METERS + 1.0);
        cruise.update(SPEED_KMH, FREE_ROAD_METERS);

        Decision decision = cruise.update(SPEED_KMH, INSIDE_HYSTERESIS_BAND_METERS);

        assertEquals(Mode.ACCELERATE, decision.mode());
    }

    @Test
    void update_farBelowTargetSpeedWithFreeRoad_acceleratesByStepLimitOnly() {
        Decision decision = cruise.update(TARGET_SPEED_KMH - 50.0, FREE_ROAD_METERS);

        assertEquals(MAX_ACCELERATION_STEP_KMH, decision.amount(), DELTA);
    }

    @Test
    void update_negativeSpeed_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(-1.0, FREE_ROAD_METERS));
    }

    @Test
    void update_nanSpeed_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(Double.NaN, FREE_ROAD_METERS));
    }

    @Test
    void update_negativeDistance_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> cruise.update(SPEED_KMH, -1.0));
    }

    @Test
    void update_infiniteDistance_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> cruise.update(SPEED_KMH, Double.POSITIVE_INFINITY));
    }
}
