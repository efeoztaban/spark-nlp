#  Copyright 2017-2022 John Snow Labs
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
"""Contains classes for CamemBertForTokenClassification."""
from sparknlp.common import *


class CamemBertForTokenClassification(AnnotatorModel,
                                      HasCaseSensitiveProperties,
                                      HasBatchedAnnotate,
                                      HasEngine):
    """CamemBertForTokenClassification can load CamemBERT Models with a token
    classification head on top (a linear layer on top of the hidden-states
    output) e.g. for Named-Entity-Recognition (NER) tasks.

    Pretrained models can be loaded with :meth:`.pretrained` of the companion
    object:

    >>> token_classifier = CamemBertForTokenClassification.pretrained() \\
    ...     .setInputCols(["token", "document"]) \\
    ...     .setOutputCol("label")

    The default model is ``"camembert_base_token_classifier_wikiner"``, if no
    name is provided.

    For available pretrained models please see the `Models Hub
    <https://nlp.johnsnowlabs.com/models?task=Named+Entity+Recognition>`__.
    To see which models are compatible and how to import them see
    `Import Transformers into Spark NLP 🚀
    <https://github.com/JohnSnowLabs/spark-nlp/discussions/5669>`_.

    ====================== ======================
    Input Annotation types Output Annotation type
    ====================== ======================
    ``DOCUMENT, TOKEN``    ``NAMED_ENTITY``
    ====================== ======================

    Parameters
    ----------
    batchSize
        Batch size. Large values allows faster processing but requires more
        memory, by default 8
    caseSensitive
        Whether to ignore case in tokens for embeddings matching, by default
        True
    configProtoBytes
        ConfigProto from tensorflow, serialized into byte array.
    maxSentenceLength
        Max sentence length to process, by default 128

    Examples
    --------
    >>> import sparknlp
    >>> from sparknlp.base import *
    >>> from sparknlp.annotator import *
    >>> from pyspark.ml import Pipeline
    >>> documentAssembler = DocumentAssembler() \\
    ...     .setInputCol("text") \\
    ...     .setOutputCol("document")
    >>> tokenizer = Tokenizer() \\
    ...     .setInputCols(["document"]) \\
    ...     .setOutputCol("token")
    >>> tokenClassifier = CamemBertForTokenClassification.pretrained() \\
    ...     .setInputCols(["token", "document"]) \\
    ...     .setOutputCol("label") \\
    ...     .setCaseSensitive(True)
    >>> pipeline = Pipeline().setStages([
    ...     documentAssembler,
    ...     tokenizer,
    ...     tokenClassifier
    ... ])
    >>> data = spark.createDataFrame([["george washington est allé à washington"]]).toDF("text")
    >>> result = pipeline.fit(data).transform(data)
    >>> result.select("label.result").show(truncate=False)
    +------------------------------+
    |result                        |
    +------------------------------+
    |[I-PER, I-PER, O, O, O, I-LOC]|
    +------------------------------+
    """
    name = "CamemBertForTokenClassification"

    inputAnnotatorTypes = [AnnotatorType.DOCUMENT, AnnotatorType.TOKEN]

    outputAnnotatorType = AnnotatorType.NAMED_ENTITY

    maxSentenceLength = Param(Params._dummy(),
                              "maxSentenceLength",
                              "Max sentence length to process",
                              typeConverter=TypeConverters.toInt)

    configProtoBytes = Param(Params._dummy(),
                             "configProtoBytes",
                             "ConfigProto from tensorflow, serialized into byte array. Get with config_proto.SerializeToString()",
                             TypeConverters.toListInt)

    def getClasses(self):
        """
        Returns labels used to train this model
        """
        return self._call_java("getClasses")

    def setConfigProtoBytes(self, b):
        """Sets configProto from tensorflow, serialized into byte array.

        Parameters
        ----------
        b : List[int]
            ConfigProto from tensorflow, serialized into byte array
        """
        return self._set(configProtoBytes=b)

    def setMaxSentenceLength(self, value):
        """Sets max sentence length to process, by default 128.

        Parameters
        ----------
        value : int
            Max sentence length to process
        """
        return self._set(maxSentenceLength=value)

    @keyword_only
    def __init__(self, classname="com.johnsnowlabs.nlp.annotators.classifier.dl.CamemBertForTokenClassification",
                 java_model=None):
        super(CamemBertForTokenClassification, self).__init__(
            classname=classname,
            java_model=java_model
        )
        self._setDefault(
            batchSize=8,
            maxSentenceLength=128,
            caseSensitive=True
        )

    @staticmethod
    def loadSavedModel(folder, spark_session):
        """Loads a locally saved model.

        Parameters
        ----------
        folder : str
            Folder of the saved model
        spark_session : pyspark.sql.SparkSession
            The current SparkSession

        Returns
        -------
        CamemBertForTokenClassification
            The restored model
        """
        from sparknlp.internal import _CamemBertForTokenClassification
        jModel = _CamemBertForTokenClassification(folder, spark_session._jsparkSession)._java_obj
        return CamemBertForTokenClassification(java_model=jModel)

    @staticmethod
    def pretrained(name="camembert_base_token_classifier_wikiner", lang="en", remote_loc=None):
        """Downloads and loads a pretrained model.

        Parameters
        ----------
        name : str, optional
            Name of the pretrained model, by default
            "camembert_base_token_classifier_wikiner"
        lang : str, optional
            Language of the pretrained model, by default "en"
        remote_loc : str, optional
            Optional remote address of the resource, by default None. Will use
            Spark NLPs repositories otherwise.

        Returns
        -------
        CamemBertForTokenClassification
            The restored model
        """
        from sparknlp.pretrained import ResourceDownloader
        return ResourceDownloader.downloadModel(CamemBertForTokenClassification, name, lang, remote_loc)
