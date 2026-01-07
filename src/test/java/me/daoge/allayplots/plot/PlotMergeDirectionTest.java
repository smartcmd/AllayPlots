package me.daoge.allayplots.plot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlotMergeDirection")
class PlotMergeDirectionTest {

    @Nested
    @DisplayName("Direction deltas")
    class DirectionDeltas {

        @Test
        @DisplayName("NORTH has correct delta")
        void northDelta() {
            assertThat(PlotMergeDirection.NORTH.dx()).isEqualTo(0);
            assertThat(PlotMergeDirection.NORTH.dz()).isEqualTo(-1);
        }

        @Test
        @DisplayName("EAST has correct delta")
        void eastDelta() {
            assertThat(PlotMergeDirection.EAST.dx()).isEqualTo(1);
            assertThat(PlotMergeDirection.EAST.dz()).isEqualTo(0);
        }

        @Test
        @DisplayName("SOUTH has correct delta")
        void southDelta() {
            assertThat(PlotMergeDirection.SOUTH.dx()).isEqualTo(0);
            assertThat(PlotMergeDirection.SOUTH.dz()).isEqualTo(1);
        }

        @Test
        @DisplayName("WEST has correct delta")
        void westDelta() {
            assertThat(PlotMergeDirection.WEST.dx()).isEqualTo(-1);
            assertThat(PlotMergeDirection.WEST.dz()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("opposite")
    class Opposite {

        @Test
        @DisplayName("NORTH opposite is SOUTH")
        void northOpposite() {
            assertThat(PlotMergeDirection.NORTH.opposite()).isEqualTo(PlotMergeDirection.SOUTH);
        }

        @Test
        @DisplayName("SOUTH opposite is NORTH")
        void southOpposite() {
            assertThat(PlotMergeDirection.SOUTH.opposite()).isEqualTo(PlotMergeDirection.NORTH);
        }

        @Test
        @DisplayName("EAST opposite is WEST")
        void eastOpposite() {
            assertThat(PlotMergeDirection.EAST.opposite()).isEqualTo(PlotMergeDirection.WEST);
        }

        @Test
        @DisplayName("WEST opposite is EAST")
        void westOpposite() {
            assertThat(PlotMergeDirection.WEST.opposite()).isEqualTo(PlotMergeDirection.EAST);
        }

        @Test
        @DisplayName("double opposite returns original")
        void doubleOpposite() {
            for (PlotMergeDirection dir : PlotMergeDirection.values()) {
                assertThat(dir.opposite().opposite()).isEqualTo(dir);
            }
        }
    }

    @Nested
    @DisplayName("getLowerCaseName")
    class GetLowerCaseName {

        @Test
        @DisplayName("returns lowercase name for all directions")
        void returnsLowercase() {
            assertThat(PlotMergeDirection.NORTH.getLowerCaseName()).isEqualTo("north");
            assertThat(PlotMergeDirection.EAST.getLowerCaseName()).isEqualTo("east");
            assertThat(PlotMergeDirection.SOUTH.getLowerCaseName()).isEqualTo("south");
            assertThat(PlotMergeDirection.WEST.getLowerCaseName()).isEqualTo("west");
        }
    }

    @Nested
    @DisplayName("fromString")
    class FromString {

        @ParameterizedTest
        @CsvSource({
                "north, NORTH",
                "NORTH, NORTH",
                "North, NORTH",
                "east, EAST",
                "EAST, EAST",
                "south, SOUTH",
                "SOUTH, SOUTH",
                "west, WEST",
                "WEST, WEST"
        })
        @DisplayName("parses valid direction strings")
        void parsesValid(String input, PlotMergeDirection expected) {
            assertThat(PlotMergeDirection.fromString(input)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "invalid", "n", "northeast", "up", "down"})
        @DisplayName("returns null for invalid strings")
        void returnsNullForInvalid(String input) {
            assertThat(PlotMergeDirection.fromString(input)).isNull();
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            assertThat(PlotMergeDirection.fromString(null)).isNull();
        }

        @Test
        @DisplayName("handles whitespace")
        void handlesWhitespace() {
            assertThat(PlotMergeDirection.fromString("  north  ")).isEqualTo(PlotMergeDirection.NORTH);
        }
    }

    @Nested
    @DisplayName("fromYaw")
    class FromYaw {

        @ParameterizedTest
        @CsvSource({
                "0, SOUTH",      // Looking south (0 degrees)
                "45, WEST",      // Looking southwest
                "90, WEST",      // Looking west
                "135, NORTH",    // Looking northwest
                "180, NORTH",    // Looking north
                "225, EAST",     // Looking northeast
                "270, EAST",     // Looking east
                "315, SOUTH",    // Looking southeast
                "359, SOUTH"     // Almost full rotation
        })
        @DisplayName("returns correct direction for yaw angles")
        void correctDirectionForYaw(double yaw, PlotMergeDirection expected) {
            assertThat(PlotMergeDirection.fromYaw(yaw)).isEqualTo(expected);
        }

        @Test
        @DisplayName("handles negative yaw")
        void handlesNegativeYaw() {
            assertThat(PlotMergeDirection.fromYaw(-90)).isEqualTo(PlotMergeDirection.EAST);
            assertThat(PlotMergeDirection.fromYaw(-180)).isEqualTo(PlotMergeDirection.NORTH);
        }

        @Test
        @DisplayName("handles yaw > 360")
        void handlesLargeYaw() {
            assertThat(PlotMergeDirection.fromYaw(450)).isEqualTo(PlotMergeDirection.WEST);
            assertThat(PlotMergeDirection.fromYaw(720)).isEqualTo(PlotMergeDirection.SOUTH);
        }
    }
}
