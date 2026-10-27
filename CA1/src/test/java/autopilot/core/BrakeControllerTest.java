package autopilot.core;

import autopilot.core.GearBox.Gear;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Naming convention: methodName_stateUnderTest_expectedBehavior
class BrakeControllerTest {

    private static final double DELTA = 1e-9;
    private static final double ABS_PRESSURE_REQUEST = 100.0;
    private static final double ABS_SPEED_KMH = 100.0;
    private static final double ABS_HIGH_PULSE = 90.0;
    private static final double ABS_LOW_PULSE = 60.0;
    private static final double MAX_PRESSURE = 100.0;
    private static final double GENTLE_PRESSURE = 50.0;

    private BrakeController brake;

    @BeforeEach
    void setUp() {
        brake = new BrakeController();
    }

    @ParameterizedTest(name = "request {0}% at {1} km/h gives {2}%")
    @CsvSource({
            "0.0,   0.0,   0.0",
            "50.0,  100.0, 50.0",
            "80.0,  100.0, 80.0",
            "100.0, 30.0,  100.0",
            "81.0,  30.0,  81.0",
            "81.0,  30.1,  90.0",
            "81.0,  100.0, 90.0",
            "100.0, 100.0, 90.0",
            "250.0, 100.0, 90.0",
            "250.0, 20.0,  100.0"
    })
    void apply_requestedPressureAndSpeed_returnsExpectedPressure(
            double requestedPressure, double speedKmh, double expectedPressure) {
        double pressure = brake.apply(requestedPressure, speedKmh, Gear.D);

        assertEquals(expectedPressure, pressure, DELTA);
    }

    @Test
    void apply_consecutiveAbsRequests_alternatesBetweenHighAndLowPulse() {
        List<Double> pulses = List.of(
                brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D),
                brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D),
                brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D),
                brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D));

        assertEquals(List.of(ABS_HIGH_PULSE, ABS_LOW_PULSE, ABS_HIGH_PULSE, ABS_LOW_PULSE), pulses);
    }

    @Test
    void apply_gentleBrakingBetweenAbsRequests_restartsPulseSequenceWithHighPulse() {
        brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D);
        brake.apply(GENTLE_PRESSURE, ABS_SPEED_KMH, Gear.D);

        double pressure = brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D);

        assertEquals(ABS_HIGH_PULSE, pressure, DELTA);
    }

    @Test
    void apply_validRequest_storesReturnedPressure() {
        double returned = brake.apply(GENTLE_PRESSURE, ABS_SPEED_KMH, Gear.D);

        assertEquals(returned, brake.getPressure(), DELTA);
    }

    @Test
    void apply_validRequest_doesNotRaiseWarning() {
        brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D);

        assertFalse(brake.isWarningActive());
    }

    @Test
    void apply_gearIsPark_leavesPressureAtZero() {
        assertThrows(IllegalStateException.class,
                () -> brake.apply(GENTLE_PRESSURE, ABS_SPEED_KMH, Gear.P));

        assertEquals(0.0, brake.getPressure(), DELTA);
    }

    @Test
    void apply_nullGear_throwsNullPointer() {
        assertThrows(NullPointerException.class,
                () -> brake.apply(GENTLE_PRESSURE, ABS_SPEED_KMH, null));
    }

    @ParameterizedTest(name = "request {0}% at {1} km/h")
    @CsvSource({
            "-0.1,      100.0",
            "NaN,       100.0",
            "Infinity,  100.0",
            "-Infinity, 100.0",
            "50.0,      -0.1",
            "50.0,      NaN",
            "50.0,      Infinity",
            "50.0,      -Infinity"
    })
    void apply_invalidPressureOrSpeed_throwsIllegalArgument(double requestedPressure, double speedKmh) {
        assertThrows(IllegalArgumentException.class,
                () -> brake.apply(requestedPressure, speedKmh, Gear.D));
    }

    @ParameterizedTest
    @EnumSource(value = Gear.class, names = "P", mode = EnumSource.Mode.EXCLUDE)
    void emergencyBrake_gearAllowsBraking_appliesMaximumPressure(Gear gear) {
        brake.emergencyBrake(gear);

        assertEquals(MAX_PRESSURE, brake.getPressure(), DELTA);
    }

    @ParameterizedTest
    @EnumSource(value = Gear.class, names = "P", mode = EnumSource.Mode.EXCLUDE)
    void emergencyBrake_gearAllowsBraking_raisesWarning(Gear gear) {
        brake.emergencyBrake(gear);

        assertTrue(brake.isWarningActive());
    }

    @Test
    void emergencyBrake_gearIsPark_doesNotRaiseWarning() {
        assertThrows(IllegalStateException.class, () -> brake.emergencyBrake(Gear.P));

        assertFalse(brake.isWarningActive());
    }

    @Test
    void emergencyBrake_nullGear_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> brake.emergencyBrake(null));
    }

    @Test
    void emergencyBrake_midAbsSequence_restartsPulseSequenceWithHighPulse() {
        brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D);
        brake.emergencyBrake(Gear.D);

        double pressure = brake.apply(ABS_PRESSURE_REQUEST, ABS_SPEED_KMH, Gear.D);

        assertEquals(ABS_HIGH_PULSE, pressure, DELTA);
    }

    @Test
    void apply_afterEmergencyBrake_keepsWarningRaised() {
        brake.emergencyBrake(Gear.D);

        brake.apply(GENTLE_PRESSURE, ABS_SPEED_KMH, Gear.D);

        assertTrue(brake.isWarningActive());
    }
}
