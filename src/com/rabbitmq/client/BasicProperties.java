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
//  Copyright (c) 2007-2013 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.client;

import java.util.Date;
import java.util.Map;

public interface BasicProperties {
    
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
     * Retrieve the value in the deliveryMode field.
     * @return deliveryMode field, or null if the field has not been set.
     */
    public abstract Integer getDeliveryMode();
    
    /**
     * Retrieve the value in the priority field.
     * @return priority field, or null if the field has not been set.
     */
    public abstract Integer getPriority();
    
    /**
     * Retrieve the value in the correlationId field.
     * @return correlationId field, or null if the field has not been set.
     */
    public abstract String getCorrelationId();
    
    /**
     * Retrieve the value in the replyTo field.
     * @return replyTo field, or null if the field has not been set.
     */
    public abstract String getReplyTo();
    
    /**
     * Retrieve the value in the expiration field.
     * @return expiration field, or null if the field has not been set.
     */
    public abstract String getExpiration();
    
    /**
     * Retrieve the value in the messageId field.
     * @return messageId field, or null if the field has not been set.
     */
    public abstract String getMessageId();
    
    /**
     * Retrieve the value in the timestamp field.
     * @return timestamp field, or null if the field has not been set.
     */
    public abstract Date getTimestamp();
    
    /**
     * Retrieve the value in the type field.
     * @return type field, or null if the field has not been set.
     */
    public abstract String getType();
    
    /**
     * Retrieve the value in the userId field.
     * @return userId field, or null if the field has not been set.
     */
    public abstract String getUserId();
    
    /**
     * Retrieve the value in the appId field.
     * @return appId field, or null if the field has not been set.
     */
    public abstract String getAppId();
    
    /**
     * Set the contentType field, or null indicating the field is not set
     * @param contentType the value to set the field to
     */
    @Deprecated
    public abstract void setContentType(String contentType);
    
    /**
     * Set the contentEncoding field, or null indicating the field is not set
     * @param contentEncoding - the value to set the field to
     */
    @Deprecated
    public abstract void setContentEncoding(String contentEncoding);
    
    /**
     * Set the headers table, or null indicating the field is not set
     * @param headers a map of table field names and values
     */
    @Deprecated
    public abstract void setHeaders(Map<String, Object> headers);
    
    /**
     * Set the deliveryMode field, or null indicating the field is not set
     * @param deliveryMode the value to set the field to
     */
    @Deprecated
    public abstract void setDeliveryMode(Integer deliveryMode);
    
    /**
     * Set the priority field, or null indicating the field is not set
     * @param priority the value to set the field to
     */
    @Deprecated
    public abstract void setPriority(Integer priority);
    
    /**
     * Set the correlationId field, or null indicating the field is not set
     * @param correlationId the value to set the field to
     */
    @Deprecated
    public abstract void setCorrelationId(String correlationId);
    
    /**
     * Set the replyTo field, or null indicating the field is not set
     * @param replyTo the value to set the field to
     */
    @Deprecated
    public abstract void setReplyTo(String replyTo);
    
    /**
     * Set the expiration field, or null indicating the field is not set
     * @param expiration the value to set the field to
     */
    @Deprecated
    public abstract void setExpiration(String expiration);
    
    /**
     * Set the messageId field, or null indicating the field is not set
     * @param messageId the value to set the field to
     */
    @Deprecated
    public abstract void setMessageId(String messageId);
    
    /**
     * Set the timestamp field, or null indicating the field is not set
     * @param timestamp the value to set the field to
     */
    @Deprecated
    public abstract void setTimestamp(Date timestamp);
    
    /**
     * Set the type field, or null indicating the field is not set
     * @param type the value to set the field to
     */
    @Deprecated
    public abstract void setType(String type);
    
    /**
     * Set the userId field, or null indicating the field is not set
     * @param userId the value to set the field to
     */
    @Deprecated
    public abstract void setUserId(String userId);
    
    /**
     * Set the appId field, or null indicating the field is not set
     * @param appId the value to set the field to
     */
    @Deprecated
    public abstract void setAppId(String appId);

}
