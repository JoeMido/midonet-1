/*
 * Copyright 2016 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.cluster.data.storage

import java.util.UUID

import scala.util.control.NonFatal

import org.midonet.cluster.data.storage.StateTableEncoder.PersistentVersion
import org.midonet.packets.{IPv4Addr, MAC}

object StateTableEncoder {
    final val PersistentVersion = Int.MaxValue

    trait MacToIdEncoder extends StateTableEncoder[MAC, UUID] {
        @inline protected override def encodeKey(mac: MAC): String = {
            mac.toString
        }

        @inline protected override def decodeKey(string: String): MAC = {
            MAC.fromString(string)
        }

        @inline protected override def encodeValue(id: UUID): String = {
            id.toString
        }

        @inline protected override def decodeValue(string: String): UUID = {
            UUID.fromString(string)
        }
    }
    object MacToIdEncoder extends MacToIdEncoder

    trait Ip4ToMacEncoder extends StateTableEncoder[IPv4Addr, MAC] {
        @inline protected override def encodeKey(address: IPv4Addr): String = {
            address.toString
        }

        @inline protected override def decodeKey(string: String): IPv4Addr = {
            IPv4Addr(string)
        }

        @inline protected override def encodeValue(mac: MAC): String = {
            mac.toString
        }

        @inline protected override def decodeValue(string: String): MAC = {
            MAC.fromString(string)
        }
    }
    object Ip4ToMacEncoder extends Ip4ToMacEncoder

    trait MacToIp4Encoder extends StateTableEncoder[MAC, IPv4Addr] {
        @inline protected override def encodeKey(mac: MAC): String = {
            mac.toString
        }

        @inline protected override def decodeKey(string: String): MAC = {
            MAC.fromString(string)
        }

        @inline protected override def encodeValue(address: IPv4Addr): String = {
            address.toString
        }

        @inline protected override def decodeValue(string: String): IPv4Addr = {
            IPv4Addr(string)
        }
    }
    object MacToIp4Encoder extends MacToIp4Encoder
}

/**
  * A base trait that handles the encoding for a [[StateTable]] with the
  * given generic arguments.
  */
trait StateTableEncoder[K, V] {

    protected def encodeKey(key: K): String

    protected def decodeKey(string: String): K

    protected def encodeValue(value: V): String

    protected def decodeValue(string: String): V

    /**
      * @return The encoded string for the specified key, value and version
      *         3-tuple.
      */
    def encodePath(key: K, value: V, version: Int): String = {
        s"/${encodeKey(key)},${encodeValue(value)}," +
        s"${"%010d".format(version)}"
    }

    /**
      * @return The encoded string for the specified persistent key-value entry.
      */
    def encodePersistentPath(key: K, value: V): String = {
        encodePath(key, value, PersistentVersion)
    }

    /**
      * @return The decoded key, value and version 3-tuple for the specified
      *         path.
      */
    def decodePath(path: String): (K, V, Int) = {
        val string = if (path.startsWith("/")) path.substring(1)
                     else path
        val tokens = string.split(",")
        if (tokens.length != 3)
            return null
        try {
            (decodeKey(tokens(0)), decodeValue(tokens(1)),
                Integer.parseInt(tokens(2)))
        } catch {
            case NonFatal(_) => null
        }
    }

}
