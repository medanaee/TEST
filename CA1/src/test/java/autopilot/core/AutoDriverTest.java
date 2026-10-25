package autopilot.core;

import autopilot.core.AutoDriver.DriveResult;
import autopilot.core.GearBox.Gear;
import autopilot.exception.SensorFailureException;
import autopilot.sensor.LaneSensor;
import autopilot.sensor.ObstacleSensor;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Naming convention: methodName_stateUnderTest_expectedBehavior
class AutoDriverTest {

    private static final double DELTA = 1e-9;

    // Cruising exactly at the target speed with a free road ahead: the cruise controller holds speed.
    private static final double TARGET_SPEED_KMH = 80.0;
    private static final double FREE_ROAD_METERS = 100.0;
    private static final double CENTERED_LANE_OFFSET_METERS = 0.0;

    private static final double NO_STEERING_DEGREES = 0.0;
    private static final double NO_BRAKE_PRESSURE = 0.0;
    private static final double SAFE_MODE_BRAKE_PRESSURE = 30.0;
    private static final String SENSOR_FAILURE_MESSAGE = "sensor failed";

    private enum FailingSensor { SPEED, OBSTACLE, LANE }

    @Test
    void tick_healthySensorsAndCenteredLane_returnsNormalDrivingResult() {
        AutoDriver driver = driverWith(TARGET_SPEED_KMH, FREE_ROAD_METERS, CENTERED_LANE_OFFSET_METERS);

        DriveResult result = driver.tick(Gear.D);

        DriveResult expected = new DriveResult(
                NO_STEERING_DEGREES, NO_BRAKE_PRESSURE, AdaptiveCruise.Mode.HOLD, false, false);
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "lane offset {0} m -> steering {1} degrees")
    @CsvSource({
            "0.3,   0.0",
            "0.5,   0.0",
            "-0.5,  0.0",
            "0.51,  -45.0",
            "1.0,   -45.0",
            "-0.51, 45.0",
            "-1.0,  45.0"
    })
    void tick_laneOffset_steersBackTowardLaneCenter(double laneOffsetMeters, double expectedSteeringDegrees) {
        AutoDriver driver = driverWith(TARGET_SPEED_KMH, FREE_ROAD_METERS, laneOffsetMeters);

        DriveResult result = driver.tick(Gear.D);

        assertEquals(expectedSteeringDegrees, result.steeringAngleDegrees(), DELTA);
    }

    @ParameterizedTest
    @EnumSource(FailingSensor.class)
    void tick_anySensorFails_returnsSafeModeResult(FailingSensor failingSensor) {
        AutoDriver driver = driverWithFailing(failingSensor);

        DriveResult result = driver.tick(Gear.D);

        DriveResult expected = new DriveResult(
                NO_STEERING_DEGREES, SAFE_MODE_BRAKE_PRESSURE, AdaptiveCruise.Mode.BRAKE, false, true);
        assertEquals(expected, result);
    }

    private static AutoDriver driverWith(double speedKmh, double distanceMeters, double laneOffsetMeters) {
        return new AutoDriver(() -> speedKmh, () -> distanceMeters, () -> laneOffsetMeters, TARGET_SPEED_KMH);
    }

    private static AutoDriver driverWithFailing(FailingSensor failingSensor) {
        SpeedSensor speedSensor = () -> TARGET_SPEED_KMH;
        ObstacleSensor obstacleSensor = () -> FREE_ROAD_METERS;
        LaneSensor laneSensor = () -> CENTERED_LANE_OFFSET_METERS;
        switch (failingSensor) {
            case SPEED -> speedSensor = AutoDriverTest::failToRead;
            case OBSTACLE -> obstacleSensor = AutoDriverTest::failToRead;
            case LANE -> laneSensor = AutoDriverTest::failToRead;
        }
        return new AutoDriver(speedSensor, obstacleSensor, laneSensor, TARGET_SPEED_KMH);
    }

    private static double failToRead() {
        throw new SensorFailureException(SENSOR_FAILURE_MESSAGE);
    }
}
