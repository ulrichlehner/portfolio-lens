package io.portfoliolens.page;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes {@code index.html}. Not implemented yet: the HTML and SVG rendering is task group 6 of
 * the standalone-lens-job change.
 */
public final class PageWriter
{
    public static final String PAGE = "index.html";

    private PageWriter()
    {
    }

    public static void write(Path outputDir, PageInput input) throws IOException
    {
        throw new UnsupportedOperationException("page rendering is not implemented yet (tasks 6.3 and 6.4)");
    }
}
