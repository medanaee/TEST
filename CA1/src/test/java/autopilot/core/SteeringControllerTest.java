package autopilot.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

// Naming convention: methodName_stateUnderTest_expectedBehavior
class SteeringControllerTest {

    private static final double DELTA = 1e-9;
    private static final double STANDSTILL_KMH = 0.0;
    private static final double CITY_SPEED_KMH = 50.0;
    private static final double HIGH_SPEED_THRESHOLD_KMH = 120.0;
    private static final double HIGHWAY_SPEED_KMH = 130.0;
    private static final double MAX_STEP_DEGREES = 60.0;
    private static final int TICKS_TO_SETTLE = 20;
    // Request sizes: reachable in one step, needing several steps, and wider than the 90 degree high-speed limit.
    private static final double SMALL_REQUEST_DEGREES = 30.0;
    private static final double LARGE_REQUEST_DEGREES = 200.0;
    private static final double WIDE_TURN_DEGREES = 300.0;

    private SteeringController steering;

    @BeforeEach
    void setUp() {
        steering = new SteeringController();
    }

    @ParameterizedTest(name = "request {0} -> first tick {1}")
    @MethodSource("requestedAngleAndFirstTickAngle")
    void update_requestFromCenteredWheel_movesAtMostOneStep(double requestedAngle, double expectedAngle) {
        double angle = steering.update(requestedAngle, CITY_SPEED_KMH);

        assertEquals(expectedAngle, angle, DELTA);
    }

    static Stream<Arguments> requestedAngleAndFirstTickAngle() {
        return Stream.of(
                arguments(30.0, 30.0),
                arguments(-30.0, -30.0),
                arguments(60.0, 60.0),
                arguments(-60.0, -60.0),
                arguments(61.0, 60.0),
                arguments(-61.0, -60.0),
                arguments(200.0, 60.0),
                arguments(-200.0, -60.0)
        );
    }

    @Test
    void update_sameLargeRequestTwice_movesTwoSteps() {
        steering.update(LARGE_REQUEST_DEGREES, CITY_SPEED_KMH);

        double angle = steering.update(LARGE_REQUEST_DEGREES, CITY_SPEED_KMH);

        assertEquals(2 * MAX_STEP_DEGREES, angle, DELTA);
    }

    @Test
    void update_requestReversesDirection_movesOneStepTowardNewTarget() {
        steering.update(MAX_STEP_DEGREES, CITY_SPEED_KMH);

        double angle = steering.update(-LARGE_REQUEST_DEGREES, CITY_SPEED_KMH);

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
                arguments(450.0, STANDSTILL_KMH, 450.0),
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
        turnWheelTo(WIDE_TURN_DEGREES);

        double angle = steering.update(WIDE_TURN_DEGREES, HIGHWAY_SPEED_KMH);

        assertEquals(WIDE_TURN_DEGREES - MAX_STEP_DEGREES, angle, DELTA);
    }

    @ParameterizedTest(name = "request {0} at {1} km/h")
    @MethodSource("invalidAngleOrSpeed")
    void update_invalidAngleOrSpeed_throwsIllegalArgument(double requestedAngle, double speedKmh) {
        assertThrows(IllegalArgumentException.class, () -> steering.update(requestedAngle, speedKmh));
    }

    static Stream<Arguments> invalidAngleOrSpeed() {
        return Stream.of(
                arguments(Double.NaN, CITY_SPEED_KMH),
                arguments(Double.POSITIVE_INFINITY, CITY_SPEED_KMH),
                arguments(Double.NEGATIVE_INFINITY, CITY_SPEED_KMH),
                arguments(SMALL_REQUEST_DEGREES, -0.1),
                arguments(SMALL_REQUEST_DEGREES, Double.NaN),
                arguments(SMALL_REQUEST_DEGREES, Double.POSITIVE_INFINITY),
                arguments(SMALL_REQUEST_DEGREES, Double.NEGATIVE_INFINITY)
        );
    }

    @Test
    void update_invalidInput_keepsCurrentAngle() {
        steering.update(SMALL_REQUEST_DEGREES, CITY_SPEED_KMH);

        assertThrows(IllegalArgumentException.class, () -> steering.update(Double.NaN, CITY_SPEED_KMH));

        assertEquals(SMALL_REQUEST_DEGREES, steering.getCurrentAngleDegrees(), DELTA);
    }

    @ParameterizedTest(name = "wheel at {0} -> {1} after one tick")
    @MethodSource("startAngleAndAngleAfterOneTick")
    void selfCenter_wheelAtStartAngle_movesAtMostOneStepTowardZero(
            double startAngle, double expectedAngle) {
        turnWheelTo(startAngle);

        double angle = steering.selfCenter();

        assertEquals(expectedAngle, angle, DELTA);
    }

    static Stream<Arguments> startAngleAndAngleAfterOneTick() {
        return Stream.of(
                arguments(0.0, 0.0),
                arguments(45.0, 0.0),
                arguments(-45.0, 0.0),
                arguments(60.0, 0.0),
                arguments(-60.0, 0.0),
                arguments(61.0, 1.0),
                arguments(-61.0, -1.0),
                arguments(200.0, 140.0),
                arguments(-200.0, -140.0)
        );
    }

    // Holds the request at city speed until the wheel has settled on the requested angle.
    private void turnWheelTo(double angleDegrees) {
        for (int tick = 0; tick < TICKS_TO_SETTLE; tick++) {
            steering.update(angleDegrees, CITY_SPEED_KMH);
        }
    }
}
