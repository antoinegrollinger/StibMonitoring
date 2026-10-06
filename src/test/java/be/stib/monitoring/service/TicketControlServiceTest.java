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
import java.util.List;
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

    private final LineStopsService lineStops = mock(LineStopsService.class);

    private final TicketControlService service =
            new TicketControlService(new ControlProperties(Duration.ofMinutes(30), 10), stibClient, lineStops, clock);

    {
        when(stibClient.getAllStopDetails()).thenReturn(Map.of("8042", STOP));
    }

    @Test
    void attachesTheStopDetails() {
        assertThat(service.report("8042", null, ControlType.POLICE, null, false).getFirst().stop()).isEqualTo(STOP);
    }

    @Test
    void reportsBothDirectionsOnRequest() {
        when(stibClient.getAllStopDetails()).thenReturn(Map.of("8042", STOP, "8041", STOP));
        when(lineStops.getSameStopPlatforms("5", "8042")).thenReturn(List.of("8041", "8042"));

        List<TicketControl> reported = service.report("8042", "5", ControlType.CONTROLLERS, "exit", true);

        assertThat(reported).extracting(TicketControl::stopId).containsExactly("8042", "8041");
        assertThat(reported).extracting(TicketControl::message).containsOnly("exit");
        assertThat(service.active()).hasSize(2);
    }

    @Test
    void rejectsUnknownStops() {
        assertThatThrownBy(() -> service.report("9999", null, ControlType.POLICE, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsTheReportWhenStopDetailsCannotBeLoaded() {
        when(stibClient.getAllStopDetails()).thenThrow(new StibApiException("down", null));

        TicketControl control = service.report("9999", null, ControlType.POLICE, null, false).getFirst();

        assertThat(control.stop()).isNull();
        assertThat(service.active()).containsExactly(control);
    }

    @Test
    void keepsReportsUntilTheyExpire() {
        TicketControl control = service.report("8042", null, ControlType.POLICE, null, false).getFirst();

        assertThat(control.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(service.active()).containsExactly(control);

        now = now.plus(Duration.ofMinutes(30));
        assertThat(service.active()).isEmpty();
    }

    @Test
    void listsTheMostRecentReportFirst() {
        TicketControl first = service.report("8042", null, ControlType.CONTROLLERS, null, false).getFirst();
        now = now.plusSeconds(60);
        TicketControl second = service.report("8042", null, ControlType.POLICE, null, false).getFirst();

        assertThat(service.active()).containsExactly(second, first);
    }

    @Test
    void stripsTheMessageAndDropsBlankOnes() {
        assertThat(service.report("8042", null, ControlType.POLICE, "  exit A ", false).getFirst().message()).isEqualTo("exit A");
        assertThat(service.report("8042", null, ControlType.POLICE, "   ", false).getFirst().message()).isNull();
    }

    @Test
    void keepsTheOptionalLine() {
        assertThat(service.report("8042", " t7 ", ControlType.POLICE, null, false).getFirst().lineId()).isEqualTo("T7");
        assertThat(service.report("8042", "", ControlType.POLICE, null, false).getFirst().lineId()).isNull();
        assertThatThrownBy(() -> service.report("8042", "1/2", ControlType.POLICE, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingTypeAndTooLongMessages() {
        assertThatThrownBy(() -> service.report("8042", null, null, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report("8042", null, ControlType.POLICE, "x".repeat(11), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.active()).isEmpty();
    }
}
