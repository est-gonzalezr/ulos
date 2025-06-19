package utilities

import scala.util.Try

import os.Path
import os.RelPath

val excludedPatterns = Seq("__MACOSX".r, ".DS_Store".r)

object FileSystemUtil:
  def saveFile(relPath: RelPath, file: Seq[Byte]): Try[Path] =
    val absPath = localPath(relPath)
    Try {
      os.write.over(absPath, file.toArray, createFolders = true)
      absPath
    }
  end saveFile

  def loadFile(relPath: RelPath): Try[Seq[Byte]] =
    val absPath = localPath(relPath)
    Try(os.read.bytes(absPath).toSeq)
  end loadFile

  def deleteTaskBaseDir(relPath: RelPath): Try[Path] =
    Try {
      val absPath = localPath(relPath)
      val deletePath = absPath / os.up / absPath.baseName
      os.remove.all(deletePath)
      absPath
    }
  end deleteTaskBaseDir

  def deleteFile(relPath: RelPath): Try[Path] =
    val absPath = localPath(relPath)
    Try {
      if os.remove(absPath) then absPath
      else throw Throwable("File doesn't exist")
    }
  end deleteFile

  def unzipFile(relPath: RelPath): Try[Path] =
    val absPath = localPath(relPath)
    val unzipPath = absPath / os.up / absPath.baseName
    val tempDir = "temporaryTransferDir"
    Try {
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
    }
  end unzipFile

  def zipFile(relPath: RelPath): Try[Path] =
    val absPath = localPath(relPath)
    val zipPath = absPath / os.up / absPath.baseName
    val _ = os.remove.all(absPath)
    Try(
      os.zip(absPath, Seq(zipPath), excludePatterns = excludedPatterns)
    )
  end zipFile

  private def localPath(relPath: RelPath): Path =
    os.pwd / relPath
  end localPath
end FileSystemUtil
