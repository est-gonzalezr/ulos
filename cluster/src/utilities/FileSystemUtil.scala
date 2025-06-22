package utilities

import os.Path
import os.RelPath

val excludedPatterns = Seq("__MACOSX".r, ".DS_Store".r)

object FileSystemUtil:
  def saveFile(relPath: RelPath, file: Seq[Byte]): Path =
    val absPath = localPath(relPath)
    println(absPath)
    os.write.over(absPath, file.toArray, createFolders = true)
    absPath

  end saveFile

  def loadFile(relPath: RelPath): Seq[Byte] =
    val absPath = localPath(relPath)
    os.read.bytes(absPath).toSeq
  end loadFile

  def deleteTaskBaseDir(relPath: RelPath): Path =
    val absPath = localPath(relPath)
    val deletePath = absPath / os.up / absPath.baseName
    os.remove.all(deletePath)
    absPath

  end deleteTaskBaseDir

  def deleteFile(relPath: RelPath): Path =
    val absPath = localPath(relPath)
    if os.remove(absPath) then absPath
    else throw Throwable("File doesn't exist")
    end if

  end deleteFile

  def unzipFile(relPath: RelPath): Path =
    val absPath = localPath(relPath)
    val unzipPath = absPath / os.up / absPath.baseName
    val tempDir = "temporaryTransferDir"

    val _ = os.remove.all(unzipPath)
    val _ = os.unzip(
      absPath,
      unzipPath,
      excludePatterns = excludedPatterns
    )

    if os.list(unzipPath).length == 1 && os.isDir(os.list(unzipPath).head)
    then
      os.list(unzipPath)
        .foreach(path =>
          os.move(path, path / os.up / tempDir, replaceExisting = true)
        )

      os.list(unzipPath / tempDir)
        .foreach(path =>
          os.move(path, unzipPath / path.last, replaceExisting = true)
        )
      os.remove.all(unzipPath / tempDir)
    end if

    unzipPath

  end unzipFile

  def zipFile(relPath: RelPath): Path =
    val absPath = localPath(relPath)
    val zipPath = absPath / os.up / absPath.baseName
    val _ = os.remove.all(absPath)

    os.zip(absPath, Seq(zipPath), excludePatterns = excludedPatterns)

  end zipFile

  private def localPath(relPath: RelPath): Path =
    os.pwd / relPath
  end localPath
end FileSystemUtil
