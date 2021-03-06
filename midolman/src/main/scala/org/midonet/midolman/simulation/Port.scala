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

package org.midonet.midolman.simulation

import java.util.{UUID, List => JList}
import java.util.Collections.emptyList

import scala.collection.JavaConverters._

import org.midonet.cluster.data.storage.StateTable
import org.midonet.cluster.data.ZoomConvert.ConvertException
import org.midonet.cluster.models.Topology
import org.midonet.cluster.state.PortStateStorage.PortState
import org.midonet.cluster.util.{IPAddressUtil, IPSubnetUtil, UUIDUtil}
import org.midonet.midolman.PacketWorkflow.{AddVirtualWildcardFlow, Drop, ErrorDrop, SimStep, SimulationResult}
import org.midonet.midolman.simulation.Simulator.{ContinueWith, SimHook, ToPortAction}
import org.midonet.midolman.topology.VirtualTopology.{VirtualDevice, tryGet}
import org.midonet.packets.{IPv4Addr, IPv4Subnet, MAC}
import org.midonet.sdn.flows.FlowTagger

object Port {
    import IPAddressUtil._
    import IPSubnetUtil._
    import UUIDUtil.{fromProto, fromProtoList}

    def apply(proto: Topology.Port,
              state: PortState,
              infilters: JList[UUID],
              outfilters: JList[UUID],
              servicePorts: JList[UUID] = emptyList(),
              peeringTable: StateTable[MAC, IPv4Addr] = StateTable.empty): Port = {
        if (proto.getSrvInsertionIdsCount > 0 && proto.hasNetworkId)
            servicePort(proto, state, infilters)
        else if (proto.hasVtepId)
            vxLanPort(proto, state, infilters, outfilters)
        else if (proto.hasNetworkId)
            bridgePort(proto, state, infilters, outfilters, servicePorts)
        else if (proto.hasRouterId)
            routerPort(proto, state, infilters, outfilters, peeringTable)
        else
            throw new ConvertException("Unknown port type")
    }

    private def bridgePort(p: Topology.Port,
                           state: PortState,
                           infilters: JList[UUID],
                           outfilters: JList[UUID],
                           servicePorts: JList[UUID]) =
        new BridgePort(
            id = p.getId,
            inboundFilters = infilters,
            outboundFilters = outfilters,
            tunnelKey = state.tunnelKey.getOrElse(p.getTunnelKey),
            peerId = if (p.hasPeerId) p.getPeerId else null,
            hostId = if (p.hasHostId) p.getHostId else null,
            interfaceName = if (p.hasInterfaceName) p.getInterfaceName else null,
            adminStateUp = p.getAdminStateUp,
            portGroups = p.getPortGroupIdsList,
            isActive = state.isActive,
            vlanId = p.getVlanId.toShort,
            networkId = if (p.hasNetworkId) p.getNetworkId else null,
            inboundMirrors = p.getInboundMirrorIdsList,
            outboundMirrors = p.getOutboundMirrorIdsList,
            servicePorts = servicePorts)

    private def routerPort(p: Topology.Port,
                           state: PortState,
                           infilters: JList[UUID],
                           outfilters: JList[UUID],
                           peeringTable: StateTable[MAC, IPv4Addr]) =
        new RouterPort(
            id = p.getId,
            inboundFilters = infilters,
            outboundFilters = outfilters,
            tunnelKey = state.tunnelKey.getOrElse(p.getTunnelKey),
            peerId = if (p.hasPeerId) p.getPeerId else null,
            hostId = if (p.hasHostId) p.getHostId else null,
            interfaceName = if (p.hasInterfaceName) p.getInterfaceName else null,
            adminStateUp = p.getAdminStateUp,
            portGroups = p.getPortGroupIdsList,
            isActive = state.isActive,
            containerId = if (p.hasServiceContainerId) p.getServiceContainerId else null,
            routerId = if (p.hasRouterId) p.getRouterId else null,
            portSubnet = if (p.hasPortSubnet) fromV4Proto(p.getPortSubnet) else null,
            portAddress = if (p.hasPortAddress) toIPv4Addr(p.getPortAddress) else null,
            portMac = if (p.hasPortMac) MAC.fromString(p.getPortMac) else null,
            routeIds = p.getRouteIdsList.asScala.map(fromProto).toSet,
            inboundMirrors = p.getInboundMirrorIdsList,
            outboundMirrors = p.getOutboundMirrorIdsList,
            vni = if (p.hasVni) p.getVni else 0,
            tunnelIp = if (p.hasTunnelIp) toIPv4Addr(p.getTunnelIp) else null,
            peeringTable = peeringTable)

