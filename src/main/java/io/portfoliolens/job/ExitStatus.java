package io.portfoliolens.job;

/** Exit statuses of the CLI, following the BSD sysexits convention. */
public final class ExitStatus
{
    /** Success, a skipped run, or another run is active. */
    public static final int OK = 0;
    /** Wrong command-line usage or configuration. */
    public static final int CONFIG = 64;
    /** Portfolio Performance is not reachable, refuses access, or is incompatible. */
    public static final int UNAVAILABLE = 69;
    /** An unexpected internal error. */
    public static final int SOFTWARE = 70;
    /** The output or cache directory cannot be written. */
    public static final int CANT_CREATE = 73;

    private ExitStatus()
    {
    }
}
