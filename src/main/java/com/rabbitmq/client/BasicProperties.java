// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.

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

}
