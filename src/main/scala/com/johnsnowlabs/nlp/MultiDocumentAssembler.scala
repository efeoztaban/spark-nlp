/*
 * Copyright 2017-2022 John Snow Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.johnsnowlabs.nlp

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.{Param, ParamMap, StringArrayParam}
import org.apache.spark.ml.util.{DefaultParamsReadable, DefaultParamsWritable, Identifiable}
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Dataset, Row}
import com.johnsnowlabs.nlp.AnnotatorType._

/** Prepares data into a format that is processable by Spark NLP. This is the entry point for
  * every Spark NLP pipeline. The `MultiDocumentAssembler` can read either a `String` column or an
  * `Array[String]`. Additionally, [[MultiDocumentAssembler.setCleanupMode]] can be used to
  * pre-process the text (Default: `disabled`). For possible options please refer the parameters
  * section.
  *
  * For more extended examples on document pre-processing see the
  * [[https://github.com/JohnSnowLabs/spark-nlp-workshop/blob/master/tutorials/Certification_Trainings/Public/2.Text_Preprocessing_with_SparkNLP_Annotators_Transformers.ipynb Spark NLP Workshop]].
  *
  * ==Example==
  * {{{
  * import spark.implicits._
  * import com.johnsnowlabs.nlp.MultiDocumentAssembler
  *
  * val data = Seq("Spark NLP is an open-source text processing library.").toDF("text")
  * val multiDocumentAssembler = new MultiDocumentAssembler().setInputCols("text").setOutputCols("document")
  *
  * val result = multiDocumentAssembler.transform(data)
  *
  * result.select("document").show(false)
  * +----------------------------------------------------------------------------------------------+
  * |document                                                                                      |
  * +----------------------------------------------------------------------------------------------+
  * |[[document, 0, 51, Spark NLP is an open-source text processing library., [sentence -> 0], []]]|
  * +----------------------------------------------------------------------------------------------+
  *
  * result.select("document").printSchema
  * root
  *  |-- document: array (nullable = true)
  *  |    |-- element: struct (containsNull = true)
  *  |    |    |-- annotatorType: string (nullable = true)
  *  |    |    |-- begin: integer (nullable = false)
  *  |    |    |-- end: integer (nullable = false)
  *  |    |    |-- result: string (nullable = true)
  *  |    |    |-- metadata: map (nullable = true)
  *  |    |    |    |-- key: string
  *  |    |    |    |-- value: string (valueContainsNull = true)
  *  |    |    |-- embeddings: array (nullable = true)
  *  |    |    |    |-- element: float (containsNull = false)
  * }}}
  *
  * @param uid
  *   required uid for storing annotator to disk
  * @groupname anno Annotator types
  * @groupdesc anno
  *   Required input and expected output annotator types
  * @groupname Ungrouped Members
  * @groupname param Parameters
  * @groupname setParam Parameter setters
  * @groupname getParam Parameter getters
  * @groupname Ungrouped Members
  * @groupprio param  1
  * @groupprio anno  2
  * @groupprio Ungrouped 3
  * @groupprio setParam  4
  * @groupprio getParam  5
  * @groupdesc param
  *   A list of (hyper-)parameter keys this annotator can take. Users can set and get the
  *   parameter values through setters and getters, respectively.
  */
