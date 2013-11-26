package utils.pdf

import java.io._
import java.net.MalformedURLException
import java.net.URL
import org.w3c.dom.Document
import org.w3c.tidy.Tidy
import org.xhtmlrenderer.pdf.ITextFSImage
import org.xhtmlrenderer.pdf.ITextFontResolver
import org.xhtmlrenderer.pdf.ITextOutputDevice
import org.xhtmlrenderer.pdf.ITextRenderer
import org.xhtmlrenderer.pdf.ITextUserAgent
import org.xhtmlrenderer.resource.CSSResource
import org.xhtmlrenderer.resource.ImageResource
import org.xhtmlrenderer.resource.XMLResource
import play.Logger
import play.api.Play
import play.api.templates.Html
import play.api._
import play.api.mvc._
import play.api.mvc.Results.Status
import com.lowagie.text.DocumentException
import com.lowagie.text.Image
import com.lowagie.text.pdf.BaseFont
import org.apache.commons.io.IOUtils
import play.api.mvc.WithHeaders
import scala.collection.JavaConversions._
import play.api.Play




object PDF {
  
	private val APP_FONT_DIR = "/conf/fonts"
  
  
	class MyUserAgent(val outputDevice:ITextOutputDevice ) extends ITextUserAgent(outputDevice) {

		

		// @Override
		override def getImageResource(uri:String):ImageResource = {
			
			Play.current.resourceAsStream(uri).map { stream =>

				try {
					val image = Image.getInstance(getData(stream))
					scaleToOutputResolution(image)
					new ImageResource(new ITextFSImage(image))
				} catch {
				  case e:Exception => 
				    Logger.error("fetching image " + uri, e)
					throw new RuntimeException(e)
				}			  
			  
			}.getOrElse( super.getImageResource(uri) )
			

		}

//		@Override
		override def getCSSResource(uri:String):CSSResource = {
			try {
				// uri is in fact a complete URL
				val path = new URL(uri).getPath()
				
				Play.current.resourceAsStream( path).map{ stream =>
					new CSSResource(stream)
				}.getOrElse{
					super.getCSSResource(uri)
				}
			} catch {
			  case e:MalformedURLException => 
			    Logger.error("fetching css " + uri, e)
				throw new RuntimeException(e)
			}
		}

//		@Override
		override def getBinaryResource(uri:String) = {
			
			Play.current.resourceAsStream(uri).map { stream =>
				
				val baos = new ByteArrayOutputStream()
				try {
					copy(stream, baos)
				} catch {
				  case e:IOException =>
				    Logger.error("fetching binary " + uri, e)
					throw new RuntimeException(e)
				}
				
				baos.toByteArray()
			}.getOrElse {
				super.getBinaryResource(uri)
			}
		}

//		@Override
		override def getXMLResource(uri:String):XMLResource = {
			Play.current.resourceAsStream(uri).map { stream =>
				XMLResource.load(stream)
			}.getOrElse {
				super.getXMLResource(uri)
			}
		}

		def scaleToOutputResolution(image:Image) = {
			val factor = getSharedContext().getDotsPerPixel()
			
			image.scaleAbsolute(image.getPlainWidth() * factor, image.getPlainHeight() * factor)
		}

		def getData(stream:InputStream) = {
			val baos = new ByteArrayOutputStream()
			copy(stream, baos)
			baos.toByteArray()
		}

		def copy(is:InputStream, os:OutputStream) = {
			IOUtils.copy(is, os)
		}
	}  
  
	

	private def tidify(body:String):String = {
		val tidy = new Tidy()
		tidy.setXHTML(true)
		val writer = new StringWriter()
		tidy.parse(new StringReader(body), writer)
		
		writer.getBuffer().toString()
	}

	def Ok(html:Html)( documentBaseURL:String ) = {
		val pdf = toBytes(tidify( html.toString ))( documentBaseURL )
		Results.Ok(pdf).
			as("application/pdf").
			withHeaders( "Content-Length" -> pdf.length.toString )
	}
	
	
	def toBytes(html:Html, request:RequestHeader ):Array[Byte] = 
		toBytes(tidify(html.toString), request )

	
	def toBytes(htmlString:String, request:RequestHeader ):Array[Byte] = {
	  val isSecure:Boolean = false
	  val baseURL = "http" + (if (isSecure) "s" else "") + "://" + request.host + "/"
	  toBytes(htmlString)(baseURL)
	}	

	def toBytes(html:Html)( documentBaseURL:String):Array[Byte] = 
	  toBytes(tidify(html.toString))( documentBaseURL )

	def toBytes(htmlString:String)(documentBaseURL:String):Array[Byte] = {
		val os = new ByteArrayOutputStream()
		toStream(htmlString, os)( documentBaseURL )
		os.toByteArray()
	}

	def toStream(string:String, os:OutputStream)( documentBaseURL:String) = {
		try {
			val reader = new StringReader(string)
			val renderer = new ITextRenderer()
			
			addFontDirectory(renderer.getFontResolver(), Play.current.path + APP_FONT_DIR)
			
			val myUserAgent = new MyUserAgent( renderer.getOutputDevice())
			
			myUserAgent.setSharedContext(renderer.getSharedContext())
			
			renderer.getSharedContext().setUserAgentCallback(myUserAgent)
			
			val document = XMLResource.load(reader).getDocument()
			renderer.setDocument(document, documentBaseURL)
			renderer.layout()
			renderer.createPDF(os)
		} catch {
		  case e:Exception => Logger.error("Creating document from template", e)
		}
	}

	// throws DocumentException, IOException
	def addFontDirectory(fontResolver:ITextFontResolver, directory:String) = {
	  try {
		  val dir = new java.io.File(directory)
		  
		  for(file <- dir.listFiles if file.getName.toString.toLowerCase endsWith ".ttf"){
			  fontResolver.addFont(file.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
		  }
	  } catch {
	    case e:Exception => 
	  }

	}
}
