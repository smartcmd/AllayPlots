package me.daoge.allayplots.plot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlotId")
class PlotIdTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("creates with positive coordinates")
        void createsWithPositiveCoords() {
            PlotId id = new PlotId(5, 10);

            assertThat(id.x()).isEqualTo(5);
            assertThat(id.z()).isEqualTo(10);
        }

        @Test
        @DisplayName("creates with negative coordinates")
        void createsWithNegativeCoords() {
            PlotId id = new PlotId(-3, -7);

            assertThat(id.x()).isEqualTo(-3);
            assertThat(id.z()).isEqualTo(-7);
        }

        @Test
        @DisplayName("creates with origin coordinates")
        void createsWithOrigin() {
            PlotId id = new PlotId(0, 0);

            assertThat(id.x()).isEqualTo(0);
            assertThat(id.z()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("fromString")
    class FromString {

        @Test
        @DisplayName("parses valid positive coordinates")
        void parsesPositive() {
            PlotId id = PlotId.fromString("5;10");

            assertThat(id.x()).isEqualTo(5);
            assertThat(id.z()).isEqualTo(10);
        }

        @Test
        @DisplayName("parses valid negative coordinates")
        void parsesNegative() {
            PlotId id = PlotId.fromString("-3;-7");

            assertThat(id.x()).isEqualTo(-3);
            assertThat(id.z()).isEqualTo(-7);
        }

        @Test
        @DisplayName("parses origin")
        void parsesOrigin() {
            PlotId id = PlotId.fromString("0;0");

            assertThat(id.x()).isEqualTo(0);
            assertThat(id.z()).isEqualTo(0);
        }

        @Test
        @DisplayName("throws on invalid format - no separator")
        void throwsOnNoSeparator() {
            assertThatThrownBy(() -> PlotId.fromString("510"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid plot id");
        }

        @Test
        @DisplayName("throws on invalid format - wrong separator")
        void throwsOnWrongSeparator() {
            assertThatThrownBy(() -> PlotId.fromString("5,10"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws on non-numeric values")
        void throwsOnNonNumeric() {
            assertThatThrownBy(() -> PlotId.fromString("a;b"))
                    .isInstanceOf(NumberFormatException.class);
        }
    }

    @Nested
    @DisplayName("asString")
    class AsString {

        @Test
        @DisplayName("formats positive coordinates")
        void formatsPositive() {
            PlotId id = new PlotId(5, 10);

            assertThat(id.asString()).isEqualTo("5;10");
        }

        @Test
        @DisplayName("formats negative coordinates")
        void formatsNegative() {
            PlotId id = new PlotId(-3, -7);

            assertThat(id.asString()).isEqualTo("-3;-7");
        }

        @Test
        @DisplayName("roundtrips through fromString")
        void roundtrips() {
            PlotId original = new PlotId(42, -17);
            PlotId parsed = PlotId.fromString(original.asString());

            assertThat(parsed).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("equals for same coordinates")
        void equalsForSameCoords() {
            PlotId id1 = new PlotId(5, 10);
            PlotId id2 = new PlotId(5, 10);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("not equals for different x")
        void notEqualsForDifferentX() {
            PlotId id1 = new PlotId(5, 10);
            PlotId id2 = new PlotId(6, 10);

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("not equals for different z")
        void notEqualsForDifferentZ() {
            PlotId id1 = new PlotId(5, 10);
            PlotId id2 = new PlotId(5, 11);

            assertThat(id1).isNotEqualTo(id2);
        }
    }
}
