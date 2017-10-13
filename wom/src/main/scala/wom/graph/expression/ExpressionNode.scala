package wom.graph.expression

import cats.instances.list._
import cats.syntax.traverse._
import cats.syntax.validated._
import lenthall.Checked
import lenthall.validation.ErrorOr.{ErrorOr, ShortCircuitingFlatMap}
import lenthall.validation.Validation._
import shapeless.Coproduct
import wom.expression.{IoFunctionSet, WomExpression}
import wom.graph.CallNode.InputDefinitionPointer
import wom.graph.GraphNodePort.{ConnectedInputPort, GraphNodeOutputPort, InputPort, OutputPort}
import wom.graph.{GraphNode, GraphNodePort, WomIdentifier}
import wom.types.WdlType
import wom.values.WdlValue

/**
  * Encapsulates a WomExpression with input ports connected to the expression's dependencies.
  */
abstract class ExpressionNode(override val identifier: WomIdentifier,
                     val womExpression: WomExpression,
                     val womType: WdlType,
                     val inputMapping: Map[String, InputPort]) extends GraphNode {
  val singleExpressionOutputPort = GraphNodeOutputPort(identifier, womType, this)
  override val outputPorts: Set[GraphNodePort.OutputPort] = Set(singleExpressionOutputPort)
  override val inputPorts = inputMapping.values.toSet
  
  /**
    * Can be used to use this node as an InputDefinitionPointer
    */
  lazy val inputDefinitionPointer = Coproduct[InputDefinitionPointer](singleExpressionOutputPort: OutputPort)

  // Again an instance of not so pretty flatMapping with mix of ErrorOrs, Eithers and Tries..
  // TODO: This should return an EitherT or whatever we decide we want to use to package Exceptions + Nel[String]
  /**
    * Evaluates the expression and coerces it.
    */
  def evaluateAndCoerce(inputs: Map[String, WdlValue], ioFunctionSet: IoFunctionSet): Checked[WdlValue] = (for {
    evaluated <- womExpression.evaluateValue(inputs, ioFunctionSet)
    coerced <- womType.coerceRawValue(evaluated).toErrorOr
  } yield coerced).toEither
}

object ExpressionNode {
  /**
    * Constructs an ExpressionNode or a subclass of an expression node.
    * Note: the WdlType is the evaluated type derived from the expression.
    */
  type ExpressionNodeConstructor[E <: ExpressionNode] = (WomIdentifier, WomExpression, WdlType, Map[String, InputPort]) => E

  /**
    * Using the passed constructor, attempts to build an expression node from input mappings by linking variable references to other
    * output ports.
    */
  def buildFromConstructor[E <: ExpressionNode](constructor: ExpressionNodeConstructor[E])
  (identifier: WomIdentifier, expression: WomExpression, inputMapping: Map[String, OutputPort]): ErrorOr[E] = {
    val graphNodeSetter = new GraphNode.GraphNodeSetter()

    for {
      combined <- linkWithInputs(graphNodeSetter, expression, inputMapping)
      (evaluatedType, inputPorts) = combined
      expressionNode = constructor(identifier, expression, evaluatedType, inputPorts)
      _ = graphNodeSetter._graphNode = expressionNode
    } yield expressionNode
  }

  /**
    * Attempts to find an output port for all referenced variables in the expression, and created input ports to connect them together.
    */
  private def linkWithInputs(graphNodeSetter: GraphNode.GraphNodeSetter, expression: WomExpression, inputMapping: Map[String, OutputPort]): ErrorOr[(WdlType, Map[String, InputPort])] = {
    def linkInput(input: String): ErrorOr[(String, InputPort)] = inputMapping.get(input) match {
      case Some(upstreamPort) => (input, ConnectedInputPort(input, upstreamPort.womType, upstreamPort, graphNodeSetter.get)).validNel
      case None => s"Expression cannot be connected without the input $input (provided: ${inputMapping.toString})".invalidNel
    }

    import lenthall.validation.ErrorOr.ShortCircuitingFlatMap
    for {
      linkedInputList <- expression.inputs.toList traverse linkInput
      linkedInputs = linkedInputList.toMap
      inputTypes = linkedInputs map { case (k, v) => k -> v.womType }
      evaluatedType <- expression.evaluateType(inputTypes)
    } yield (evaluatedType, linkedInputs)
  }
}