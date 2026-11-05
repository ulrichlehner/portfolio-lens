package io.portfoliolens.testing;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** A temporary folder per test, deleted after the test. Register with {@code @RegisterExtension}. */
public final class TemporaryFolder implements AfterEachCallback
{
    private Path root;

    public synchronized File getRoot()
    {
        try
        {
            if (root == null)
                root = Files.createTempDirectory("lens-test");
            return root.toFile();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    public File newFolder() throws IOException
    {
        return Files.createTempDirectory(getRoot().toPath(), "folder").toFile();
    }

    @Override
    public synchronized void afterEach(ExtensionContext context) throws IOException
    {
        if (root == null)
            return;
        try (var paths = Files.walk(root))
        {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
        root = null;
    }
}
