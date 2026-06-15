/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2019 wcm.io
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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Wrapper around the commons compress library to decompress the zip archives.
 *
 * <p>
 * Extraction is hardened against zip slip and zip bomb attacks via {@link SafeExtract}.
 * See <a href="https://commons.apache.org/proper/commons-compress/security-reports.html">
 * Commons Compress security recommendations</a> and
 * <a href="https://rules.sonarsource.com/java/RSPEC-5042">SonarSource rule java:S5042</a>.
 * </p>
 */
public class ZipUnArchiver {

  private final File archive;

  /**
   * Constructor
   * @param archive Archive
   */
  public ZipUnArchiver(File archive) {
    this.archive = archive;
  }

  /**
   * Unarchives the archive into the base dir
   * @param baseDir Base dir
   * @throws MojoExecutionException Mojo execution exception
   */
  public void unarchive(String baseDir) throws MojoExecutionException {
    Path baseDirPath = Path.of(baseDir);
    long entryCount = 0;
    long totalBytes = 0;
    try (FileInputStream fis = new FileInputStream(archive);
        ZipArchiveInputStream zipIn = new ZipArchiveInputStream(fis)) {
      ZipArchiveEntry zipEntry = zipIn.getNextEntry();
      while (zipEntry != null) {
        entryCount++;
        SafeExtract.checkEntryCount(entryCount);
        // resolve safely against the base directory (mitigates zip slip)
        final Path destPath = SafeExtract.resolveSafely(baseDirPath, zipEntry.getName());
        if (zipEntry.isDirectory()) {
          Files.createDirectories(destPath);
        }
        else {
          Path destParent = destPath.getParent();
          if (destParent != null) {
            Files.createDirectories(destParent);
          }
          try (OutputStream bout = new BufferedOutputStream(Files.newOutputStream(destPath))) {
            totalBytes = SafeExtract.copyWithLimit(zipIn, bout, totalBytes);
          }
        }
        zipEntry = zipIn.getNextEntry();
      }
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

}
