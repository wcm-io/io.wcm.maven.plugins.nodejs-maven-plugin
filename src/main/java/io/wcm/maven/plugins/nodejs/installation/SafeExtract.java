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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Helper to mitigate zip slip and zip bomb attacks during archive extraction.
 *
 * <p>
 * Apache Commons Compress does not perform these checks itself; callers are
 * expected to implement them. See:
 * <ul>
 * <li><a href="https://commons.apache.org/proper/commons-compress/security-reports.html">
 * Apache Commons Compress security recommendations</a></li>
 * <li><a href="https://rules.sonarsource.com/java/RSPEC-5042">
 * SonarSource rule java:S5042 (Expanding archive files without controlling resource consumption is
 * security-sensitive)</a></li>
 * <li><a href="https://snyk.io/research/zip-slip-vulnerability">
 * Snyk: Zip Slip vulnerability</a></li>
 * </ul>
 * </p>
 */
final class SafeExtract {

  /** Maximum total uncompressed size of an extracted archive (1 GB). */
  static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L;
  /** Maximum number of entries in an archive. */
  static final long MAX_ENTRIES = 100_000L;

  private static final int BUFFER_SIZE = 8192;

  private SafeExtract() {
    // static helper
  }

  /**
   * Resolve an entry path against the target base directory and ensure it stays inside it
   * (mitigates zip slip).
   * @param baseDir target base directory (normalized, absolute)
   * @param entryName archive entry name
   * @return resolved, normalized destination path
   * @throws IOException if the entry would escape the base directory
   */
  static Path resolveSafely(Path baseDir, String entryName) throws IOException {
    Path normalizedBase = baseDir.toAbsolutePath().normalize();
    Path resolved = normalizedBase.resolve(entryName).normalize();
    if (!resolved.startsWith(normalizedBase)) {
      throw new IOException("Archive entry is outside of the target directory: " + entryName);
    }
    return resolved;
  }

  /**
   * Copy data from an archive entry stream while enforcing the global byte limit
   * (mitigates zip bomb).
   * @param in archive entry input stream
   * @param out destination stream
   * @param bytesWrittenSoFar bytes already written for the current archive
   * @return new total of bytes written
   * @throws IOException if the limit is exceeded
   */
  static long copyWithLimit(InputStream in, OutputStream out, long bytesWrittenSoFar) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long total = bytesWrittenSoFar;
    int n = in.read(buffer);
    while (n != -1) {
      total += n;
      if (total > MAX_TOTAL_UNCOMPRESSED_BYTES) {
        throw new IOException("Archive uncompressed size exceeds limit of "
            + MAX_TOTAL_UNCOMPRESSED_BYTES + " bytes (possible zip bomb)");
      }
      out.write(buffer, 0, n);
      n = in.read(buffer);
    }
    return total;
  }

  /**
   * Check that the number of processed entries does not exceed the limit.
   * @param entryCount current entry count
   * @throws IOException if the limit is exceeded
   */
  static void checkEntryCount(long entryCount) throws IOException {
    if (entryCount > MAX_ENTRIES) {
      throw new IOException("Archive contains more than " + MAX_ENTRIES
          + " entries (possible zip bomb)");
    }
  }

}
