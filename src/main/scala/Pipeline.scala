import WebApiDocumentationExample.logger
import amf.core.remote.Vendor
import com.typesafe.scalalogging.Logger
import helpers.Conversions.Url
import helpers.{Amf, Rdf}
import org.apache.jena.rdf.model.Model

import scala.concurrent.{ExecutionContext, Future}

//noinspection SameParameterValue
trait Pipeline {
  implicit val logger: Logger       = Logger[this.type]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val resources  = "file://src/main/resources"
  val raml       = s"$resources/web-apis/raml"
  val ontologies = s"$resources/web-apis/ontologies"
  val queries    = s"$resources/web-apis/queries"

  protected def obtainModelFromAmf(fileUrl: String, vendor: Vendor): Future[Model] = {
    for {
      parsed   <- Amf.parse(fileUrl, vendor)
      resolved <- Amf.resolve(parsed, vendor)
      _        <- Amf.render(resolved, fileUrl.withExtension(".jsonld"))
      rdf      <- Rdf.IO.read(fileUrl.noProtocol.withExtension(".jsonld"))
    } yield {
      rdf
    }
  }

  protected def obtainInferenceModelFrom(model: Model, ontologyUrl: String, fileUrl: String): Future[Model] = {
    for {
      ontology       <- Rdf.IO.read(ontologyUrl.noProtocol, lang = "TTL")
      inferenceModel <- Rdf.Inference.default(ontology, model)
      _              <- Rdf.IO.write(inferenceModel, fileUrl.noProtocol.withExtension(".inference.jsonld"), "JSON-LD", fileUrl)
    } yield {
      inferenceModel
    }
  }

  protected def runQueriesOn(model: Model, fileUrl: String): Future[Unit]

  protected def run(fileUrl: String, ontologyUrl: String, vendor: Vendor): Future[Unit] = {
    for {
      model          <- obtainModelFromAmf(fileUrl, vendor)          // generate basic graph
      inferenceModel <- obtainInferenceModelFrom(model, ontologyUrl, fileUrl) // enrich graph
      _              <- runQueriesOn(inferenceModel, fileUrl)        // query graph
    } yield {
      println()
    }
  }

}
