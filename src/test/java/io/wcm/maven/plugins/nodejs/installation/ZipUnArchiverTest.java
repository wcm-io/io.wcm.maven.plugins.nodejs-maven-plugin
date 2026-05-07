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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipUnArchiverTest {

  private static final String FILE_CONTENT = "hello zip";

  @Test
  void testUnarchive_validZip(@TempDir Path tempDir) throws IOException, MojoExecutionException {
    File archive = tempDir.resolve("test.zip").toFile();
    try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(archive)) {
      addDirEntry(zos, "subdir/");
      addFileEntry(zos, "subdir/file.txt", FILE_CONTENT.getBytes(StandardCharsets.UTF_8));
      addFileEntry(zos, "root.txt", "root".getBytes(StandardCharsets.UTF_8));
    }

    Path target = tempDir.resolve("out");
    Files.createDirectories(target);
    new ZipUnArchiver(archive).unarchive(target.toString());

    assertTrue(Files.isDirectory(target.resolve("subdir")));
    assertEquals(FILE_CONTENT, Files.readString(target.resolve("subdir/file.txt")));
    assertEquals("root", Files.readString(target.resolve("root.txt")));
    // archive must be deleted after extraction
    assertFalse(archive.exists());
  }

  @Test
  void testUnarchive_zipSlipRejected(@TempDir Path tempDir) throws IOException {
    File archive = tempDir.resolve("evil.zip").toFile();
    try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(archive)) {
      addFileEntry(zos, "../../evil.txt", "boom".getBytes(StandardCharsets.UTF_8));
    }

    Path target = tempDir.resolve("out");
    Files.createDirectories(target);
    MojoExecutionException ex = assertThrows(MojoExecutionException.class,
        () -> new ZipUnArchiver(archive).unarchive(target.toString()));
    assertTrue(ex.getCause() instanceof IOException);
    assertTrue(ex.getCause().getMessage().contains("outside of the target directory"));
    // No file written outside target
    assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    assertFalse(Files.exists(tempDir.getParent().resolve("evil.txt")));
  }

  @Test
  void testUnarchive_tooManyEntries(@TempDir Path tempDir) throws IOException {
    File archive = tempDir.resolve("many.zip").toFile();
    try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(archive)) {
      // create more than MAX_ENTRIES empty entries
      long count = SafeExtract.MAX_ENTRIES + 1;
      for (long i = 0; i < count; i++) {
        ZipArchiveEntry e = new ZipArchiveEntry("f" + i);
        e.setSize(0);
        zos.putArchiveEntry(e);
        zos.closeArchiveEntry();
      }
    }

    Path target = tempDir.resolve("out");
    Files.createDirectories(target);
    MojoExecutionException ex = assertThrows(MojoExecutionException.class,
        () -> new ZipUnArchiver(archive).unarchive(target.toString()));
    assertTrue(ex.getCause() instanceof IOException);
    assertTrue(ex.getCause().getMessage().contains("possible zip bomb"));
  }

  private static void addFileEntry(ZipArchiveOutputStream zos, String name, byte[] data) throws IOException {
    ZipArchiveEntry entry = new ZipArchiveEntry(name);
    entry.setSize(data.length);
    zos.putArchiveEntry(entry);
    zos.write(data);
    zos.closeArchiveEntry();
  }

  private static void addDirEntry(ZipArchiveOutputStream zos, String name) throws IOException {
    ZipArchiveEntry entry = new ZipArchiveEntry(name);
    zos.putArchiveEntry(entry);
    zos.closeArchiveEntry();
  }

}
