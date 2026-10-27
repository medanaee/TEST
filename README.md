# Software Testing CA1: Parameterized Tests with JUnit 5

This is our solution to the first assignment of the Software Testing course at the University of Tehran (Dr. Ehsan Khamespanah, Fall 2026 / Mehr 1405). The course gave us a small self-driving car codebase and asked us to unit test it with JUnit 5, with a focus on parameterized tests.

The production code in `CA1/src/main` is the course's [F05-CA1](https://github.com/UT-SWT/F05-CA1) codebase, unchanged. Everything we wrote lives in `CA1/src/test`.

**Team:** [medanaee](https://github.com/medanaee) and [imSaDy](https://github.com/imSaDy)

## Running the tests

You need JDK 17 or newer (we used Temurin 26) and Maven 3.6.3 or newer.

```bash
cd CA1
mvn test
```

All 149 tests should pass. To run a single class or a single test:

```bash
mvn test -Dtest=GearBoxTest
mvn test -Dtest=GearBoxTest#changeTo_nullTargetGear_throwsNullPointer
```

In IntelliJ, open `CA1/pom.xml` as a project, right-click `src/test/java` and choose **Run 'All Tests'**. The test panel lists every parameterized case on its own line, which is the easiest way to see exactly which inputs were tried.

## What's where

```
CA1/
├── README.md          architecture of the codebase (from the course)
├── SWT-05-CA1.md      the assignment brief, in Persian (also as a PDF)
├── pom.xml
└── src/
    ├── main/java/autopilot/       the code under test: core, sensor, exception
    └── test/
        ├── java/autopilot/core/   one test class per class we tested
        └── resources/             CSV tables used by @CsvFileSource
```

## How we wrote the tests

Every test name follows `methodName_stateUnderTest_expectedBehavior`, for example `changeTo_forbiddenTransition_throwsIllegalGearChange` or `apply_gearIsPark_leavesPressureAtZero`. We chose it because when a test fails, the name alone tells you what broke.

Most of the work went into picking inputs. For each method we split the inputs into groups that should behave the same way, took a typical value from each group, and then tested both sides of every boundary in the code. Some examples:

- `GearBox`: shifting from P to R is only allowed below 5 km/h, so we try 4.9 (allowed) and 5.0 (rejected).
- `SteeringController`: the wheel moves at most 60° per tick and its range shrinks above 120 km/h, so we try 60° against 61°, and 120 against 120.1 km/h.
- `BrakeController`: ABS kicks in above 80% pressure and above 30 km/h, so we try 80/81% and 30/30.1 km/h.
- `AdaptiveCruise`: at 80 km/h the emergency, safe and release distances are 20, 40 and 48 m, and the tests read distances right at and just next to them.
- Invalid input (negative values, `NaN`, ±infinity) gets one parameterized test per method instead of a copy-pasted test per value.

Each test creates its own objects, either in `@BeforeEach` or in the test itself, so no test depends on another or on the order they run in.

### Parameter sources

The brief asked for all five sources, each used for a different method. This is where each one ended up:

| Source | Used for | Why this one |
|---|---|---|
| `@ValueSource` | `AdaptiveCruise.safeDistanceFor` (invalid speeds) | one input per case, nothing else needed |
| `@CsvSource` | `GearBox.changeTo`, `BrakeController.apply`, `AutoDriver.tick` (lane offsets) | short tables that read well next to the test |
| `@CsvFileSource` | `AdaptiveCruise.update` | the decision tables are long enough to deserve their own files |
| `@MethodSource` | `SteeringController.update` and `selfCenter` | the arguments use constants like `HIGHWAY_SPEED_KMH` and `Double.NaN` |
| `@EnumSource` | `BrakeController.emergencyBrake`, `AutoDriver.tick` (sensor failures) | every gear except P, and every sensor, without listing them by hand |

## Reviewing the AutoDriver tests

The course shipped `AutoDriverTest` with some deliberate problems. Here is what we found and what we did about it:

- Names like `test1` and `testLanePositive` didn't say anything, so they now follow our convention.
- Magic numbers (`80.0`, `100.0`, `30.0`, `±1.0`) became named constants such as `TARGET_SPEED_KMH` and `SAFE_MODE_BRAKE_PRESSURE`.
- Every test rebuilt three anonymous sensor classes. Two small helpers, `driverWith(...)` and `driverWithFailing(...)`, now do that with lambdas.
- The speed and obstacle failure tests were near copies of each other, with five assertions each. They are now one `@EnumSource` test that compares the whole `DriveResult` in a single assertion. It also covers a lane sensor failure, which the original tests never checked.
- The lane tests only checked whether the angle was positive or negative at ±1 m. The new `@CsvSource` test checks the exact angle and includes the 0.5 / 0.51 m threshold.

## What isn't covered, and why

For the four classes we were asked to test (`AdaptiveCruise`, `BrakeController`, `GearBox`, `SteeringController`), line and branch coverage is complete except for:

- one getter and one constructor check, which the brief says don't need tests;
- the `safeDistance == 0` branch in `AdaptiveCruise.proportionalBrake`, which can never run. It is only reached when the distance is below the safe distance, and a distance can't be negative.

Outside those four classes, the braking branches of `AutoDriver.tick` aren't covered because the brief asked us to fix the existing `AutoDriver` tests rather than add new scenarios, and `SensorSnapshot` isn't used anywhere in the project.

## Things we noticed along the way

We didn't touch the production code, so the tests describe how it behaves today. A few of those behaviours surprised us:

- `GearBox` never lets you go back to P once you leave it, even when the car is standing still.
- If a sensor fails right after an emergency brake, `AutoDriver` drops to 30% braking and turns the warning off until the sensor comes back.
- `AutoDriver.tick` throws `IllegalStateException` in P if the cruise controller wants to brake, because it only catches sensor failures.
