/*
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
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

package org.fusesource.fabric.launcher.api

import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.util.StreamReaderDelegate
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URL
import java.util.Properties
import org.fusesource.fabric.launcher.internal.FilterSupport

/**
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object XmlCodec {

  private final val factory: XMLInputFactory = XMLInputFactory.newInstance
  @volatile
  var _context: JAXBContext = null


  /**
   * Changes ${property} with values from a properties object
   */
  class PropertiesFilter(parent: XMLStreamReader, val props: Properties) extends StreamReaderDelegate(parent) {
    override def getAttributeValue(index: Int): String = {
      import FilterSupport._
      return filter(super.getAttributeValue(index), props)
    }
  }

  private def context: JAXBContext = {
    var rc: JAXBContext = _context
    if (rc == null) {
      rc = ({
        _context = createContext; _context
      })
    }
    return rc
  }

  private def createContext: JAXBContext = {
    var packages: String = "io.fabric.launcher.api"
    return JAXBContext.newInstance(packages)
  }

  def decode[T](t : Class[T], url: URL): T = {
    return decode(t, url, null)
  }

  def decode[T](t : Class[T], url: URL, props: Properties): T = {
    return decode(t, url.openStream, props)
  }

  def decode[T](t : Class[T], is: InputStream): T = {
    return decode(t, is, null)
  }

  def decode[T](t : Class[T], is: InputStream, props: Properties): T = {
    if (is == null) {
      throw new IllegalArgumentException("input stream was null")
    }
    try {
      var reader: XMLStreamReader = factory.createXMLStreamReader(is)
      if (props != null) {
        reader = new XmlCodec.PropertiesFilter(reader, props)
      }
      var unmarshaller: Unmarshaller = context.createUnmarshaller
      return t.cast(unmarshaller.unmarshal(reader))
    }
    finally {
      is.close
    }
  }

  def encode[T](in: T, os: OutputStream, format: Boolean): Unit = {
    var marshaller: Marshaller = context.createMarshaller
    if (format) {
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    }
    marshaller.marshal(in, new OutputStreamWriter(os))
  }


}