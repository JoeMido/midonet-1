/*
 * Copyright 2015 Midokura SARL
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

package org.midonet.southbound.vtep

import java.util.UUID
import java.util.UUID._
import java.util.concurrent.Executor

import scala.concurrent.duration._
import scala.util.Random

import org.opendaylight.ovsdb.lib.schema.DatabaseSchema

import org.midonet.cluster.data.vtep.model._
import org.midonet.packets.{IPv4Addr, MAC}
import org.midonet.southbound.vtep.OvsdbOperations._
import org.midonet.southbound.vtep.VtepEntryUpdate._
import org.midonet.southbound.vtep.mock.InMemoryOvsdbVtep
import org.midonet.southbound.vtep.schema._
import org.midonet.util.concurrent.{CallingThreadExecutionContext, _}

object OvsdbVtepBuilder {

    private val random = new Random()

    implicit def asVtepBuilder(vtep: InMemoryOvsdbVtep): OvsdbVtepBuilder = {
        new OvsdbVtepBuilder(vtep)
    }

    def randPhysLocatorAdditions(n: Int)
    : Set[VtepEntryUpdate[PhysicalLocator]] = {
        (1 to n).map { _ =>
            addition(PhysicalLocator(randomUUID(), IPv4Addr.random))
         }.toSet
    }

    def randPhysLocatorRemovals(n: Int)
    : Set[VtepEntryUpdate[PhysicalLocator]] = {
        (1 to n).map { _ =>
            removal(PhysicalLocator(randomUUID, IPv4Addr.random))
        }.toSet
    }

    def randPhysLocators(n: Int) = {
        (1 to n).map { _ =>
            PhysicalLocator(randomUUID, IPv4Addr.random)
        }.toSet
    }

}

class OvsdbVtepBuilder(val vtep: InMemoryOvsdbVtep) extends AnyVal {

    private def schema: DatabaseSchema = {
        val executor = CallingThreadExecutionContext.asInstanceOf[Executor]
        getDbSchema(vtep.getHandle.get.client,
                    OvsdbOperations.DbHardwareVtep)(executor).await(5 seconds)
    }

    def endPoint: VtepEndPoint = vtep.endPoint

    def createPhysicalPort(id: UUID = UUID.randomUUID(),
                           portName: String = "",
                           portDescription: String = ""): PhysicalPort = {
        PhysicalPort(id, portName, portDescription)
    }

    def createPhysicalSwitch(id: UUID = UUID.randomUUID(),
                             vxlanIp: IPv4Addr = IPv4Addr.random,
                             vtepName: String = "",
                             vtepDescription: String = "",
                             ports: Seq[PhysicalPort] = Seq.empty)
    : PhysicalSwitch = {
        val psTable = new PhysicalSwitchTable(schema)
        val portTable = new PhysicalPortTable(schema)
        val ps = PhysicalSwitch(id, vtepName, vtepDescription,
                                ports.map(_.uuid).toSet, Set(endPoint.mgmtIp),
                                Set(vxlanIp))
        vtep.putEntry(psTable, ps)
        ports.foreach(p => vtep.putEntry(portTable, p))
        ps
    }

    def createLogicalSwitch(id: UUID = UUID.randomUUID(),
                            tunnelKey: Int = OvsdbVtepBuilder.random.nextInt(4096),
                            lsDescription: String = ""): LogicalSwitch = {
        val lsName = LogicalSwitch.networkIdToLogicalSwitchName(UUID.randomUUID())
        val lsTable = new LogicalSwitchTable(schema)
        val ls = new LogicalSwitch(id, lsName, tunnelKey, lsDescription)
        vtep.putEntry(lsTable, ls)
        ls
    }

    def createLocalUcastMac(ls: UUID,
                            mac: String = MAC.random().toString,
                            ip: IPv4Addr = IPv4Addr.random,
                            locator: String = UUID.randomUUID.toString): UcastMac = {
        val uLocalTable = new UcastMacsLocalTable(schema)
        val uMac = UcastMac(UUID.randomUUID, ls, mac, ip, locator)
        vtep.putEntry(uLocalTable, uMac)
        uMac
    }
}
