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


public interface StreamProperties {
    
    /**
     * Retrieve the value in the contentType field.
     * @return contentType field, or null if the field has not been set.
     */
    public abstract String getContentType();
    
    /**
     * Retrieve the value in the contentEncoding field.
     * @return contentEncoding field, or null if the field has not been set.
     */
    public abstract String getContentEncoding();
    
    /**
     * Retrieve the table in the headers field as a map of fields names and
     * values. 
     * @return headers table, or null if the headers field has not been set.
     */
    public abstract Map<String, Object> getHeaders();
    
    /**
     * Retrieve the value in the priority field.
     * @return priority field, or null if the field has not been set.
     */
    public abstract Integer getPriority();

    /**
     * Retrieve the value in the timestamp field.
     * @return timestamp field, or null if the field has not been set.
     */
    public abstract Date getTimestamp();
 
    /**
     * Set the contentType field, or null indicating the field is not set
     * @param contentType the value to set the field to
     */
    public abstract void setContentType(String contentType);
    
    /**
     * Set the contentEncoding field, or null indicating the field is not set
     * @param contentEncoding the value to set the field to
     */
    public abstract void setContentEncoding(String contentEncoding);
    
    /**
     * Set the headers table, or null indicating the field is not set
     * @param headers a map of table field names and values
     */
    public abstract void setHeaders(Map<String, Object> headers);
    
    /**
     * Set the priority field, or null indicating the field is not set
     * @param priority the value to set the field to
     */
    public abstract void setPriority(Integer priority);
    
    /**
     * Set the timestamp field, or null indicating the field is not set
     * @param timestamp the value to set the field to
     */
    public abstract void setTimestamp(Date timestamp);
}
