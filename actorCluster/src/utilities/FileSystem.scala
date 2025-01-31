package utilities

/** @author
  *   Esteban Gonzalez Ruales
  */

import scala.util.Try
import os.Path
import os.RelPath

object FileSystem:
  def saveFile(path: RelPath, file: Seq[Byte]): Try[Path] =
    val absPath = os.pwd / path
    Try {
      os.write.over(absPath, file.toArray, createFolders = true)
      absPath
    }
  end saveFile

  def loadFile(path: RelPath): Try[Seq[Byte]] =
    val absPath = os.pwd / path
    Try(os.read.bytes(absPath).toSeq)
  end loadFile

  def deleteFile(path: RelPath): Try[Path] =
    val absPath = os.pwd / path
    Try {
      os.remove.all(absPath)
      absPath
    }
  end deleteFile
end FileSystem
