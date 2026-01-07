package me.daoge.allayplots.plot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Plot")
class PlotTest {

    @Test
    @DisplayName("getOwnerNameOrUUID returns owner name when set")
    void getOwnerNameOrUUID_withOwnerName_returnsOwnerName() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot("world", new PlotId(0, 0))
                .withOwner(owner, "TestPlayer");

        assertThat(plot.getOwnerNameOrUUID()).isEqualTo("TestPlayer");
    }

    @Test
    @DisplayName("getOwnerNameOrUUID returns UUID when owner name is null")
    void getOwnerNameOrUUID_withoutOwnerName_returnsUUID() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot("world", new PlotId(0, 0))
                .withOwner(owner, null);

        assertThat(plot.getOwnerNameOrUUID()).isEqualTo(owner.toString());
    }

    @Test
    @DisplayName("getOwnerNameOrUUID returns 'Unknown' when owner is null")
    void getOwnerNameOrUUID_withNullOwner_returnsUnknown() {
        Plot plot = new Plot("world", new PlotId(0, 0));

        assertThat(plot.getOwnerNameOrUUID()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("isClaimed returns false for unclaimed plot")
    void isClaimed_unclaimedPlot_returnsFalse() {
        Plot plot = new Plot("world", new PlotId(0, 0));

        assertThat(plot.isClaimed()).isFalse();
    }

    @Test
    @DisplayName("isClaimed returns true for claimed plot")
    void isClaimed_claimedPlot_returnsTrue() {
        Plot plot = new Plot("world", new PlotId(0, 0))
                .withOwner(UUID.randomUUID(), "Player");

        assertThat(plot.isClaimed()).isTrue();
    }

    @Test
    @DisplayName("withOwner creates new instance")
    void withOwner_createsNewInstance() {
        Plot original = new Plot("world", new PlotId(0, 0));
        Plot claimed = original.withOwner(UUID.randomUUID(), "Player");

        assertThat(claimed).isNotSameAs(original);
        assertThat(original.isClaimed()).isFalse();
        assertThat(claimed.isClaimed()).isTrue();
    }

    @Test
    @DisplayName("withTrustedAdded adds player to trusted list")
    void withTrustedAdded_addsPlayer() {
        UUID player = UUID.randomUUID();
        Plot plot = new Plot("world", new PlotId(0, 0))
                .withOwner(UUID.randomUUID(), "Owner")
                .withTrustedAdded(player);

        assertThat(plot.getTrusted()).contains(player);
    }

    @Test
    @DisplayName("withDeniedAdded adds player to denied list")
    void withDeniedAdded_addsPlayer() {
        UUID player = UUID.randomUUID();
        Plot plot = new Plot("world", new PlotId(0, 0))
                .withOwner(UUID.randomUUID(), "Owner")
                .withDeniedAdded(player);

        assertThat(plot.getDenied()).contains(player);
    }
}