    private def vxLanPort(p: Topology.Port,
                          state: PortState,
                          infilters: JList[UUID],
                          outfilters: JList[UUID]) =
        VxLanPort(
            id = p.getId,
            inboundFilters = infilters,
            outboundFilters = outfilters,
            tunnelKey = state.tunnelKey.getOrElse(p.getTunnelKey),
            peerId = if (p.hasPeerId) p.getPeerId else null,
            adminStateUp = p.getAdminStateUp,
            portGroups = p.getPortGroupIdsList,
            networkId = if (p.hasNetworkId) p.getNetworkId else null,
            vtepId = if (p.hasVtepId) p.getVtepId else null,
            inboundMirrors = p.getInboundMirrorIdsList,
            outboundMirrors = p.getOutboundMirrorIdsList)

    private def servicePort(p: Topology.Port,
                            state: PortState,
                            infilters: JList[UUID]) =
        new ServicePort(
            id = p.getId,
            inboundFilters = infilters,
            tunnelKey = state.tunnelKey.getOrElse(p.getTunnelKey),
            peerId = if (p.hasPeerId) p.getPeerId else null,
            hostId = if (p.hasHostId) p.getHostId else null,
            interfaceName = if (p.hasInterfaceName) p.getInterfaceName else null,
            realAdminStateUp = p.getAdminStateUp,
            portGroups = p.getPortGroupIdsList,
            isActive = state.isActive,
            vlanId = p.getVlanId.toShort,
            networkId = if (p.hasNetworkId) p.getNetworkId else null,
            inboundMirrors = p.getInboundMirrorIdsList,
            outboundMirrors = p.getOutboundMirrorIdsList)
}

trait Port extends VirtualDevice with InAndOutFilters with MirroringDevice with Cloneable {
    def id: UUID
    def inboundFilters: JList[UUID]
    def outboundFilters: JList[UUID]
    def tunnelKey: Long
    def peerId: UUID
    def hostId: UUID
    def interfaceName: String
    def adminStateUp: Boolean
    def portGroups: JList[UUID] = emptyList()
    def isActive: Boolean = false
    def containerId: UUID = null
    def deviceId: UUID
    def vlanId: Short = Bridge.UntaggedVlanId
    def servicePorts: JList[UUID]

    override def infilters = inboundFilters
    override def outfilters = outboundFilters

    val action = ToPortAction(id)

    val deviceTag = FlowTagger.tagForPort(id)
    val flowStateTag = FlowTagger.tagForFlowStateDevice(id)
    val txTag = FlowTagger.tagForPortTx(id)
    val rxTag = FlowTagger.tagForPortRx(id)

    def isExterior: Boolean = this.hostId != null && this.interfaceName != null

    def isInterior: Boolean = this.peerId != null

    def isPlugged: Boolean = this.isInterior || this.isExterior

    protected def device: ForwardingDevice

    private[this] val emit = ContinueWith(
        if (isExterior) {
            context =>
                context.calculateActionsFromMatchDiff()
                context.log.debug("Emitting packet from port {}", id)
                context.addVirtualAction(action)
                context.trackConnection(deviceId)
                AddVirtualWildcardFlow
        } else if (isInterior) {
            context =>
                tryGet[Port](peerId).ingress(context)
        } else {
            context =>
                context.log.warn("Port {} is unplugged", id)
                ErrorDrop
        })

    def ingress(context: PacketContext): SimulationResult = {
        context.log.debug(s"Ingressing port $id at device $deviceId")
        if (context.devicesTraversed >= Simulator.MAX_DEVICES_TRAVERSED) {
            context.log.debug(s"Dropping packet that traversed too many devices "+
                s"(${context.devicesTraversed}}), possible topology loop.")
            ErrorDrop
        } else {
            context.devicesTraversed += 1
            context.addFlowTag(deviceTag)
            context.addFlowTag(rxTag)
            context.inPortId = id
            context.inPortGroups = portGroups
            context.currentDevice = deviceId
            mirroringInbound(context, portIngress)
        }
    }

