/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2014 wcm.io
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Wrapper around the commons compress library to decompress the zipped tar archives.
 *
 * <p>
 * Extraction is hardened against zip slip and zip bomb attacks via {@link SafeExtract}.
 * See <a href="https://commons.apache.org/proper/commons-compress/security-reports.html">
 * Commons Compress security recommendations</a> and
 * <a href="https://rules.sonarsource.com/java/RSPEC-5042">SonarSource rule java:S5042</a>.
 * </p>
 */
public class TarUnArchiver {

  private final File archive;

  /**
   * @param archive Archive
   */
  public TarUnArchiver(File archive) {
    this.archive = archive;
  }

  /**
   * Unarchives the archive into the base dir
   * @param baseDir Base dir
   * @throws MojoExecutionException Mojo execution exception
   */
  public void unarchive(String baseDir) throws MojoExecutionException {
    Path baseDirPath = Path.of(baseDir);
    try (FileInputStream fis = new FileInputStream(archive);
        TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(fis))) {
      extractEntries(tarIn, baseDirPath);
    }
    catch (IOException ex) {
      throw new MojoExecutionException("Could not extract archive: " + archive.getAbsolutePath(), ex);
    }

    // delete archive after extraction
    try {
      Files.deleteIfExists(archive.toPath());
    }
    catch (IOException ex) {
      throw new MojoExecutionException("Could not delete archive: " + archive.getAbsolutePath(), ex);
    }
  }

  private static void extractEntries(TarArchiveInputStream tarIn, Path baseDirPath) throws IOException {
    long entryCount = 0;
    long totalBytes = 0;
    TarArchiveEntry tarEntry = tarIn.getNextEntry();
    while (tarEntry != null) {
      entryCount++;
      SafeExtract.checkEntryCount(entryCount);
      // resolve safely against the base directory (mitigates zip slip)
      final Path destPath = SafeExtract.resolveSafely(baseDirPath, tarEntry.getName());
      if (tarEntry.isSymbolicLink()) {
        extractSymbolicLink(tarEntry, destPath, baseDirPath);
      }
      else if (tarEntry.isDirectory()) {
        Files.createDirectories(destPath);
      }
      else {
        totalBytes = extractFile(tarIn, destPath, totalBytes);
      }
      tarEntry = tarIn.getNextEntry();
    }
  }

  private static void extractSymbolicLink(TarArchiveEntry tarEntry, Path destPath, Path baseDirPath) throws IOException {
    // ensure symlink target stays within the base directory.
    // Symlink targets are typically relative to the directory containing the symlink,
    // so resolve them against the symlink's parent directory but verify the final
    // location against the extraction base directory.
    Path destParent = destPath.getParent();
    Path linkParent = destParent != null ? destParent : baseDirPath;
    Path resolvedLinkTarget = linkParent.resolve(tarEntry.getLinkName()).normalize();
    if (!resolvedLinkTarget.startsWith(baseDirPath.toAbsolutePath().normalize())) {
      throw new IOException("Symbolic link target is outside of the target directory: "
          + tarEntry.getName() + " -> " + tarEntry.getLinkName());
    }
    if (destParent != null) {
      Files.createDirectories(destParent);
    }
    Files.createSymbolicLink(destPath, Path.of(tarEntry.getLinkName()));
  }

  private static long extractFile(TarArchiveInputStream tarIn, Path destPath, long totalBytes) throws IOException {
    Path destParent = destPath.getParent();
    if (destParent != null) {
      Files.createDirectories(destParent);
    }
    long newTotal;
    try (OutputStream bout = new BufferedOutputStream(Files.newOutputStream(destPath))) {
      newTotal = SafeExtract.copyWithLimit(tarIn, bout, totalBytes);
    }
    setExecutablePermissionsIfPosix(destPath);
    return newTotal;
  }

  private static void setExecutablePermissionsIfPosix(Path destPath) throws IOException {
    // set executable permission via PosixFilePermissions when supported
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(destPath);
      perms.add(PosixFilePermission.OWNER_EXECUTE);
      perms.add(PosixFilePermission.GROUP_EXECUTE);
      Files.setPosixFilePermissions(destPath, perms);
    }
    catch (UnsupportedOperationException ex) {
      // not a POSIX file system (e.g. Windows) - skip setting permissions
    }
  }

}
