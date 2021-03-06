/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
 * ===================================================================
 *  Modifications Copyright (C) 2019 IBM.
 *  Modifications Copyright (c) 2019 Samsung
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

package org.onap.music.rest;

import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import org.onap.music.datastore.jsonobjects.JsonDelete;
import org.onap.music.datastore.jsonobjects.JsonInsert;
import org.onap.music.datastore.jsonobjects.JsonTable;
import org.onap.music.datastore.jsonobjects.JsonUpdate;
import org.onap.music.eelf.logging.EELFLoggerDelegate;

import org.onap.music.eelf.logging.format.AppMessages;
import org.onap.music.eelf.logging.format.ErrorSeverity;
import org.onap.music.eelf.logging.format.ErrorTypes;
import org.apache.commons.lang3.StringUtils;
import org.onap.music.datastore.MusicDataStoreHandle;
import org.onap.music.datastore.PreparedQueryObject;
import com.datastax.driver.core.ResultSet;
import org.onap.music.exceptions.MusicQueryException;
import org.onap.music.exceptions.MusicServiceException;
import org.onap.music.main.MusicCore;
import org.onap.music.main.MusicUtil;
import org.onap.music.main.ResultType;
import org.onap.music.response.jsonobjects.JsonResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Path("/v2/priorityq/")
@Api(value = "Q Api",hidden = true)
public class RestMusicQAPI {

    private EELFLoggerDelegate logger = EELFLoggerDelegate.getLogger(RestMusicQAPI.class);


  /**
   * 
   * @param tableObj
   * @param keyspace
   * @param tablename
   * @throws Exception
   */
 
    @POST
    @Path("/keyspaces/{keyspace}/{qname}") // qname same as tablename
    @ApiOperation(value = "Create Q", response = String.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createQ(
        @ApiParam(value = "Major Version", required = true) @PathParam("version") String version,
        @ApiParam(value = "Minor Version", required = false) @HeaderParam("X-minorVersion") String minorVersion,
        @ApiParam(value = "Patch Version", required = false) @HeaderParam("X-patchVersion") String patchVersion,
        @ApiParam(value = "AID", required = false) @HeaderParam("aid") String aid,
        @ApiParam(value = "Application namespace", required = true) @HeaderParam("ns") String ns,
        @ApiParam(value = "Authorization", required = true) @HeaderParam(MusicUtil.AUTHORIZATION) String authorization,
        JsonTable tableObj, 
        @ApiParam(value = "Key Space", required = true) @PathParam("keyspace") String keyspace,
        @ApiParam(value = "Table Name", required = true) @PathParam("qname") String tablename) throws Exception {
        ResponseBuilder response = MusicUtil.buildVersionResponse(version, minorVersion, patchVersion);

        Map<String, String> fields = tableObj.getFields();
    if (fields == null) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                .entity(new JsonResponse(ResultType.FAILURE)
                .setError("CreateQ/Required table fields are empty or not set").toMap())
                .build();
        }

        String primaryKey = tableObj.getPrimaryKey();
        String partitionKey = tableObj.getPartitionKey();
        String clusteringKey = tableObj.getClusteringKey();
        String filteringKey = tableObj.getFilteringKey();
        String clusteringOrder = tableObj.getClusteringOrder();

        if(primaryKey == null) {
            primaryKey = tableObj.getFields().get("PRIMARY KEY");
        }

        if ((primaryKey == null) && (partitionKey == null)) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                    ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                    .entity(new JsonResponse(ResultType.FAILURE)
                            .setError("CreateQ: Partition key cannot be empty").toMap())
                    .build();
        }

