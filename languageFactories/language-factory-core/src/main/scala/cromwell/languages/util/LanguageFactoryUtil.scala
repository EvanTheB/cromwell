package cromwell.languages.util

// TODO 2.11: Remove cats.syntax.either._ import:
import cats.syntax.either._
import cats.syntax.validated._
import cats.data.NonEmptyList
import common.Checked
import common.validation.ErrorOr.ErrorOr
import cromwell.core.path.BetterFileMethods.OpenOptions
import cromwell.core.path.{DefaultPathBuilder, Path}
import wom.executable.Executable.ResolvedExecutableInputs
import wom.executable.Executable
import wom.expression.{IoFunctionSet, NoIoFunctionSet}
import wom.graph.GraphNodePort.OutputPort
import wom.values.{WomSingleFile, WomValue}
import cromwell.core.CromwellGraphNode.CromwellEnhancedOutputPort
import cromwell.languages.ValidatedWomNamespace

import scala.util.{Failure, Success, Try}

object LanguageFactoryUtil {

  /**
    * Unzip the imports.zip and validate that it was good.
    * @param zipContents the zip contents
    * @param parentPath if specified, where to unzip to. Otherwise it'll end up somewhere random
    * @return where the imports were unzipped to
    */
  def validateImportsDirectory(zipContents: Array[Byte], parentPath: Option[Path] = None): ErrorOr[Path] = {

    def makeZipFile: Try[Path] = Try {
      DefaultPathBuilder.createTempFile("", ".zip", parentPath).writeByteArray(zipContents)(OpenOptions.default)
    }

    def unZipFile(f: Path) = Try(f.unzipTo(parentPath))

    val importsFile = for {
      zipFile <- makeZipFile
      unzipped <- unZipFile(zipFile)
      _ <- Try(zipFile.delete(swallowIOExceptions = true))
    } yield unzipped

    importsFile match {
      case Success(unzippedDirectory: Path) => unzippedDirectory.validNel
      case Failure(t) => t.getMessage.invalidNel
    }
  }

  def validateWomNamespace(womExecutable: Executable): Checked[ValidatedWomNamespace] = for {
    evaluatedInputs <- validateExecutableInputs(womExecutable.resolvedExecutableInputs, NoIoFunctionSet).toEither
    validatedWomNamespace = ValidatedWomNamespace(womExecutable, evaluatedInputs, Map.empty)
    _ <- validateWdlFiles(validatedWomNamespace.womValueInputs)
  } yield validatedWomNamespace

  /*
    * At this point input values are either a WomValue (if it was provided through the input file)
    * or a WomExpression (if we fell back to the default).
    * We assume that default expressions do NOT reference any "variables" (other inputs, call outputs ...)
    * Should this assumption prove not sufficient InstantiatedExpressions or ExpressionNodes would need to be provided
    * instead so that they can be evaluated JIT.
    * Note that the ioFunctions use engine level pathBuilders. This means that their credentials come from the engine section
    * of the configuration, and are not backend specific.
   */
  private def validateExecutableInputs(inputs: ResolvedExecutableInputs, ioFunctions: IoFunctionSet): ErrorOr[Map[OutputPort, WomValue]] = {
    import common.validation.ErrorOr.MapTraversal
    inputs.traverse[OutputPort, WomValue] {
      case (key, value) => value.fold(ResolvedExecutableInputsPoly).apply(ioFunctions) map { key -> _ }
    }
  }

  private def validateWdlFiles(workflowInputs: Map[OutputPort, WomValue]): Checked[Unit] = {

    def prefix(port: OutputPort) = s"Invalid value for File input '${port.fullyQualifiedName}':"

    val failedFiles = workflowInputs collect {
      case (port, womSingleFile: WomSingleFile) if womSingleFile.value.startsWith("\"gs://") =>
        s"""${prefix(port)} ${womSingleFile.value} starts with a '"'"""
      case (port, womSingleFile: WomSingleFile) if womSingleFile.value.isEmpty =>
        s"${prefix(port)} empty value"
    }

    NonEmptyList.fromList(failedFiles.toList) match {
      case Some(errors) => Left(errors)
      case None => Right(())
    }
  }
}
