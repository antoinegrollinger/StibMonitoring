package be.stib.monitoring.service;

import be.stib.monitoring.client.StibApiException;
import be.stib.monitoring.client.StibClient;
import be.stib.monitoring.config.ControlProperties;
import be.stib.monitoring.model.ControlType;
import be.stib.monitoring.model.LocalizedName;
import be.stib.monitoring.model.StopDetails;
import be.stib.monitoring.model.TicketControl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketControlServiceTest {

    private Instant now = Instant.parse("2026-10-06T08:00:00Z");

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private static final StopDetails STOP =
            new StopDetails("8042", new LocalizedName("Arts-Loi", "Kunst-Wet"), 50.8455, 4.3695);

    private final StibClient stibClient = mock(StibClient.class);

    private final TicketControlService service =
            new TicketControlService(new ControlProperties(Duration.ofMinutes(30), 10), stibClient, clock);

    {
        when(stibClient.getAllStopDetails()).thenReturn(Map.of("8042", STOP));
    }

    @Test
    void attachesTheStopDetails() {
        assertThat(service.report("8042", null, ControlType.POLICE, null).stop()).isEqualTo(STOP);
    }

    @Test
    void rejectsUnknownStops() {
        assertThatThrownBy(() -> service.report("9999", null, ControlType.POLICE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsTheReportWhenStopDetailsCannotBeLoaded() {
        when(stibClient.getAllStopDetails()).thenThrow(new StibApiException("down", null));

        TicketControl control = service.report("9999", null, ControlType.POLICE, null);

        assertThat(control.stop()).isNull();
        assertThat(service.active()).containsExactly(control);
    }

    @Test
    void keepsReportsUntilTheyExpire() {
        TicketControl control = service.report("8042", null, ControlType.POLICE, null);

        assertThat(control.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(service.active()).containsExactly(control);

        now = now.plus(Duration.ofMinutes(30));
        assertThat(service.active()).isEmpty();
    }

    @Test
    void listsTheMostRecentReportFirst() {
        TicketControl first = service.report("8042", null, ControlType.CONTROLLERS, null);
        now = now.plusSeconds(60);
        TicketControl second = service.report("8042", null, ControlType.POLICE, null);

        assertThat(service.active()).containsExactly(second, first);
    }

    @Test
    void stripsTheMessageAndDropsBlankOnes() {
        assertThat(service.report("8042", null, ControlType.POLICE, "  exit A ").message()).isEqualTo("exit A");
        assertThat(service.report("8042", null, ControlType.POLICE, "   ").message()).isNull();
    }

    @Test
    void keepsTheOptionalLine() {
        assertThat(service.report("8042", " t7 ", ControlType.POLICE, null).lineId()).isEqualTo("T7");
        assertThat(service.report("8042", "", ControlType.POLICE, null).lineId()).isNull();
        assertThatThrownBy(() -> service.report("8042", "1/2", ControlType.POLICE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingTypeAndTooLongMessages() {
        assertThatThrownBy(() -> service.report("8042", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report("8042", null, ControlType.POLICE, "x".repeat(11)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.active()).isEmpty();
    }
}
