package autopilot.core;

import autopilot.core.GearBox.Gear;
import autopilot.exception.IllegalGearChangeException;
import autopilot.exception.SensorFailureException;
import autopilot.sensor.SpeedSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Naming convention: methodName_stateUnderTest_expectedBehavior
class GearBoxTest {

    private static final double STANDSTILL_KMH = 0.0;

    private SettableSpeedSensor speedSensor;
    private GearBox gearBox;

    @BeforeEach
    void setUp() {
        speedSensor = new SettableSpeedSensor(STANDSTILL_KMH);
        gearBox = new GearBox(speedSensor);
    }

    @Test
    void changeTo_freshGearBox_startsInPark() {
        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvSource({
            "P, R, 0.0",
            "P, R, 4.9",
            "R, D, 0.0",
            "D, R, 0.0",
            "R, N, 0.0",
            "R, N, 80.0",
            "N, D, 0.0",
            "N, D, 80.0"
    })
    void changeTo_allowedTransition_switchesToTargetGear(Gear from, Gear to, double speedKmh) {
        moveTo(from);
        speedSensor.speedKmh = speedKmh;

        gearBox.changeTo(to);

        assertEquals(to, gearBox.getCurrentGear());
    }

    @ParameterizedTest(name = "{0} -> {1} at {2} km/h")
    @CsvSource({
            "P, R, 5.0",
            "P, R, 80.0",
            "R, D, 0.1",
            "D, R, 0.1",
            "P, N, 0.0",
            "P, D, 0.0",
            "R, P, 0.0",
            "N, P, 0.0",
            "N, R, 0.0",
            "D, P, 0.0",
            "D, N, 0.0"
    })
    void changeTo_forbiddenTransition_throwsIllegalGearChange(Gear from, Gear to, double speedKmh) {
        moveTo(from);
        speedSensor.speedKmh = speedKmh;

        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(to));
    }

    @Test
    void changeTo_forbiddenTransition_keepsCurrentGear() {
        speedSensor.speedKmh = 80.0;

        assertThrows(IllegalGearChangeException.class, () -> gearBox.changeTo(Gear.R));

        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    @Test
    void changeTo_sameGearAsCurrent_doesNotReadSpeedSensor() {
        gearBox.setSpeedSensor(() -> {
            throw new SensorFailureException("speed sensor must not be read");
        });

        gearBox.changeTo(Gear.P);

        assertEquals(Gear.P, gearBox.getCurrentGear());
    }

    @Test
    void changeTo_speedSensorFails_propagatesSensorFailure() {
        gearBox.setSpeedSensor(() -> {
            throw new SensorFailureException("speed sensor is down");
        });

        assertThrows(SensorFailureException.class, () -> gearBox.changeTo(Gear.R));
    }

    @Test
    void changeTo_negativeSensorSpeed_throwsIllegalArgument() {
        speedSensor.speedKmh = -1.0;

        assertThrows(IllegalArgumentException.class, () -> gearBox.changeTo(Gear.R));
    }

    @Test
    void changeTo_nanSensorSpeed_throwsIllegalArgument() {
        speedSensor.speedKmh = Double.NaN;

        assertThrows(IllegalArgumentException.class, () -> gearBox.changeTo(Gear.R));
    }

    @Test
    void changeTo_nullTargetGear_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> gearBox.changeTo(null));
    }

    // Walks the gearbox to the requested gear while the car is standing still, where every step is legal.
    private void moveTo(Gear gear) {
        switch (gear) {
            case P -> { }
            case R -> gearBox.changeTo(Gear.R);
            case N -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.N);
            }
            case D -> {
                gearBox.changeTo(Gear.R);
                gearBox.changeTo(Gear.D);
            }
        }
    }

    private static final class SettableSpeedSensor implements SpeedSensor {
        private double speedKmh;

        private SettableSpeedSensor(double speedKmh) {
            this.speedKmh = speedKmh;
        }

        @Override
        public double getSpeedKmh() {
            return speedKmh;
        }
    }
}
