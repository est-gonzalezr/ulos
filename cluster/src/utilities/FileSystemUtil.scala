package utilities

/** @author
  *   Esteban Gonzalez Ruales
  */

import os.Path
import os.RelPath

import scala.util.Try
import scala.util.Failure

object FileSystemUtil:
  def saveFile(path: Path, file: Seq[Byte]): Try[Path] =
    val absPath = localPath(path)
    Try {
      os.write.over(absPath, file.toArray, createFolders = true)
      absPath
    }
  end saveFile

  def loadFile(path: Path): Try[Seq[Byte]] =
    val absPath = localPath(path)
    Try(os.read.bytes(absPath).toSeq)
  end loadFile

  def deleteBaseDir(path: Path): Try[Path] =
    val baseDir = path.segments.toVector.headOption
    Try {
      baseDir match
        case Some(str) =>
          os.remove.all(os.pwd / str)
        case _ =>
      end match
      path
    }
  end deleteBaseDir

  def deleteFile(path: Path): Try[Path] =
    val absPath = localPath(path)
    Try {
      if os.remove(absPath) then absPath
      else throw Throwable("File doesn't exist")
    }
  end deleteFile

  def localPath(path: Path): Path =
    val rel = path.relativeTo(os.root)
    os.pwd / rel
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
