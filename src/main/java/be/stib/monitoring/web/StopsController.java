package be.stib.monitoring.web;

import be.stib.monitoring.model.LineMessage;
import be.stib.monitoring.model.LineStopDetails;
import be.stib.monitoring.model.LineStops;
import be.stib.monitoring.model.LiveLineStops;
import be.stib.monitoring.model.MergedLiveLine;
import be.stib.monitoring.service.LineStopsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lines")
public class StopsController {

    private final LineStopsService lineStopsService;

    public StopsController(LineStopsService lineStopsService) {
        this.lineStopsService = lineStopsService;
    }

    /** Ids of every line in the network. */
    @GetMapping
    public List<String> lineIds() {
        return lineStopsService.getLineIds();
    }

    /** Live view of several lines at once, e.g. {@code /api/lines/live?ids=1,5,92}. */
    @GetMapping("/live")
    public List<LiveLineStops> liveStops(@RequestParam List<String> ids) {
        return lineStopsService.getLiveStopsByLines(ids);
    }

    /** Like {@link #liveStops(List)}, with each line's directions merged into one list of stops. */
    @GetMapping("/live/merged")
    public List<MergedLiveLine> mergedLiveStops(@RequestParam List<String> ids) {
        return lineStopsService.getMergedLiveStopsByLines(ids);
    }

    /** Service messages for several lines at once, each message listed once. */
    @GetMapping("/messages")
    public List<LineMessage> messages(@RequestParam List<String> ids) {
        return lineStopsService.getLineMessages(ids);
    }

    @GetMapping("/{lineId:[A-Za-z0-9]+}/stops")
    public List<LineStops> stops(@PathVariable String lineId) {
        return lineStopsService.getStopsByLine(lineId);
    }

    @GetMapping("/{lineId:[A-Za-z0-9]+}/stops/details")
    public List<LineStopDetails> stopDetails(@PathVariable String lineId) {
        return lineStopsService.getStopDetailsByLines(List.of(lineId));
    }

    @GetMapping("/{lineId:[A-Za-z0-9]+}/stops/live")
    public List<LiveLineStops> liveStops(@PathVariable String lineId) {
        return lineStopsService.getLiveStopsByLines(List.of(lineId));
    }

    @GetMapping("/{lineId:[A-Za-z0-9]+}/messages")
    public List<LineMessage> messages(@PathVariable String lineId) {
        return lineStopsService.getLineMessages(List.of(lineId));
    }
}
