package utilities

/** @author
  *   Esteban Gonzalez Ruales
  */

import os.Path
import os.RelPath

import scala.util.Try

object FileSystemUtil:
  def saveFile(path: Path, file: Seq[Byte]): Try[Path] =
    val absPath = os.pwd / path.last
    Try {
      os.write.over(absPath, file.toArray, createFolders = true)
      absPath
    }
  end saveFile

  def loadFile(path: Path): Try[Seq[Byte]] =
    val absPath = os.pwd / path.last
    Try(os.read.bytes(absPath).toSeq)
  end loadFile

  def deleteFile(path: Path): Try[Path] =
    val absPath = os.pwd / path.last
    Try {
      os.remove.all(absPath)
      absPath
    }
  end deleteFile
end FileSystemUtil
