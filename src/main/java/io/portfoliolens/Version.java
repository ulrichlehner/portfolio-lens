package io.portfoliolens;

/** Version of the running Lens build, from the jar manifest. */
public final class Version
{
    private Version()
    {
    }

    /** The version, or {@code dev} when running from classes outside a jar. */
    public static String get()
    {
        var version = Version.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    /** The user agent of every outgoing request: a product name, no personal data. */
    public static String userAgent()
    {
        return "PortfolioLens/" + get();
    }
}
