package me.daoge.allayplots.plot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlotBounds")
class PlotBoundsTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("stores min and max coordinates")
        void storesCoordinates() {
            PlotBounds bounds = new PlotBounds(0, 34, 0, 34);

            assertThat(bounds.minX()).isEqualTo(0);
            assertThat(bounds.maxX()).isEqualTo(34);
            assertThat(bounds.minZ()).isEqualTo(0);
            assertThat(bounds.maxZ()).isEqualTo(34);
        }

        @Test
        @DisplayName("handles negative coordinates")
        void handlesNegativeCoords() {
            PlotBounds bounds = new PlotBounds(-42, -1, -42, -1);

            assertThat(bounds.minX()).isEqualTo(-42);
            assertThat(bounds.maxX()).isEqualTo(-1);
            assertThat(bounds.minZ()).isEqualTo(-42);
            assertThat(bounds.maxZ()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("contains")
    class Contains {

        private final PlotBounds bounds = new PlotBounds(0, 34, 0, 34);

        @Test
        @DisplayName("returns true for point inside bounds")
        void trueForInside() {
            assertThat(bounds.contains(17, 17)).isTrue();
            assertThat(bounds.contains(10, 20)).isTrue();
        }

        @Test
        @DisplayName("returns true for point on min edge")
        void trueForMinEdge() {
            assertThat(bounds.contains(0, 0)).isTrue();
            assertThat(bounds.contains(0, 17)).isTrue();
            assertThat(bounds.contains(17, 0)).isTrue();
        }

        @Test
        @DisplayName("returns true for point on max edge")
        void trueForMaxEdge() {
            assertThat(bounds.contains(34, 34)).isTrue();
            assertThat(bounds.contains(34, 17)).isTrue();
            assertThat(bounds.contains(17, 34)).isTrue();
        }

        @Test
        @DisplayName("returns true for all corners")
        void trueForCorners() {
            assertThat(bounds.contains(0, 0)).isTrue();
            assertThat(bounds.contains(0, 34)).isTrue();
            assertThat(bounds.contains(34, 0)).isTrue();
            assertThat(bounds.contains(34, 34)).isTrue();
        }

        @Test
        @DisplayName("returns false for point outside - x too low")
        void falseForXTooLow() {
            assertThat(bounds.contains(-1, 17)).isFalse();
        }

        @Test
        @DisplayName("returns false for point outside - x too high")
        void falseForXTooHigh() {
            assertThat(bounds.contains(35, 17)).isFalse();
        }

        @Test
        @DisplayName("returns false for point outside - z too low")
        void falseForZTooLow() {
            assertThat(bounds.contains(17, -1)).isFalse();
        }

        @Test
        @DisplayName("returns false for point outside - z too high")
        void falseForZTooHigh() {
            assertThat(bounds.contains(17, 35)).isFalse();
        }

        @Test
        @DisplayName("returns false for point completely outside")
        void falseForCompletelyOutside() {
            assertThat(bounds.contains(-100, -100)).isFalse();
            assertThat(bounds.contains(100, 100)).isFalse();
        }
    }

    @Nested
    @DisplayName("contains with negative bounds")
    class ContainsNegative {

        private final PlotBounds bounds = new PlotBounds(-42, -8, -42, -8);

        @Test
        @DisplayName("returns true for point inside negative bounds")
        void trueForInsideNegative() {
            assertThat(bounds.contains(-25, -25)).isTrue();
        }

        @Test
        @DisplayName("returns true for corners of negative bounds")
        void trueForNegativeCorners() {
            assertThat(bounds.contains(-42, -42)).isTrue();
            assertThat(bounds.contains(-42, -8)).isTrue();
            assertThat(bounds.contains(-8, -42)).isTrue();
            assertThat(bounds.contains(-8, -8)).isTrue();
        }

        @Test
        @DisplayName("returns false for point outside negative bounds")
        void falseForOutsideNegative() {
            assertThat(bounds.contains(-43, -25)).isFalse();
            assertThat(bounds.contains(-7, -25)).isFalse();
            assertThat(bounds.contains(0, 0)).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equals for same bounds")
        void equalsForSameBounds() {
            PlotBounds b1 = new PlotBounds(0, 34, 0, 34);
            PlotBounds b2 = new PlotBounds(0, 34, 0, 34);

            assertThat(b1).isEqualTo(b2);
            assertThat(b1.hashCode()).isEqualTo(b2.hashCode());
        }

        @Test
        @DisplayName("not equals for different bounds")
        void notEqualsForDifferentBounds() {
            PlotBounds b1 = new PlotBounds(0, 34, 0, 34);
            PlotBounds b2 = new PlotBounds(0, 35, 0, 34);

            assertThat(b1).isNotEqualTo(b2);
        }
    }
}
