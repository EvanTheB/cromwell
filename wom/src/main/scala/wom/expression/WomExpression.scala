package wom.expression

import cats.data.Validated._
import cats.syntax.option._
import common.validation.ErrorOr.ErrorOr
import wom.types.WomType
import wom.values._

import scala.concurrent.{ExecutionContext, Future}

trait WomExpression {
  def sourceString: String
  def cacheString = sourceString
  def inputs: Set[String]
  def evaluateValue(inputValues: Map[String, WomValue], ioFunctionSet: IoFunctionSet): ErrorOr[WomValue]
  def evaluateType(inputTypes: Map[String, WomType]): ErrorOr[WomType]
  def evaluateFiles(inputTypes: Map[String, WomValue], ioFunctionSet: IoFunctionSet, coerceTo: WomType): ErrorOr[Set[WomFile]]
}

/**
  * It looks and acts like an expression, but it's really just a value.
  */
final case class ValueAsAnExpression(value: WomValue) extends WomExpression {
  override def sourceString: String = value.valueString
  override def evaluateValue(inputValues: Map[String, WomValue], ioFunctionSet: IoFunctionSet): ErrorOr[WomValue] = Valid(value)
  override def evaluateType(inputTypes: Map[String, WomType]): ErrorOr[WomType] = Valid(value.womType)
  override def evaluateFiles(inputTypes: Map[String, WomValue], ioFunctionSet: IoFunctionSet, coerceTo: WomType): ErrorOr[Set[WomFile]] = Valid(Set.empty)
  override val inputs: Set[String] = Set.empty
}

/**
  * Functions only requiring path manipulation and NO I/O
  */
trait PathFunctionSet {
  /**
    * Similar to java.nio.Path.resolveSibling with
    * of == a string representation of a java.nio.Path
    */
  def sibling(of: String, other: String): String

  /**
    * Similar to java.nio.Path.isAbsolute
    */
  def isAbsolute(path: String): Boolean

  /**
    * Similar to sibling only if "of" IS an absolute path and "other" IS NOT an absolute path, otherwise return other
    */
  def absoluteSibling(of: String, other: String): String = if (isAbsolute(of) && !isAbsolute(other)) sibling(of, other) else other

  /**
    * If path is relative, prefix it with the _host_ call root.
    */
  def relativeToHostCallRoot(path: String): String

  /**
    * Similar to java.nio.Path.getFileName
    */
  def name(path: String): String

  /**
    * Path to stdout
    */
  def stdout: String

  /**
    * Path to stderr
    */
  def stderr: String
}

/**
  * Utility functions to perform various I/O and path related operations
  * Because at this time WOM does not assume anything in terms of implementation,
  * all the paths are of type String.
  */
trait IoFunctionSet {
  // Functions that do NOT necessitate network I/O but are only manipulating paths
  def pathFunctions: PathFunctionSet

  // Functions that (possibly) necessitate I/O operation (on local, network, or cloud filesystems)
  /**
    * Read the content of a file
    * @param path path of the file to read from
    * @param maxBytes maximum number of bytes that can be read
    * @param failOnOverflow if true, the Future will fail if the files has more than maxBytes
    * @return the content of the file as a String
    */
  def readFile(path: String, maxBytes: Option[Int], failOnOverflow: Boolean): Future[String]

  /**
    * Write "content" to the specified "path" location
    */
  def writeFile(path: String, content: String): Future[WomSingleFile]

  /**
    * Copy pathFrom to targetName
    * @return destination as a WomSingleFile
    */
  def copyFile(source: String, destination: String): Future[WomSingleFile]

  /**
    * Glob files and directories using the provided pattern.
    * @return the list of globbed paths
    */
  def glob(pattern: String): Future[Seq[String]]

  /**
    * Recursively list all files (and only files, not directories) under "dirPath"
    * dirPath MUST BE a directory
    * @return The list of all files under "dirPath"
    */
  def listAllFilesUnderDirectory(dirPath: String): Future[Seq[String]]

  /**
    * List entries in a directory non recursively. Includes directories
    */
  def listDirectory(path: String): Future[Iterator[String]]

  /**
    * Return true if path points to a directory, false otherwise
    */
  def isDirectory(path: String): Future[Boolean]

  /**
    * Return the size of the file located at "path"
    */
  def size(path: String): Future[Long]

  /**
    * To map/flatMap over IO results
    */
  implicit def ec: ExecutionContext
}

/**
  * Simply looks up the id in the inputs map.
  */
case class InputLookupExpression(tpe: WomType, id: String) extends WomExpression {

  override def sourceString: String = id

  override def evaluateValue(inputValues: Map[String, WomValue], ioFunctionSet: IoFunctionSet): ErrorOr[WomValue] =
    inputValues.get(id).toValidNel(s"could not find $id in $inputValues!")

  override def evaluateType(inputTypes: Map[String, WomType]): ErrorOr[WomType] = validNel(tpe)

  override def evaluateFiles(inputTypes: Map[String, WomValue], ioFunctionSet: IoFunctionSet, coerceTo: WomType): ErrorOr[Set[WomFile]] =
    evaluateValue(inputTypes, ioFunctionSet).map{
      case x: WomFile => Set(x)
      case _ => Set.empty
    }

  override def inputs: Set[String] = Set(id)
}
