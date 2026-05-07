/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2026 wcm.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.wcm.maven.plugins.nodejs.installation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarUnArchiverTest {

  private static final String FILE_CONTENT = "hello tar";

  @Test
  void testUnarchive_validTarGz(@TempDir Path tempDir) throws IOException, MojoExecutionException {
    File archive = tempDir.resolve("test.tar.gz").toFile();
    try (TarArchiveOutputStream tos = newTarGz(archive)) {
      addDirEntry(tos, "subdir/");
      addFileEntry(tos, "subdir/file.txt", FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
      addFileEntry(tos, "root.txt", "root".getBytes(StandardCharsets.UTF_8));
    }

    Path target = tempDir.resolve("out");
    Files.createDirectories(target);
    new TarUnArchiver(archive).unarchive(target.toString());

    assertTrue(Files.isDirectory(target.resolve("subdir")));
    assertEquals(FILE_CONTENT, Files.readString(target.resolve("subdir/file.txt")));
    assertEquals("root", Files.readString(target.resolve("root.txt")));
    // archive must be deleted after extraction
    assertFalse(archive.exists());
  }

  @Test
  void testUnarchive_zipSlipRejected(@TempDir Path tempDir) throws IOException {
    File archive = tempDir.resolve("evil.tar.gz").toFile();
    try (TarArchiveOutputStream tos = newTarGz(archive)) {
      addFileEntry(tos, "../../evil.txt", "boom".getBytes(StandardCharsets.UTF_8));
    }

    Path target = tempDir.resolve("out");
    Files.createDirectories(target);
    MojoExecutionException ex = assertThrows(MojoExecutionException.class,
        () -> new TarUnArchiver(archive).unarchive(target.toString()));
    assertTrue(ex.getCause() instanceof IOException);
    assertTrue(ex.getCause().getMessage().contains("outside of the target directory"));
    assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")));
  }

  @Test
  void testUnarchive_symlinkEscapeRejected(@TempDir Path tempDir) throws IOException {
    File archive = tempDir.resolve("symlink-evil.tar.gz").toFile();
    try (TarArchiveOutputStream tos = newTarGz(archive)) {
      TarArchiveEntry entry = new TarArchiveEntry("link", TarConstants.LF_SYMLINK);
      // symlink target attempts to escape the destination directory
      entry.setLinkName("../../../../etc/passwd");
      tos.putArchiveEntry(entry);
      tos.closeArchiveEntry();
    }

    Path target = tempDir.resolve("out");
    Files.createDirectories(target);
    MojoExecutionException ex = assertThrows(MojoExecutionException.class,
        () -> new TarUnArchiver(archive).unarchive(target.toString()));
    assertTrue(ex.getCause() instanceof IOException);
    assertTrue(ex.getCause().getMessage().contains("outside of the target directory"));
    // symlink itself must not have been created
    assertFalse(Files.exists(target.resolve("link"), java.nio.file.LinkOption.NOFOLLOW_LINKS));
  }

  private static TarArchiveOutputStream newTarGz(File archive) throws IOException {
    OutputStream fos = new FileOutputStream(archive);
    return new TarArchiveOutputStream(new GzipCompressorOutputStream(fos));
  }

  private static void addFileEntry(TarArchiveOutputStream tos, String name, byte[] data) throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    entry.setSize(data.length);
    tos.putArchiveEntry(entry);
    tos.write(data);
    tos.closeArchiveEntry();
  }

  private static void addDirEntry(TarArchiveOutputStream tos, String name) throws IOException {
    TarArchiveEntry entry = new TarArchiveEntry(name);
    tos.putArchiveEntry(entry);
    tos.closeArchiveEntry();
  }

}
