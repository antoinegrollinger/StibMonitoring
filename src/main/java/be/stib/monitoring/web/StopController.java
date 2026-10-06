package be.stib.monitoring.web;

import be.stib.monitoring.client.StibClient;
import be.stib.monitoring.model.WaitingTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stops")
public class StopController {

    private final StibClient stibClient;

    public StopController(StibClient stibClient) {
        this.stibClient = stibClient;
    }

    @GetMapping("/{stopId:[A-Za-z0-9]+}/waiting-times")
    public List<WaitingTime> waitingTimes(@PathVariable String stopId) {
        return stibClient.getWaitingTimes(stopId);
    }
}
