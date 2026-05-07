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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Wrapper around the commons compress library to decompress the zipped tar archives
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
    try (FileInputStream fis = new FileInputStream(archive);
        TarArchiveInputStream tarIn = new TarArchiveInputStream(new GzipCompressorInputStream(fis))) {
      TarArchiveEntry tarEntry = tarIn.getNextEntry();
      while (tarEntry != null) {
        // Create a file for this tarEntry
        final Path destPath = Path.of(baseDir, tarEntry.getName());
        if (tarEntry.isSymbolicLink()) {
          Path targetPath = Path.of(tarEntry.getLinkName());
          Files.createSymbolicLink(destPath, targetPath);
        }
        else if (tarEntry.isDirectory()) {
          Files.createDirectories(destPath);
        }
        else {
          if (destPath.getParent() != null) {
            Files.createDirectories(destPath.getParent());
          }
          try (BufferedOutputStream bout = new BufferedOutputStream(Files.newOutputStream(destPath))) {
            IOUtils.copy(tarIn, bout);
          }
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
        tarEntry = tarIn.getNextEntry();
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
