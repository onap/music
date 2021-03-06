/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
 * ===================================================================
 *  Modifications Copyright (C) 2019 IBM 
 * ===================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * 
 * ============LICENSE_END=============================================
 * ====================================================================
 */

package org.onap.music.datastore.jsonobjects;

import java.util.Map;

import javax.ws.rs.core.Response.Status;

import org.onap.music.datastore.PreparedQueryObject;
import org.onap.music.eelf.logging.EELFLoggerDelegate;
import org.onap.music.eelf.logging.format.AppMessages;
import org.onap.music.eelf.logging.format.ErrorSeverity;
import org.onap.music.eelf.logging.format.ErrorTypes;
import org.onap.music.exceptions.MusicQueryException;
import org.onap.music.main.MusicUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "JsonTable", description = "Json model creating new keyspace")
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonKeySpace {
    private EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(JsonKeySpace.class);
    private String keyspaceName;
    private Map<String, Object> replicationInfo;
    private String durabilityOfWrites;
    private Map<String, String> consistencyInfo;

    @ApiModelProperty(value = "Consistency level", allowableValues = "eventual,critical,atomic")
    public Map<String, String> getConsistencyInfo() {
        return consistencyInfo;
    }

    public void setConsistencyInfo(Map<String, String> consistencyInfo) {
        this.consistencyInfo = consistencyInfo;
    }

    @ApiModelProperty(value = "Replication information")
    public Map<String, Object> getReplicationInfo() {
        return replicationInfo;
    }

    public void setReplicationInfo(Map<String, Object> replicationInfo) {
        this.replicationInfo = replicationInfo;
    }

    @ApiModelProperty(value = "Durability", allowableValues = "true,false")
    public String getDurabilityOfWrites() {
        return durabilityOfWrites;
    }

    public void setDurabilityOfWrites(String durabilityOfWrites) {
        this.durabilityOfWrites = durabilityOfWrites;
    }

    @ApiModelProperty(value = "Keyspace name")
    public String getKeyspaceName() {
        return keyspaceName;
    }

    public void setKeyspaceName(String keyspaceName) {
        this.keyspaceName = keyspaceName;
    }

    /**
     * Will generate query to create Keyspacce.
     * 
     * @throws MusicQueryException
     */
    @SuppressWarnings("deprecation")
    public PreparedQueryObject genCreateKeyspaceQuery() throws MusicQueryException {

        if (logger.isDebugEnabled()) {
            logger.debug("Came inside createKeyspace method");
        }

        String keyspaceName = this.getKeyspaceName();
        String durabilityOfWrites = this.getDurabilityOfWrites();
        String consistency = MusicUtil.EVENTUAL;

        logger.info("genCreateKeyspaceQuery keyspaceName ::" + keyspaceName);
        logger.info("genCreateKeyspaceQuery class :: " + this.getReplicationInfo().get("class"));
        logger.info("genCreateKeyspaceQuery replication_factor :: " + this.getReplicationInfo().get("replication_factor"));
        logger.info("genCreateKeyspaceQuery durabilityOfWrites :: " + durabilityOfWrites);

        PreparedQueryObject queryObject = new PreparedQueryObject();
        
        if(consistency.equalsIgnoreCase(MusicUtil.EVENTUAL) && this.getConsistencyInfo().get("consistency") != null) {
            if(MusicUtil.isValidConsistency(this.getConsistencyInfo().get("consistency"))) {
                queryObject.setConsistency(this.getConsistencyInfo().get("consistency"));
            }else {
                throw new MusicQueryException("Invalid Consistency type",Status.BAD_REQUEST.getStatusCode());
            }  
        }
        
        long start = System.currentTimeMillis();
        Map<String, Object> replicationInfo = this.getReplicationInfo();
        String repString = null;
        try {
            repString = "{" + MusicUtil.jsonMaptoSqlString(replicationInfo, ",") + "}";
        } catch (Exception e) {
            logger.error(EELFLoggerDelegate.errorLogger, e.getMessage(), AppMessages.MISSINGDATA,
                    ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
        }
        queryObject.appendQueryString("CREATE KEYSPACE " + keyspaceName + " WITH replication = " + repString);
        if (this.getDurabilityOfWrites() != null) {
            queryObject.appendQueryString(" AND durable_writes = " + this.getDurabilityOfWrites());
        }
        queryObject.appendQueryString(";");
        long end = System.currentTimeMillis();
        logger.info(EELFLoggerDelegate.applicationLogger,
                "Time taken for setting up query in create keyspace:" + (end - start));

        return queryObject;
    }

    /**
     * Will generate Query to drop a keyspace.
     * 
     * @return
     */
    public PreparedQueryObject genDropKeyspaceQuery() {
        if (logger.isDebugEnabled()) {
            logger.debug("Coming inside genDropKeyspaceQuery method "+this.getKeyspaceName());
        }

        PreparedQueryObject queryObject = new PreparedQueryObject();
        queryObject.appendQueryString("DROP KEYSPACE " + this.getKeyspaceName() + ";");

        return queryObject;
    }

    @Override
    public String toString() {
        return "CassaKeyspaceObject [keyspaceName=" + keyspaceName + ", replicationInfo=" + replicationInfo
                + "durabilityOfWrites=" + durabilityOfWrites + "]";
    }

}
