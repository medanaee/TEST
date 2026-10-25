package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// Naming convention: methodName_stateUnderTest_expectedBehavior
class SteeringControllerTest {

    private static final double DELTA = 1e-9;
    private static final double CITY_SPEED_KMH = 50.0;
    private static final double HIGH_SPEED_THRESHOLD_KMH = 120.0;
    private static final double HIGHWAY_SPEED_KMH = 130.0;
    private static final double MAX_STEP_DEGREES = 60.0;
    private static final int TICKS_TO_SETTLE = 20;

    private SteeringController steering;

    @BeforeEach
    void setUp() {
        steering = new SteeringController();
    }

    @Test
    void update_requestWithinOneStep_reachesRequestedAngleImmediately() {
        double angle = steering.update(30.0, CITY_SPEED_KMH);

        assertEquals(30.0, angle, DELTA);
    }

    @Test
    void update_requestExactlyOneStep_reachesRequestedAngleImmediately() {
        double angle = steering.update(MAX_STEP_DEGREES, CITY_SPEED_KMH);

        assertEquals(MAX_STEP_DEGREES, angle, DELTA);
    }

    @ParameterizedTest(name = "request {0} -> first tick {1}")
    @ValueSource(doubles = {61.0, 200.0, 450.0})
    void update_positiveRequestBeyondOneStep_movesByOneStepOnly(double requestedAngle) {
        double angle = steering.update(requestedAngle, CITY_SPEED_KMH);

        assertEquals(MAX_STEP_DEGREES, angle, DELTA);
    }

    @Test
    void update_negativeRequestBeyondOneStep_movesByOneStepOnly() {
        double angle = steering.update(-200.0, CITY_SPEED_KMH);

        assertEquals(-MAX_STEP_DEGREES, angle, DELTA);
    }

    @Test
    void update_sameLargeRequestTwice_movesTwoSteps() {
        steering.update(200.0, CITY_SPEED_KMH);

        double angle = steering.update(200.0, CITY_SPEED_KMH);

        assertEquals(2 * MAX_STEP_DEGREES, angle, DELTA);
    }

    @Test
    void update_requestReversesDirection_movesOneStepTowardNewTarget() {
        steering.update(MAX_STEP_DEGREES, CITY_SPEED_KMH);

        double angle = steering.update(-200.0, CITY_SPEED_KMH);

        assertEquals(0.0, angle, DELTA);
    }

    @ParameterizedTest(name = "request {0} at {1} km/h settles at {2}")
    @MethodSource("requestedAngleAndSettledAngle")
    void update_requestHeldUntilSettled_endsWithinSteeringLimit(
            double requestedAngle, double speedKmh, double expectedSettledAngle) {
        double angle = 0.0;
        for (int tick = 0; tick < TICKS_TO_SETTLE; tick++) {
            angle = steering.update(requestedAngle, speedKmh);
        }

        assertEquals(expectedSettledAngle, angle, DELTA);
    }

    static Stream<Arguments> requestedAngleAndSettledAngle() {
        return Stream.of(
                arguments(450.0, CITY_SPEED_KMH, 450.0),
                arguments(451.0, CITY_SPEED_KMH, 450.0),
                arguments(10_000.0, CITY_SPEED_KMH, 450.0),
                arguments(-450.0, CITY_SPEED_KMH, -450.0),
                arguments(-451.0, CITY_SPEED_KMH, -450.0),
                arguments(-10_000.0, CITY_SPEED_KMH, -450.0),
                arguments(300.0, HIGH_SPEED_THRESHOLD_KMH, 300.0),
                arguments(300.0, HIGH_SPEED_THRESHOLD_KMH + 0.1, 90.0),
                arguments(300.0, HIGHWAY_SPEED_KMH, 90.0),
                arguments(-300.0, HIGHWAY_SPEED_KMH, -90.0),
                arguments(90.0, HIGHWAY_SPEED_KMH, 90.0),
                arguments(45.0, HIGHWAY_SPEED_KMH, 45.0)
        );
    }

    @Test
    void update_speedRisesAboveThresholdWhileTurnedWide_returnsTowardHighSpeedLimitStepByStep() {
        for (int tick = 0; tick < 5; tick++) {
            steering.update(300.0, CITY_SPEED_KMH);
        }

        double angle = steering.update(300.0, HIGHWAY_SPEED_KMH);

        assertEquals(300.0 - MAX_STEP_DEGREES, angle, DELTA);
    }

    @ParameterizedTest(name = "speed {0} km/h")
    @ValueSource(doubles = {-0.1, -50.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void update_invalidSpeed_throwsIllegalArgument(double invalidSpeedKmh) {
        assertThrows(IllegalArgumentException.class, () -> steering.update(10.0, invalidSpeedKmh));
    }

    @Test
    void update_nanAngle_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> steering.update(Double.NaN, CITY_SPEED_KMH));
    }

    @Test
    void update_infiniteAngle_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> steering.update(Double.POSITIVE_INFINITY, CITY_SPEED_KMH));
    }

    @Test
    void update_invalidInput_keepsCurrentAngle() {
        steering.update(30.0, CITY_SPEED_KMH);

        assertThrows(IllegalArgumentException.class, () -> steering.update(Double.NaN, CITY_SPEED_KMH));

        assertEquals(30.0, steering.getCurrentAngleDegrees(), DELTA);
    }

    @Test
    void selfCenter_wheelAlreadyCentered_staysAtZero() {
        double angle = steering.selfCenter();

        assertEquals(0.0, angle, DELTA);
    }

    @Test
    void selfCenter_angleWithinOneStep_returnsToZeroImmediately() {
        steering.update(45.0, CITY_SPEED_KMH);

        double angle = steering.selfCenter();

        assertEquals(0.0, angle, DELTA);
    }

    @Test
    void selfCenter_angleBeyondOneStep_movesOneStepTowardZero() {
        for (int tick = 0; tick < 4; tick++) {
            steering.update(200.0, CITY_SPEED_KMH);
        }

        double angle = steering.selfCenter();

        assertEquals(200.0 - MAX_STEP_DEGREES, angle, DELTA);
    }

    @Test
    void selfCenter_negativeAngleBeyondOneStep_movesOneStepTowardZero() {
        steering.update(-MAX_STEP_DEGREES, CITY_SPEED_KMH);
        steering.update(-200.0, CITY_SPEED_KMH);

        double angle = steering.selfCenter();

        assertEquals(-MAX_STEP_DEGREES, angle, DELTA);
    }
}
