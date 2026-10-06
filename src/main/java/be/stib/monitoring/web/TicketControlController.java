package be.stib.monitoring.web;

import be.stib.monitoring.model.ControlType;
import be.stib.monitoring.model.TicketControl;
import be.stib.monitoring.service.TicketControlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TicketControlController {

    /** @param bothDirections also report it at the stop's platform(s) in the other direction */
    public record ReportRequest(ControlType type, String lineId, String message, boolean bothDirections) {
    }

    private final TicketControlService controls;

    public TicketControlController(TicketControlService controls) {
        this.controls = controls;
    }

    /** Every active report; there are few at any time, so the frontend filters them itself. */
    @GetMapping("/controls")
    public List<TicketControl> active() {
        return controls.active();
    }

    @PostMapping("/stops/{stopId:[A-Za-z0-9]+}/controls")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TicketControl> report(@PathVariable String stopId, @RequestBody ReportRequest request) {
        return controls.report(stopId, request.lineId(), request.type(), request.message(), request.bothDirections());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidReport(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
