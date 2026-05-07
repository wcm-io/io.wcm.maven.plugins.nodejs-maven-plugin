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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeExtractTest {

  @Test
  void testResolveSafely_validEntry(@TempDir Path tempDir) throws IOException {
    Path resolved = SafeExtract.resolveSafely(tempDir, "subdir/file.txt");
    assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()));
    assertTrue(resolved.endsWith(Path.of("subdir", "file.txt")));
  }

  @Test
  void testResolveSafely_validNestedEntry(@TempDir Path tempDir) throws IOException {
    Path resolved = SafeExtract.resolveSafely(tempDir, "a/b/c/d.txt");
    assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()));
  }

  @Test
  void testResolveSafely_zipSlipParentEscape(@TempDir Path tempDir) {
    IOException ex = assertThrows(IOException.class,
        () -> SafeExtract.resolveSafely(tempDir, "../../../etc/passwd"));
    assertTrue(ex.getMessage().contains("outside of the target directory"));
  }

  @Test
  void testResolveSafely_zipSlipDeepParentEscape(@TempDir Path tempDir) {
    // many parent-references should always escape
    assertThrows(IOException.class,
        () -> SafeExtract.resolveSafely(tempDir, "../../../../../../evil.txt"));
  }

  @Test
  void testResolveSafely_subdirParentRefStillInside(@TempDir Path tempDir) throws IOException {
    // entry that uses .. but stays inside the base directory must be allowed
    Path resolved = SafeExtract.resolveSafely(tempDir, "a/b/../c.txt");
    assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()));
    assertTrue(resolved.endsWith(Path.of("a", "c.txt")));
  }

  @Test
  void testCopyWithLimit_copiesAllBytes() throws IOException {
    byte[] data = "Hello, World!".getBytes();
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    long total = SafeExtract.copyWithLimit(in, out, 0L);
    assertEquals(data.length, total);
    assertEquals("Hello, World!", out.toString());
  }

  @Test
  void testCopyWithLimit_accumulatesPriorTotal() throws IOException {
    byte[] data = new byte[100];
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    long total = SafeExtract.copyWithLimit(in, out, 500L);
    assertEquals(600L, total);
  }

  @Test
  void testCopyWithLimit_exceedsLimit() {
    // start near the limit so a small input pushes us over
    byte[] data = new byte[200];
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    OutputStream out = OutputStream.nullOutputStream();
    long startTotal = SafeExtract.MAX_TOTAL_UNCOMPRESSED_BYTES - 50L;
    IOException ex = assertThrows(IOException.class,
        () -> SafeExtract.copyWithLimit(in, out, startTotal));
    assertTrue(ex.getMessage().contains("possible zip bomb"));
  }

  @Test
  void testCopyWithLimit_exactlyAtLimitOk() throws IOException {
    // writing exactly to the limit should NOT throw (strict greater-than check)
    int chunk = 100;
    InputStream in = new ByteArrayInputStream(new byte[chunk]);
    OutputStream out = OutputStream.nullOutputStream();
    long startTotal = SafeExtract.MAX_TOTAL_UNCOMPRESSED_BYTES - chunk;
    long total = SafeExtract.copyWithLimit(in, out, startTotal);
    assertEquals(SafeExtract.MAX_TOTAL_UNCOMPRESSED_BYTES, total);
  }

  @Test
  void testCheckEntryCount_underLimit() throws IOException {
    SafeExtract.checkEntryCount(0L);
    SafeExtract.checkEntryCount(1L);
    SafeExtract.checkEntryCount(SafeExtract.MAX_ENTRIES);
  }

  @Test
  void testCheckEntryCount_overLimit() {
    IOException ex = assertThrows(IOException.class,
        () -> SafeExtract.checkEntryCount(SafeExtract.MAX_ENTRIES + 1));
    assertTrue(ex.getMessage().contains("possible zip bomb"));
  }

}
