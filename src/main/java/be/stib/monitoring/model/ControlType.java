package be.stib.monitoring.model;

/** Who is checking tickets at a stop, as reported by a traveller. */
public enum ControlType {
    /** Police officers are present (possibly together with ticket controllers). */
    POLICE,
    /** Only STIB ticket controllers. */
    CONTROLLERS
}
