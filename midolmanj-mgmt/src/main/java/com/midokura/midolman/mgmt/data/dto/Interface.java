/*
 * Copyright 2011 Midokura Europe SARL
 */
package com.midokura.midolman.mgmt.data.dto;

import java.net.URI;
import java.util.UUID;
import javax.xml.bind.annotation.XmlRootElement;

import com.midokura.midolman.mgmt.rest_api.core.ResourceUriBuilder;

/**
 * Author: Toader Mihai Claudiu <mtoader@midokura.com>
 * <p/>
 * Date: 1/30/12
 * Time: 3:12 PM
 */
@XmlRootElement
public class Interface extends UriResource {

    UUID id;
    UUID hostId;
    String name;
    String mac;
    int mtu;
    int status;
    Type type;

    public enum Type {
        Physical, Virtual, Tunnel, Unknown
    }

    public Interface() {
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getHostId() {
        return hostId;
    }

    public void setHostId(UUID hostId) {
        this.hostId = hostId;
    }

    @Override
    public URI getUri() {
        return ResourceUriBuilder.getHostInterface(super.getBaseUri(), id);
    }

}
