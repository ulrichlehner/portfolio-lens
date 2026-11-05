package io.portfoliolens.job;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Replaces files so that a reader sees either the old or the new content, never a part. */
public final class AtomicFiles
{
    private AtomicFiles()
    {
    }

    public static void write(Path target, byte[] content) throws IOException
    {
        var directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        var temporary = Files.createTempFile(directory, "." + target.getFileName(), ".tmp");
        try
        {
            Files.write(temporary, content);
            try
            {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }
}
