package womtool.input

import java.nio.file.{Files, Paths}

import better.files.File
import cats.syntax.either._
import cats.instances.either._
import common.Checked
import common.transforms.CheckedAtoB
import common.validation.Validation._
import common.validation.Checked._
import common.collections.EnhancedCollections._
import cromwell.core.path.{DefaultPathBuilder, Path}
import cromwell.languages.util.ImportResolver._
import cwl.CwlDecoder
import languages.cwl.CwlV1_0LanguageFactory
import languages.wdl.draft2.WdlDraft2LanguageFactory
import languages.wdl.draft3.WdlDraft3LanguageFactory
import spray.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue}
import wdl.draft2.model.{WdlNamespace, WdlNamespaceWithWorkflow}
import wdl.transforms.draft2.wdlom2wom.WdlDraft2WomBundleMakers._
import wdl.draft3.transforms.wdlom2wom.{FileElementToWomBundleInputs, fileElementToWomBundle}
import wdl.draft3.transforms.ast2wdlom.astToFileElement
import wdl.draft3.transforms.parsing.fileToAst
import wom.callable.WorkflowDefinition
import wom.executable.WomBundle
import wom.expression.NoIoFunctionSet
import wom.graph._
import wom.transforms.WomBundleMaker.ops._
import wom.types._

import scala.collection.JavaConverters._
import scala.util.Try

object WomGraphMaker {

  def fromFiles(mainFile: Path, inputs: Option[Path]): Checked[Graph] = {

    // Resolves for:
    // - Where we run from
    // - Where the file is
    lazy val importResolvers = List(
      directoryResolver(DefaultPathBuilder.build(Paths.get("."))),
      directoryResolver(DefaultPathBuilder.build(Paths.get(mainFile.toAbsolutePath.toFile.getParent)))
    )

    readFile(mainFile.toAbsolutePath.pathAsString) flatMap { mainFileContents =>
      val languageFactory = if (mainFile.name.toLowerCase().endsWith("wdl")) {
        if (mainFileContents.startsWith("version draft-3")) new WdlDraft3LanguageFactory(Map.empty) else new WdlDraft2LanguageFactory(Map.empty)
      } else new CwlV1_0LanguageFactory(Map.empty)

      val womBundleCheck = languageFactory.getWomBundle(mainFileContents, "{}", importResolvers, List(languageFactory))

      val results = inputs match {
        case None =>
          for {
            womBundle <- womBundleCheck
            executableCallable <- womBundle.toExecutableCallable
          } yield executableCallable.graph
        case Some(inputsFile) =>
          for {
            womBundle <- womBundleCheck
            inputsContents <- readFile(inputsFile.toAbsolutePath.pathAsString)
            validatedWomNamespace <- languageFactory.createExecutable(womBundle, inputsContents, NoIoFunctionSet)
          } yield validatedWomNamespace.executable.graph
      }
      results
    }

  }

  private def readFile(filePath: String): Checked[String] = Try(Files.readAllLines(Paths.get(filePath)).asScala.mkString(System.lineSeparator())).toChecked

//  private def womExecutableFromWdl(filePath: Path, inputs: Option[Path]): Checked[Graph] = {
//    val workflowFileString = readFile(filePath.toAbsolutePath.toString)
//
//    // Resolves for:
//    // - Where we run from
//    // - Where the file is
//    val importResolvers = List(
//      directoryResolver(DefaultPathBuilder.build(Paths.get("."))),
//      directoryResolver(DefaultPathBuilder.build(Paths.get(filePath.toAbsolutePath.toFile.getParent)))
//    )
//
//    val womBundle: Checked[WomBundle] = if (workflowFileString.trim.startsWith("version draft-3")) {
//      val converter: CheckedAtoB[File, WomBundle] = fileToAst andThen astToFileElement.map(FileElementToWomBundleInputs(_, "{}", importResolvers, List(new WdlDraft3LanguageFactory()))) andThen fileElementToWomBundle
//      converter.run(File(filePath.pathAsString))
//    } else {
//      WdlNamespaceWithWorkflow.load(readFile(filePath.pathAsString), Seq(WdlNamespace.fileResolver _)).toChecked.flatMap(_.toWomBundle)
//    }
//
//    (womBundle flatMap {
//      case wom if (wom.allCallables.filterByType[WorkflowDefinition]: Set[WorkflowDefinition]).size == 1 =>
//        Right((wom.allCallables.filterByType[WorkflowDefinition]: Set[WorkflowDefinition]).head.graph)
//      case _ => "Can only interpret a workflow file with exactly one workflow".invalidNelCheck
//    }) leftMap { errors => errors.map("Failed to create WOM: " + _) }
//  }
//
//  private def womExecutableFromCwl(filePath: Path): Checked[Graph] = {
//    import cwl.AcceptAllRequirements
//    (for {
//      clt <- CwlDecoder.decodeCwlFile(File(filePath.toAbsolutePath.toString)).
//        value.
//        unsafeRunSync
//      inputs = clt.requiredInputs
//      fakedInputs = JsObject(inputs map { i => i._1 -> fakeInput(i._2) })
//      wom <- clt.womExecutable(AcceptAllRequirements, Option(fakedInputs.prettyPrint))
//    } yield wom) match {
//      case Right(womExecutable) => womExecutable.graph.validNelCheck
//      case Left(e) => Left(e.map(error => s"Can't build WOM executable from CWL: $error))"))
//    }
//  }
//
//  def fakeInput(womType: WomType): JsValue = womType match {
//    case WomStringType => JsString("hio")
//    case WomIntegerType | WomFloatType => JsNumber(25)
//    case WomUnlistedDirectoryType => JsString("gs://bucket/path")
//    case WomSingleFileType => JsString("gs://bucket/path/file.txt")
//    case WomBooleanType => JsBoolean(true)
//    case _: WomOptionalType => JsNull
//    case WomMapType(_, valueType) => JsObject(Map("0" -> fakeInput(valueType)))
//    case WomArrayType(innerType) => JsArray(Vector(fakeInput(innerType)))
//    case WomPairType(leftType, rightType) => JsObject(Map("left" -> fakeInput(leftType), "right" -> fakeInput(rightType)))
//  }
}