class MultiDocumentAssembler(override val uid: String)
    extends Transformer
    with DefaultParamsWritable
    with HasOutputAnnotatorType {

  def this() = this(Identifiable.randomUID("MultiDocumentAssembler"))

  /** Output Annotator Type: DOCUMENT
    *
    * @group anno
    */
  override val outputAnnotatorType: AnnotatorType = DOCUMENT

  override def copy(extra: ParamMap): Transformer = defaultCopy(extra)

  private type DocumentationContent = Row

  val EMPTY_STR = ""

  /** Name of input annotation cols
    *
    * @group param
    */
  val inputCols: StringArrayParam =
    new StringArrayParam(this, "inputCols", "Name of input annotation cols")

  /** @group setParam */
  def setInputCols(value: Array[String]): this.type = set(inputCols, value)

  /** @group setParam */
  def setInputCols(value: String*): this.type = setInputCols(value.toArray)

  /** @group getParam */
  def getInputCols: Array[String] = $(inputCols)

  /** @group param */
  val outputCols: StringArrayParam =
    new StringArrayParam(this, "outputCols", "Name of output cols")

  /** @group setParam */
  def setOutputCols(value: Array[String]): this.type = set(outputCols, value)

  /** @group setParam */
  def setOutputCols(value: String*): this.type = setOutputCols(value.toArray)

  /** @group getParam */
  def getOutputCols: Array[String] = get(outputCols).getOrElse(getInputCols.map("finished_" + _))

  /** Id column for row reference
    *
    * @group param
    */
  val idCol: Param[String] = new Param[String](this, "idCol", "id column for row reference")

  /** Metadata for document column
    *
    * @group param
    */
  val metadataCol: Param[String] =
    new Param[String](this, "metadataCol", "metadata for document column")

  /** cleanupMode can take the following values:
    *   - `disabled`: keep original. Useful if need to head back to source later
    *   - `inplace`: newlines and tabs into whitespaces, not stringified ones, don't trim
    *   - `inplace_full`: newlines and tabs into whitespaces, including stringified, don't trim
    *   - `shrink`: all whitespaces, newlines and tabs to a single whitespace, but not
    *     stringified, do trim
    *   - `shrink_full`: all whitespaces, newlines and tabs to a single whitespace, stringified
    *     ones too, trim all
    *   - `each`: newlines and tabs to one whitespace each
    *   - `each_full`: newlines and tabs, stringified ones too, to one whitespace each
    *   - `delete_full`: remove stringified newlines and tabs (replace with nothing)
    *
    * @group param
    */
  val cleanupMode: Param[String] = new Param[String](
    this,
    "cleanupMode",
    "possible values: " +
      "disabled, inplace, inplace_full, shrink, shrink_full, each, each_full, delete_full")

  setDefault(cleanupMode -> "disabled")

  /** Id column for row reference
    *
    * @group setParam
    */
  def setIdCol(value: String): this.type = set(idCol, value)

  /** Id column for row reference
    *
    * @group getParam
    */
  def getIdCol: String = $(idCol)

  /** Metadata for document column
    *
    * @group setParam
    */
  def setMetadataCol(value: String): this.type = set(metadataCol, value)

  /** cleanupMode to pre-process text
    *
    * @group setParam
    */
  def setCleanupMode(v: String): this.type = {
    v.trim.toLowerCase() match {
      case "disabled" => set(cleanupMode, "disabled")
      case "inplace" => set(cleanupMode, "inplace")
      case "inplace_full" => set(cleanupMode, "inplace_full")
      case "shrink" => set(cleanupMode, "shrink")
      case "shrink_full" => set(cleanupMode, "shrink_full")
      case "each" => set(cleanupMode, "each")
      case "each_full" => set(cleanupMode, "each_full")
      case "delete_full" => set(cleanupMode, "delete_full")
      case b =>
        throw new IllegalArgumentException(s"Special Character Cleanup supports only: " +
          s"disabled, inplace, inplace_full, shrink, shrink_full, each, each_full, delete_full. Received: $b")
    }
  }

  /** cleanupMode to pre-process text
    *
    * @group getParam
    */
  def getCleanupMode: String = $(cleanupMode)

  /** Metadata for document column
    *
    * @group getParam
    */
  def getMetadataCol: String = $(metadataCol)

  private[nlp] def assemble(text: String, metadata: Map[String, String]): Seq[Annotation] = {

    val _text = Option(text).getOrElse(EMPTY_STR)

    val possiblyCleaned = $(cleanupMode) match {
      case "disabled" => _text
      case "inplace" => _text.replaceAll("\\s", " ")
      case "inplace_full" => _text.replaceAll("\\s|(?:\\\\r)?(?:\\\\n)|(?:\\\\t)", " ")
      case "shrink" => _text.trim.replaceAll("\\s+", " ")
      case "shrink_full" => _text.trim.replaceAll("\\s+|(?:\\\\r)*(?:\\\\n)+|(?:\\\\t)+", " ")
      case "each" => _text.replaceAll("\\s[\\n\\t]", " ")
      case "each_full" => _text.replaceAll("\\s(?:\\n|\\t|(?:\\\\r)?(?:\\\\n)|(?:\\\\t))", " ")
      case "delete_full" => _text.trim.replaceAll("(?:\\\\r)?(?:\\\\n)|(?:\\\\t)", "")
      case b =>
        throw new IllegalArgumentException(s"Special Character Cleanup supports only: " +
          s"disabled, inplace, inplace_full, shrink, shrink_full, each, each_full, delete_full. Received: $b")
    }
    try {
      Seq(
        Annotation(outputAnnotatorType, 0, possiblyCleaned.length - 1, possiblyCleaned, metadata))
    } catch {
      case _: Exception =>
        /*
         * when there is a null in the row
         * it outputs an empty Annotation
         * */
        Seq.empty[Annotation]
    }

  }

  private[nlp] def assembleFromArray(texts: Seq[String]): Seq[Annotation] = {
    texts.zipWithIndex.flatMap { case (text, idx) =>
      assemble(text, Map("sentence" -> idx.toString))
    }
  }

  private def dfAssemble: UserDefinedFunction = udf {
    (text: String, id: String, metadata: Map[String, String]) =>
      assemble(text, metadata ++ Map("id" -> id, "sentence" -> "0"))
  }

  private def dfAssembleOnlyId: UserDefinedFunction = udf { (text: String, id: String) =>
    assemble(text, Map("id" -> id, "sentence" -> "0"))
  }

  private def dfAssembleNoId: UserDefinedFunction = udf {
    (text: String, metadata: Map[String, String]) =>
      assemble(text, metadata ++ Map("sentence" -> "0"))
  }

  private def dfAssembleNoExtras: UserDefinedFunction = udf { text: String =>
    assemble(text, Map("sentence" -> "0"))
  }

  private def dfAssemblyFromArray: UserDefinedFunction = udf { texts: Seq[String] =>
    assembleFromArray(texts)
  }

  /** requirement for pipeline transformation validation. It is called on fit() */
  override final def transformSchema(schema: StructType): StructType = {
    val metadataBuilder: MetadataBuilder = new MetadataBuilder()
    metadataBuilder.putString("annotatorType", outputAnnotatorType)
    val outputColsStructure = getOutputCols.map(outpuCol =>
      StructField(
        outpuCol,
        ArrayType(Annotation.dataType),
        nullable = false,
        metadataBuilder.build))
    val outputFields = schema.fields ++ outputColsStructure

    StructType(outputFields)
  }

  override def transform(dataset: Dataset[_]): DataFrame = {
    val metadataBuilder: MetadataBuilder = new MetadataBuilder()
    metadataBuilder.putString("annotatorType", outputAnnotatorType)
    require(
      getInputCols.length == getOutputCols.length,
      "inputCols and outputCols length must match")
    val cols = getInputCols.zip(getOutputCols)
    var flattened = dataset
    cols.foreach { case (inputCol, outputCol) =>
      flattened = {
        val documentAnnotations =
          if (flattened.schema.fields
              .find(_.name == inputCol)
              .getOrElse(throw new IllegalArgumentException(
                s"Dataset does not have any '$inputCol' column"))
              .dataType == ArrayType(StringType, containsNull = false))
            dfAssemblyFromArray(flattened.col(inputCol))
          else if (get(idCol).isDefined && get(metadataCol).isDefined)
            dfAssemble(
              flattened.col(inputCol),
              flattened.col(getIdCol),
              flattened.col(getMetadataCol))
          else if (get(idCol).isDefined)
            dfAssembleOnlyId(flattened.col(inputCol), flattened.col(getIdCol))
          else if (get(metadataCol).isDefined)
            dfAssembleNoId(flattened.col(inputCol), flattened.col(getMetadataCol))
          else
            dfAssembleNoExtras(flattened.col(inputCol))
        flattened.withColumn(outputCol, documentAnnotations.as(outputCol, metadataBuilder.build))
      }

    }
    flattened.toDF()
  }
}

/** This is the companion object of [[MultiDocumentAssembler]]. Please refer to that class for the
  * documentation.
  */
object MultiDocumentAssembler extends DefaultParamsReadable[MultiDocumentAssembler]
