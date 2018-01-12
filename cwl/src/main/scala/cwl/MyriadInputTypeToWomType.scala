package cwl

import cwl.CwlType.CwlType
import cwl.command.ParentName
import mouse.all._
import shapeless.Poly1
import wom.types._


object MyriadInputTypeToWomType extends Poly1 {

  implicit def m = at[MyriadInputInnerType] {_.fold(MyriadInputInnerTypeToWomType)}

  // An array of type means "this input value can be in any of those types"
  implicit def am = at[Array[MyriadInputInnerType]] { types =>
    // If one of the type is "null", it means this is an optional type
    types.partition(_.select[String].contains("null")) match {
        // If there's no non null type, create a coproduct of the types
      case (nullTypes, nonNullTypes) if nullTypes.isEmpty =>
        WomCoProductType(nonNullTypes.map(_.fold(MyriadInputInnerTypeToWomType)).toList)
        // If there's a null type and a single non null type, it's a classic WomOptionalType
      case (nullTypes, Array(singleNonNullType)) if nullTypes.nonEmpty =>
        WomOptionalType(singleNonNullType.fold(MyriadInputInnerTypeToWomType))
        // If there's a null type and multiple non null types, it's a WomOptionalType(WomCoProductType)
      case (nullTypes, nonNullTypes) if nullTypes.nonEmpty =>
        WomOptionalType(WomCoProductType(nonNullTypes.map(_.fold(MyriadInputInnerTypeToWomType)).toList))
    }
  }
}

object MyriadInputInnerTypeToWomType extends Poly1 {
  import Case._

  def ex(component: String) = throw new RuntimeException(s"input type $component not yet suported by WOM!")

  implicit def ct: Aux[CwlType, WomType] = at[CwlType]{
    cwl.cwlTypeToWomType
  }
  implicit def irs: Aux[InputRecordSchema, WomType] = at[InputRecordSchema]{
    case InputRecordSchema(_, Some(fields), _) =>
      val typeMap = fields.map({ field =>
          FullyQualifiedName(field.name)(ParentName.empty).id -> field.`type`.fold(MyriadInputTypeToWomType)
      }).toMap
      WomCompositeType(typeMap)
    case irs => irs.toString |> ex
  }

  implicit def ies: Aux[InputEnumSchema, WomType] = at[InputEnumSchema]{
    _.toString |> ex
  }

  implicit def ias: Aux[InputArraySchema, WomType] = at[InputArraySchema]{
    ias =>
      val arrayType: WomType = ias.items.fold(MyriadInputTypeToWomType)

      WomArrayType(arrayType)
  }
  implicit def s: Aux[String, WomType] = at[String]{
    _.toString |> ex
  }

}
