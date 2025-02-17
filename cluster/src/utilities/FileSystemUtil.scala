package utilities

/** @author
  *   Esteban Gonzalez Ruales
  */

import os.Path
import os.RelPath

import scala.util.Try
import scala.util.Failure

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
      val baseDir = relPath.segments.toVector.headOption
      baseDir match
        case Some(str) =>
          val taskBasePath = os.pwd / str
          os.remove.all(taskBasePath)
          taskBasePath
        case _ => throw Throwable("Non existent base dir")
      end match
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
    val unzipPath = absPath / os.up
    Try(
      os.unzip(
        absPath,
        unzipPath,
        excludePatterns = excludedPatterns
      ) / absPath.baseName
    )
  end unzipFile

  def zipFile(relPath: RelPath): Try[Path] =
    val absPath = localPath(relPath)
    val zipPath = absPath / os.up / absPath.baseName
    Try(os.zip(absPath, Seq(zipPath)))
  end zipFile

  private def localPath(relPath: RelPath): Path =
    os.pwd / relPath
  end localPath

  private def commonPrefix(p1: Path, p2: Path): Option[Path] =
    val segments1 = p1.segments.toVector
    val segments2 = p2.segments.toVector
    val commonSegments =
      segments1.zip(segments2).takeWhile(_ == _).map(_(0))

    if commonSegments.nonEmpty then
      Some(os.Path(commonSegments.mkString("/", "/", "")))
    else None
    end if
  end commonPrefix
end FileSystemUtil