    protected def egressCommon(context: PacketContext,
                               next: SimStep): SimulationResult = {
        context.log.debug(s"Egressing port $id")
        context.addFlowTag(deviceTag)
        context.addFlowTag(txTag)
        context.outPortId = id

        next(context)
    }

    def egress(context: PacketContext): SimulationResult = {
        egressCommon(context, filterAndContinueOut)
    }

    private[this] val ingressDevice: SimStep = context => {
        val dev = device
        dev.continue(context, dev.process(context))
    }

    protected val continueIn: SimStep = c => ingressDevice(c)
    protected val continueOut: SimStep = c => mirroringOutbound(c, emit)
    protected val filterAndContinueOut: SimStep = context => {
        filterOut(context, continueOut)
    }
    val egressNoFilter: SimStep = context => {
        egressCommon(context, continueOut)
    }

    private val portIngress = ContinueWith(filterIn(_, continueIn))

    override protected val preIn: SimHook = c => {
        if (isExterior && (portGroups ne null))
            c.portGroups = portGroups
        c.inPortId = id
        c.inPortGroups = portGroups
        c.outPortId = null
        c.outPortGroups = null
    }

    override protected val preOut: SimHook = c => {
        c.outPortId = id
        c.outPortGroups = portGroups
    }

    override def toString =
        s"id=$id active=$isActive adminStateUp=$adminStateUp " +
        s"inboundFilters=$inboundFilters outboundFilters=$outboundFilters " +
        s"tunnelKey=$tunnelKey portGroups=$portGroups peerId=$peerId " +
        s"hostId=$hostId interfaceName=$interfaceName vlanId=$vlanId"

}

class BridgePort(override val id: UUID,
                 override val inboundFilters: JList[UUID] = emptyList(),
                 override val outboundFilters: JList[UUID] = emptyList(),
                 override val tunnelKey: Long = 0,
                 override val peerId: UUID = null,
                 override val hostId: UUID = null,
                 override val interfaceName: String = null,
                 override val adminStateUp: Boolean = true,
                 override val portGroups: JList[UUID] = emptyList(),
                 override val isActive: Boolean = false,
                 override val vlanId: Short = Bridge.UntaggedVlanId,
                 val networkId: UUID,
                 override val inboundMirrors: JList[UUID] = emptyList(),
                 override val outboundMirrors: JList[UUID] = emptyList(),
                 override val servicePorts: JList[UUID] = emptyList())
        extends Port {

    override def toString =
        s"BridgePort [${super.toString} networkId=$networkId]"

    override def deviceId = networkId

    protected def device = tryGet[Bridge](networkId)

    override def egressCommon(context: PacketContext,
                              next: SimStep): SimulationResult = {
        if (id == context.inPortId) {
            Drop
        } else {
            if ((vlanId > 0) && context.wcmatch.isVlanTagged)
                context.wcmatch.removeVlanId(vlanId)
            super.egressCommon(context, next)
        }
    }
}

class ServicePort(override val id: UUID,
                  override val inboundFilters: JList[UUID] = emptyList(),
                  override val tunnelKey: Long = 0,
                  override val peerId: UUID = null,
                  override val hostId: UUID = null,
                  override val interfaceName: String = null,
                  val realAdminStateUp: Boolean,
                  override val portGroups: JList[UUID] = emptyList(),
                  override val isActive: Boolean = false,
                  override val vlanId: Short = Bridge.UntaggedVlanId,
                  override val networkId: UUID,
                  override val inboundMirrors: JList[UUID] = emptyList(),
                  override val outboundMirrors: JList[UUID] = emptyList())
        extends BridgePort(id, inboundFilters, emptyList(),
                           tunnelKey, peerId, hostId, interfaceName, true,
                           portGroups, isActive, vlanId, networkId,
                           inboundMirrors, outboundMirrors) {

    override def toString =
        s"ServicePort [${super.toString} networkId=$networkId" +
            s" realAdminState=$realAdminStateUp]"

    override def ingress(context: PacketContext): SimulationResult = {
        if (!realAdminStateUp) {
            context.log.debug("Service port has adminStateUp=false, Dropping")
            Drop
        } else {
            super.ingress(context)
        }
    }

    override def egressCommon(context: PacketContext,
                              next: SimStep): SimulationResult = {
        if (context.isRedirectedOut) {
            val result = if (!realAdminStateUp || !isActive) {
                /* If a service port is down, what happens to the packet depends
                 * on whether the redirect has a FAIL_OPEN flag or not.
                 * - If it doesn't have the flag, the packet is simply dropped.
                 * - If it does have FAIL_OPEN set, the packet will be bounced
                 * back, as if it had arrived at the service and the service had
                 * sent it back to us. To do this, we ingress the packet on the
                 * port. The admin state and active state will be ignored on
                 * ingress, as we always report admin state as true for the
                 * port, and active is only considered on egress.
                 */
                if (context.isRedirectFailOpen) {
                    context.clearRedirect()
                    super.ingress(context)
                } else {
                    context.log.debug(
                        s"Service port is down (adminStateUp:$realAdminStateUp,"
                            + s" active:$isActive), Dropping")
                    Drop
                }
            } else {
                super.egressCommon(context, next)
            }
            context.clearRedirect()
            result
        } else {
            context.log.debug("Non-redirected packet entered service port. Dropping")
            Drop
        }
    }
}

