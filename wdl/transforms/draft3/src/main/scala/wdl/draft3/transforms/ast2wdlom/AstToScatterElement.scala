package wdl.draft3.transforms.ast2wdlom

import cats.syntax.apply._
import cats.syntax.either._
import common.validation.ErrorOr.ErrorOr
import wdl.draft3.parser.WdlParser.{Ast, Terminal}
import wdl.draft3.transforms.ast2wdlom.EnhancedDraft3Ast._
import wdl.model.draft3.elements._

object AstToScatterElement {
  def convert(ast: Ast): ErrorOr[ScatterElement] = {

    val scatterVariableValidation: ErrorOr[Terminal] = ast.getAttributeAs[Terminal]("item").toValidated

    val scatterCollectionExpressionValidation: ErrorOr[ExpressionElement] = ast.getAttributeAs[ExpressionElement]("collection").toValidated
    val bodyValidation: ErrorOr[Vector[WorkflowGraphElement]] = ast.getAttributeAsVector[WorkflowGraphElement]("body").toValidated

    (scatterVariableValidation, scatterCollectionExpressionValidation, bodyValidation) mapN { (variable, collection, body) =>
      val scatterName = s"ScatterAt${variable.line}_${variable.col}"
      ScatterElement(scatterName, collection, variable.source_string, body)
    }
  }
}
