package be.stib.monitoring.model;

/**
 * A vehicle on a line: {@code pointId} is the last stop it passed and
 * {@code distanceFromPoint} how many metres it has travelled since.
 * {@code directionId} is the stop id of the terminus it is heading to.
 */
public record VehiclePosition(
        String directionId,
        String pointId,
        int distanceFromPoint) {
}
