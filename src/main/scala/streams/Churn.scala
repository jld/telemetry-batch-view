package telemetry.streams

import awscala._
import awscala.s3._
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.generic.{GenericRecord, GenericRecordBuilder}
import org.json4s.jackson.JsonMethods._
import scala.collection.JavaConverters._
import telemetry.SimpleDerivedStream
import telemetry.heka.{HekaFrame, Message}
import org.json4s.JsonAST.{JValue, JNothing, JInt}

case class Churn(prefix: String) extends SimpleDerivedStream{
  override def filterPrefix: String = prefix
  override def streamName: String = "telemetry"
  
  override def buildSchema: Schema = {
    SchemaBuilder
      .record("System").fields
      .name("clientId").`type`().stringType().noDefault()
      .name("sampleId").`type`().intType().noDefault()
      .name("channel").`type`().stringType().noDefault() // appUpdateChannel
      .name("normalizedChannel").`type`().stringType().noDefault() // normalizedChannel
      .name("country").`type`().stringType().noDefault() // geoCountry
      .name("profileCreationDate").`type`().nullable().intType().noDefault() // environment/profile/creationDate
      .name("submissionDate").`type`().stringType().noDefault()
      // See bug 1232050
      .name("syncConfigured").`type`().nullable().booleanType().noDefault() // WEAVE_CONFIGURED
//      .name("syncCountDesktop").`type`().nullable().intType().noDefault() // WEAVE_DEVICE_COUNT_DESKTOP
//      .name("syncCountMobile").`type`().nullable().intType().noDefault() // WEAVE_DEVICE_COUNT_MOBILE
      
      .name("version").`type`().stringType().noDefault() // appVersion
      .endRecord
  }

  def booleanHistogramToBoolean(h: JValue): Option[Boolean] = {
    // TODO
//    h match {
//      case JNothing => None
//      case _ => {...}
//    }
    if (h == JNothing) {
      None
    } else {
      var one = h \ "1"
      if (one != JNothing && one.asInstanceOf[Int] > 0) {
        Some(true)
      } else {
        var zero = h \ "0"
        if (zero != JNothing && zero.asInstanceOf[Int] > 0) {
          Some(false)
        } else {
          None
        }
      }
    }
  }
  
  def enumHistogramToCount(h: JValue): Option[Long] = {
    // FIXME
    Some(5)
  }

  override def buildRecord(message: Message, schema: Schema): Option[GenericRecord] ={
    val fields = HekaFrame.fields(message)
    val profile = parse(fields.getOrElse("environment.profile", "{}").asInstanceOf[String])
    val histograms = parse(fields.getOrElse("payload.histograms", "{}").asInstanceOf[String])
    
    val weaveConfigured = booleanHistogramToBoolean(histograms \ "WEAVE_CONFIGURED")
//    val weaveDesktop = enumHistogramToCount(histograms \ "WEAVE_DEVICE_COUNT_DESKTOP")
//    val weaveMobile = enumHistogramToCount(histograms \ "WEAVE_DEVICE_COUNT_MOBILE")
//    println("Processing one record...")
    val root = new GenericRecordBuilder(schema)
      .set("clientId", fields.getOrElse("clientId", None) match {
             case x: String => x
             case _ => {
               println("skip: no clientid")
               return None
             }
           })
      .set("sampleId", fields.getOrElse("sampleId", None) match {
             case x: Long => x
             case x: Double => x.toLong
             case _ => {
               println("skip: no sampleid")
               return None
             }
           })
      .set("channel", fields.getOrElse("appUpdateChannel", None) match {
             case x: String => x
             case _ => ""
           })
      .set("normalizedChannel", fields.getOrElse("normalizedChannel", None) match {
             case x: String => x
             case _ => ""
           })
      .set("country", fields.getOrElse("geoCountry", None) match {
             case x: String => x
             case _ => ""
           })
      .set("version", fields.getOrElse("appVersion", None) match {
             case x: String => x
             case _ => ""
           })
      .set("submissionDate", fields.getOrElse("submissionDate", None) match {
             case x: String => x
             case _ => {
               println("skip: no subdate")
               return None
             }
           })
      .set("profileCreationDate", (profile \ "creationDate") match {
             case JNothing => null
             case x: JInt => x.num.toLong
             case _ => {
               println("profile creation date was not an int")
               null
             }
      })
      .set("syncConfigured", weaveConfigured match {
             case Some(x) => x
             case _ => null
           })
      // TODO
//      .set("syncCountDesktop", weaveDesktop match {
//             case Some(x) => x
//             case _ => null
//           })
//      .set("syncCountMobile", weaveMobile match {
//             case Some(x) => x
//             case _ => null
//           })
      .build
//    println("Matched one record")
    Some(root)
  }
}
