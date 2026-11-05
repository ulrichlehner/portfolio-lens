package io.portfoliolens.source;

import java.util.Objects;

/**
 * A failure to read from Portfolio Performance. The message is safe to print: it never contains
 * the token, response bodies or portfolio data.
 */
public final class PpException extends Exception
{
    private static final long serialVersionUID = 1L;

    public enum Kind
    {
        /** No connection: PP not running or REST API disabled. */
        UNREACHABLE,
        /** The token was rejected (401). */
        UNAUTHORIZED,
        /** PP refused the request (403), for example a non-loopback Host header. */
        FORBIDDEN,
        /** Unknown or not enabled file, or unknown endpoint (404). */
        NOT_FOUND,
        /** The file is enabled but not open in PP (409 file-not-open). */
        FILE_NOT_OPEN,
        /** The alias matches several files (409 ambiguous-alias). */
        AMBIGUOUS_ALIAS,
        /** No file or several files are enabled and none is configured. */
        FILE_SELECTION,
        /** The API version is not 1.x. */
        INCOMPATIBLE,
        /** A response is not valid JSON or lacks a required field. */
        MALFORMED,
        /** Any other HTTP status. */
        HTTP_ERROR
    }

    private final Kind kind;

    public PpException(Kind kind, String message)
    {
        super(Objects.requireNonNull(message));
        this.kind = Objects.requireNonNull(kind);
    }

    public Kind kind()
    {
        return kind;
    }
}
