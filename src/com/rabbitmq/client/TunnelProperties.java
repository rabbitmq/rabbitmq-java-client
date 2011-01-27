//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client;

import java.util.Date;
import java.util.Map;

public interface TunnelProperties {
    
    /**
     * Retrieve the table in the headers field as a map of fields names and
     * values. 
     * @return headers table, or null if the headers field has not been set.
     */
    public abstract Map<String, Object> getHeaders();
    
    /**
     * Retrieve the value in the proxyName field.
     * @return proxyName field, or null if the field has not been set.
     */
    public abstract String getProxyName();
    
    /**
     * Retrieve the value in the dataName field.
     * @return dataName field, or null if the field has not been set.
     */
    public abstract String getDataName();
    
    /**
     * Retrieve the value in the durable field.
     * @return durable field, or null if the field has not been set.
     */
    public abstract Integer getDurable();
    
    /**
     * Retrieve the value in the broadcast field.
     * @return broadcast field, or null if the field has not been set.
     */
    public abstract Integer getBroadcast();
    
     /**
     * Set the headers table, or null indicating the field is not set
     * @param headers - a map of table field names and values
     */
    public abstract void setHeaders(Map<String, Object> headers);
    
    /**
     * Set the proxyName field, or null indicating the field is not set
     * @param proxyName the value to set the field to
     */
    public abstract void setProxyName(String proxyName);
    
    /**
     * Set the dataName field, or null indicating the field is not set
     * @param dataName the value to set the field to
     */
    public abstract void setDataName(String dataName);
    
    /**
     * Set the durable field, or null indicating the field is not set
     * @param durable the value to set the field to
     */
    public abstract void setDurable(Integer durable);
    
    /**
     * Set the broadcast field, or null indicating the field is not set
     * @param broadcast the value to set the field to
     */
    public abstract void setBroadcast(Integer broadcast);

}