case class RouterPort(override val id: UUID,
                      override val inboundFilters: JList[UUID] = emptyList(),
                      override val outboundFilters: JList[UUID] = emptyList(),
                      override val tunnelKey: Long = 0,
                      override val peerId: UUID = null,
                      override val hostId: UUID = null,
                      override val interfaceName: String = null,
                      override val adminStateUp: Boolean = true,
                      override val portGroups: JList[UUID] = emptyList(),
                      override val isActive: Boolean = false,
                      override val containerId: UUID = null,
                      routerId: UUID,
                      portSubnet: IPv4Subnet,
                      portAddress: IPv4Addr,
                      portMac: MAC,
                      routeIds: Set[UUID] = Set.empty,
                      override val inboundMirrors: JList[UUID] = emptyList(),
                      override val outboundMirrors: JList[UUID] = emptyList(),
                      vni: Int = 0,
                      tunnelIp: IPv4Addr = null,
                      peeringTable: StateTable[MAC, IPv4Addr] = StateTable.empty)
    extends Port {

    override val servicePorts: JList[UUID] = emptyList()

    def isL2 = vni != 0

    protected def device = tryGet[Router](routerId)

    override def deviceId = routerId

    override protected def reject(context: PacketContext): Unit = {
        if (context.inPortId ne null) {
            if (context.inPortId eq id)
                sendIcmpProhibited(this, context)
            else
                sendIcmpProhibited(tryGet[RouterPort](context.inPortId), context)
        }
    }

    private def sendIcmpProhibited(from: RouterPort, context: PacketContext): Unit = {
        import Icmp.IPv4Icmp._
        val ethOpt = unreachableProhibitedIcmp(from, context, context.wcmatch)
        if (ethOpt.isDefined)
            context.addGeneratedPacket(from.id, ethOpt.get)
    }

    override def egressCommon(context: PacketContext,
                              next: SimStep): SimulationResult = {
        if (context.wcmatch.getEthSrc == context.wcmatch.getEthDst) {
            ingress(context)
        } else {
            super.egressCommon(context, next)
        }
    }

    override def toString =
        s"RouterPort [${super.toString} routerId=$routerId " +
        s"portSubnet=$portSubnet portAddress=$portAddress portMac=$portMac " +
        (if (isL2) s"vni=$vni tunnelIp=$tunnelIp" else "") + s"routeIds=$routeIds]"
}

case class VxLanPort(override val id: UUID,
                     override val inboundFilters: JList[UUID] = emptyList(),
                     override val outboundFilters: JList[UUID] = emptyList(),
                     override val tunnelKey: Long = 0,
                     override val peerId: UUID = null,
                     override val adminStateUp: Boolean = true,
                     override val portGroups: JList[UUID] = emptyList(),
                     networkId: UUID,
                     vtepId: UUID,
                     override val inboundMirrors: JList[UUID] = emptyList(),
                     override val outboundMirrors: JList[UUID] = emptyList())
    extends Port {

    override def hostId = null
    override def interfaceName = null
    override def deviceId = networkId
    override def isExterior = true
    override def isInterior = false
    override def isActive = true
    override def servicePorts: JList[UUID] = emptyList()

    protected def device = tryGet[Bridge](networkId)

    override def toString =
        s"VxLanPort [${super.toString} networkId=$networkId vtepId=$vtepId]"
}