        if ((primaryKey == null) && (clusteringKey == null)) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                    ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                    .entity(new JsonResponse(ResultType.FAILURE)
                            .setError("CreateQ: Clustering key cannot be empty").toMap())
                    .build();
        }

        if (clusteringOrder == null) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                    ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                    .entity(new JsonResponse(ResultType.FAILURE)
                            .setError("CreateQ: Clustering Order cannot be empty").toMap())
                    .build();
        }

        if ((primaryKey!=null) && (partitionKey == null)) {
            primaryKey = primaryKey.trim();
            int count1 = StringUtils.countMatches(primaryKey,')');
            int count2 = StringUtils.countMatches(primaryKey,'(');
            if (count1 != count2) {
                return response.status(Status.BAD_REQUEST).entity(new JsonResponse(ResultType.FAILURE)
                    .setError("CreateQ Error: primary key '(' and ')' do not match, primary key=" + primaryKey)
                    .toMap()).build();
            }

            if ( primaryKey.indexOf('(') == -1  || ( count2 == 1 && (primaryKey.lastIndexOf(')') +1) ==  primaryKey.length() ) ) {
                if (primaryKey.contains(",") ) {
                    partitionKey= primaryKey.substring(0,primaryKey.indexOf(','));
                    partitionKey=partitionKey.replaceAll("[\\(]+","");
                    clusteringKey=primaryKey.substring(primaryKey.indexOf(',')+1);  // make sure index
                    clusteringKey=clusteringKey.replaceAll("[)]+", "");
                } else {
                    partitionKey=primaryKey;
                    partitionKey=partitionKey.replaceAll("[\\)]+","");
                    partitionKey=partitionKey.replaceAll("[\\(]+","");
                    clusteringKey="";
                }
            } else {
                partitionKey= primaryKey.substring(0,primaryKey.indexOf(')'));
                partitionKey=partitionKey.replaceAll("[\\(]+","");
                partitionKey = partitionKey.trim();
                clusteringKey= primaryKey.substring(primaryKey.indexOf(')'));
                clusteringKey=clusteringKey.replaceAll("[\\(]+","");
                clusteringKey=clusteringKey.replaceAll("[\\)]+","");
                clusteringKey = clusteringKey.trim();
                if (clusteringKey.indexOf(',') == 0)
					clusteringKey=clusteringKey.substring(1);
                clusteringKey = clusteringKey.trim();
                if (clusteringKey.equals(",") )
					clusteringKey=""; // print error if needed    ( ... ),)
            }
        }

        if (partitionKey.trim().isEmpty()) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                .entity(new JsonResponse(ResultType.FAILURE)
                .setError("CreateQ: Partition key cannot be empty").toMap())
                .build();
        }

        if (clusteringKey.trim().isEmpty()) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                .entity(new JsonResponse(ResultType.FAILURE)
                .setError("CreateQ: Clustering key cannot be empty").toMap())
                .build();
        }

        if((filteringKey != null) && (filteringKey.equalsIgnoreCase(partitionKey))) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                .entity(new JsonResponse(ResultType.FAILURE)
                .setError("CreateQ: Filtering key cannot be same as Partition Key").toMap())
                .build();
        }

        return new RestMusicDataAPI().createTable(version, minorVersion, patchVersion, aid, ns, authorization, tableObj, keyspace, tablename);
    }

  /**
   * 
   * @param insObj
   * @param keyspace
   * @param tablename
   * @throws Exception
   */
    @POST
    @Path("/keyspaces/{keyspace}/{qname}/rows")
    @ApiOperation(value = "", response = Void.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    // public Map<String, Object> insertIntoQ(
    public Response insertIntoQ(
        @ApiParam(value = "Major Version", required = true) @PathParam("version") String version,
        @ApiParam(value = "Minor Version", required = false) @HeaderParam("X-minorVersion") String minorVersion,
        @ApiParam(value = "Patch Version", required = false) @HeaderParam("X-patchVersion") String patchVersion,
        @ApiParam(value = "AID", required = false) @HeaderParam("aid") String aid,
        @ApiParam(value = "Application namespace", required = true) @HeaderParam("ns") String ns,
        @ApiParam(value = "Authorization", required = true) @HeaderParam(MusicUtil.AUTHORIZATION) String authorization,
        JsonInsert insObj,
        @ApiParam(value = "Key Space", required = true) @PathParam("keyspace") String keyspace,
        @ApiParam(value = "Table Name", required = true) @PathParam("qname") String tablename) {
        
        ResponseBuilder response = MusicUtil.buildVersionResponse(version, minorVersion, patchVersion);
        if (insObj.getValues().isEmpty()) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
            ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST).entity(new JsonResponse(ResultType.FAILURE)
                .setError("Required HTTP Request body is missing.").toMap()).build();
        }
        return new RestMusicDataAPI().insertIntoTable(version, minorVersion, patchVersion, aid, ns,
            authorization, insObj, keyspace, tablename);
    }

    /**
    * 
    * @param updateObj
    * @param keyspace
    * @param tablename
    * @param info
    * @return
    * @throws Exception
    */
    @PUT
    @Path("/keyspaces/{keyspace}/{qname}/rows")
    @ApiOperation(value = "updateQ", response = String.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateQ(
        @ApiParam(value = "Major Version", required = true) @PathParam("version") String version,
        @ApiParam(value = "Minor Version", required = false) @HeaderParam("X-minorVersion") String minorVersion,
        @ApiParam(value = "Patch Version", required = false) @HeaderParam("X-patchVersion") String patchVersion,
        @ApiParam(value = "AID", required = false) @HeaderParam("aid") String aid,
        @ApiParam(value = "Application namespace", required = true) @HeaderParam("ns") String ns,
        @ApiParam(value = "Authorization", required = true) @HeaderParam(MusicUtil.AUTHORIZATION) String authorization,
        JsonUpdate updateObj,
        @ApiParam(value = "Key Space", required = true) @PathParam("keyspace") String keyspace,
        @ApiParam(value = "Table Name", required = true) @PathParam("qname") String tablename,
        @Context UriInfo info) throws MusicServiceException, MusicQueryException {

        ResponseBuilder response = MusicUtil.buildVersionResponse(version, minorVersion, patchVersion);
        if (updateObj.getValues().isEmpty()) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
                ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
            return response.status(Status.BAD_REQUEST)
                .entity(new JsonResponse(ResultType.FAILURE)
                .setError("Required HTTP Request body is missing. JsonUpdate updateObj.getValues() is empty. ")
                .toMap())
                .build();
        }
        return new RestMusicDataAPI().updateTable(version, minorVersion, patchVersion, aid, ns, 
            authorization,updateObj, keyspace, tablename, info);
    }

    /**
    * 
    * @param delObj
    * @param keyspace
    * @param tablename
    * @param info
    * 
    * @return
    * @throws Exception
    */

    @DELETE
    @Path("/keyspaces/{keyspace}/{qname}/rows")
    @ApiOperation(value = "deleteQ", response = String.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteFromQ(
    @ApiParam(value = "Major Version", required = true) @PathParam("version") String version,
    @ApiParam(value = "Minor Version", required = false) @HeaderParam("X-minorVersion") String minorVersion,
    @ApiParam(value = "Patch Version", required = false) @HeaderParam("X-patchVersion") String patchVersion,
    @ApiParam(value = "AID", required = false) @HeaderParam("aid") String aid,
    @ApiParam(value = "Application namespace", required = true) @HeaderParam("ns") String ns,
    @ApiParam(value = "Authorization", required = true) @HeaderParam(MusicUtil.AUTHORIZATION) String authorization,
    JsonDelete delObj,
    @ApiParam(value = "Key Space", required = true) @PathParam("keyspace") String keyspace,
    @ApiParam(value = "Table Name", required = true) @PathParam("qname") String tablename,
    @Context UriInfo info) throws MusicServiceException, MusicQueryException {
    // added checking as per RestMusicDataAPI
    ResponseBuilder response = MusicUtil.buildVersionResponse(version, minorVersion, patchVersion);
    if (delObj == null) {
        logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.MISSINGDATA,
            ErrorSeverity.CRITICAL, ErrorTypes.DATAERROR);
        return response.status(Status.BAD_REQUEST).entity(new JsonResponse(ResultType.FAILURE)
            .setError("deleteFromQ JsonDelete delObjis empty").toMap()).build();
    }

    return new RestMusicDataAPI().deleteFromTable(version, minorVersion, patchVersion, aid, ns,         
        authorization, delObj, keyspace, tablename, info);
    }

    /**
    * 
    * @param keyspace
    * @param tablename
    * @param info
    * @return
    * @throws Exception
    */
    @GET
    @Path("/keyspaces/{keyspace}/{qname}/peek")
    @ApiOperation(value = "", response = Map.class)
    @Produces(MediaType.APPLICATION_JSON)
    //public Map<String, HashMap<String, Object>> peek(
    public Response peek(
        @ApiParam(value = "Major Version", required = true) @PathParam("version") String version,
        @ApiParam(value = "Minor Version", required = false) @HeaderParam("X-minorVersion") String minorVersion,
        @ApiParam(value = "Patch Version", required = false) @HeaderParam("X-patchVersion") String patchVersion,
        @ApiParam(value = "AID", required = false) @HeaderParam("aid") String aid,
        @ApiParam(value = "Application namespace", required = true) @HeaderParam("ns") String ns,
        @ApiParam(value = "Authorization", required = true) @HeaderParam(MusicUtil.AUTHORIZATION) String authorization,
        @ApiParam(value = "Key Space", required = true) @PathParam("keyspace") String keyspace,
        @ApiParam(value = "Table Name", required = true) @PathParam("qname") String tablename,
        @Context UriInfo info)  {
        int limit =1; //peek must return just the top row
        // Map<String ,String> auth = new HashMap<>();
        // String userId =auth.get(MusicUtil.USERID);
        // String password =auth.get(MusicUtil.PASSWORD);  
        ResponseBuilder response = MusicUtil.buildVersionResponse(version, minorVersion, patchVersion);

        PreparedQueryObject queryObject = new PreparedQueryObject();
        if (info.getQueryParameters() == null ) { //|| info.getQueryParameters().isEmpty())
            queryObject.appendQueryString(
                "SELECT *  FROM " + keyspace + "." + tablename + " LIMIT " + limit + ";");
        } else {
            try {
                queryObject = new RestMusicDataAPI().selectSpecificQuery(keyspace, tablename, info, limit);
            } catch (MusicServiceException ex) {
                logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.UNKNOWNERROR,
                ErrorSeverity.WARN, ErrorTypes.GENERALSERVICEERROR, ex);
                return response.status(Status.BAD_REQUEST)
                    .entity(new JsonResponse(ResultType.FAILURE).setError(ex.getMessage()).toMap())
                    .build();
            }
        }

        try {
            ResultSet results = MusicCore.get(queryObject);
            return response.status(Status.OK).entity(new JsonResponse(ResultType.SUCCESS)
                .setDataResult(MusicDataStoreHandle.marshallResults(results)).toMap()).build();
        } catch (MusicServiceException ex) {
            logger.error(EELFLoggerDelegate.errorLogger, "", AppMessages.UNKNOWNERROR,
                ErrorSeverity.ERROR, ErrorTypes.MUSICSERVICEERROR, ex);
            return response.status(Status.BAD_REQUEST)
                .entity(new JsonResponse(ResultType.FAILURE).setError(ex.getMessage()).toMap())
                .build();
        }
    }

    /**
    * 
    *
    * @param keyspace
    * @param tablename
    * @param info
    * @return
    * @throws Exception
    */
    @GET
    @Path("/keyspaces/{keyspace}/{qname}/filter")
    @ApiOperation(value = "filter", response = Map.class)
    @Produces(MediaType.APPLICATION_JSON)
    // public Map<String, HashMap<String, Object>> filter(
    public Response filter(
        @ApiParam(value = "Major Version", required = true) @PathParam("version") String version,
        @ApiParam(value = "Minor Version", required = false) @HeaderParam("X-minorVersion") String minorVersion,
        @ApiParam(value = "Patch Version", required = false) @HeaderParam("X-patchVersion") String patchVersion,
        @ApiParam(value = "AID", required = false) @HeaderParam("aid") String aid,
        @ApiParam(value = "Application namespace", required = true) @HeaderParam("ns") String ns,
        @ApiParam(value = "Authorization", required = true) @HeaderParam(MusicUtil.AUTHORIZATION) String authorization,
        @ApiParam(value = "Key Space", required = true) @PathParam("keyspace") String keyspace,
        @ApiParam(value = "Table Name", required = true) @PathParam("qname") String tablename,
        @Context UriInfo info) throws Exception {

        return new RestMusicDataAPI().selectWithCritical(version, minorVersion, patchVersion, aid, ns, authorization,null, keyspace, tablename, info);// , limit)

    }

    /**
    * 
    * @param tabObj
    * @param keyspace
    * @param tablename
    * @throws Exception
    */
    @DELETE
    @ApiOperation(value = "DropQ", response = String.class)
    @Path("/keyspaces/{keyspace}/{qname}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dropQ(
        @ApiParam(value = "Major Version", required = true) @PathParam("version") String version,
        @ApiParam(value = "Minor Version",
        required = false) @HeaderParam("X-minorVersion") String minorVersion,
        @ApiParam(value = "Patch Version",
        required = false) @HeaderParam("X-patchVersion") String patchVersion,
        @ApiParam(value = "AID", required = false) @HeaderParam("aid") String aid,
        @ApiParam(value = "Application namespace", required = true) @HeaderParam("ns") String ns,
        @ApiParam(value = "Authorization", required = true) @HeaderParam(MusicUtil.AUTHORIZATION) String authorization,
        @ApiParam(value = "Key Space", required = true) @PathParam("keyspace") String keyspace,
          @ApiParam(value = "Table Name", required = true) @PathParam("qname") String tablename) throws Exception {

        return new RestMusicDataAPI().dropTable(version, minorVersion, patchVersion, aid, ns, authorization, keyspace, tablename);

    }
}
