/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.onap.music.datastore.PreparedQueryObject;
import org.onap.music.datastore.jsonobjects.JsonOnboard;
import org.onap.music.main.CachingUtil;
import org.onap.music.main.MusicCore;
import org.onap.music.main.MusicUtil;
import com.att.eelf.configuration.EELFLogger;
import com.att.eelf.configuration.EELFManager;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Path("/v{version: [0-9]+}/admin")
// @Path("/admin")
@Api(value = "Admin Api", hidden = true)
public class RestMusicAdminAPI {
    private static EELFLogger logger = EELFManager.getInstance().getLogger(RestMusicAdminAPI.class);

    /*
     * API to onboard an application with MUSIC. This is the mandatory first step.
     * 
     */
    @POST
    @Path("/onboardAppWithMusic")
    @ApiOperation(value = "Onboard application", response = String.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> onboardAppWithMusic(JsonOnboard jsonObj,
                    @Context HttpServletResponse response) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        String appName = jsonObj.getAppname();
        String userId = jsonObj.getUserId();
        String isAAF = jsonObj.getIsAAF();
        String password = jsonObj.getPassword();
        response.addHeader("X-latestVersion", MusicUtil.getVersion());
        if (appName == null || userId == null || isAAF == null || password == null) {
            resultMap.put("Exception",
                            "Please check the request parameters. Some of the required values appName(ns), userId, password, isAAF are missing.");
            return resultMap;
        }

        PreparedQueryObject pQuery = new PreparedQueryObject();
        pQuery.appendQueryString(
                        "select uuid from admin.keyspace_master where application_name = ? allow filtering");
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), appName));
        ResultSet rs = MusicCore.get(pQuery);
        if (!rs.all().isEmpty()) {
            resultMap.put("Exception", "Your application " + appName
                            + " has already been onboarded. Please contact admin.");
            return resultMap;
        }

        pQuery = new PreparedQueryObject();
        String uuid = CachingUtil.generateUUID();
        pQuery.appendQueryString(
                        "INSERT INTO admin.keyspace_master (uuid, keyspace_name, application_name, is_api, "
                                        + "password, username, is_aaf) VALUES (?,?,?,?,?,?,?)");
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.uuid(), uuid));
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(),
                        MusicUtil.DEFAULTKEYSPACENAME));
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), appName));
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.cboolean(), "True"));
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), password));
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), userId));
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.cboolean(), isAAF));

        String returnStr = MusicCore.eventualPut(pQuery).toString();
        if (returnStr.contains("Failure")) {
            resultMap.put("Exception",
                            "Oops. Something wrong with onboarding process. Please retry later or contact admin.");
            return resultMap;
        }
        CachingUtil.updateisAAFCache(appName, isAAF);
        resultMap.put("Success", "Your application " + appName + " has been onboarded with MUSIC.");
        resultMap.put("Generated AID", uuid);
        return resultMap;
    }


    /*
     * API to onboard an application with MUSIC. This is the mandatory first step.
     * 
     */
    @GET
    @Path("/onboardAppWithMusic")
    @ApiOperation(value = "Onboard application", response = String.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getOnboardedInfo(
                    @ApiParam(value = "AID", required = true) @HeaderParam("aid") String uuid,
                    @ApiParam(value = "Application namespace",
                                    required = true) @HeaderParam("ns") String appName,
                    @Context HttpServletResponse response) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();

        response.addHeader("X-latestVersion", MusicUtil.getVersion());
        if (appName == null && uuid == null) {
            resultMap.put("Exception",
                            "Please check the request parameters. Some of the required values appName(ns), aid are missing.");
            return resultMap;
        }

        PreparedQueryObject pQuery = new PreparedQueryObject();

        String cql = "select uuid, keyspace_name from admin.keyspace_master where ";
        if (appName != null)
            cql = cql + "application_name = ?";
        else if (uuid != null)
            cql = cql + "uuid = ?";
        cql = cql + " allow filtering";
        System.out.println("Get OnboardingInfo CQL: " + cql);
        pQuery.appendQueryString(cql);
        if (appName != null)
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), appName));
        else if (uuid != null)
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.uuid(), uuid));
        ResultSet rs = MusicCore.get(pQuery);
        Iterator<Row> it = rs.iterator();
        while (it.hasNext()) {
            Row row = (Row) it.next();
            resultMap.put(row.getString("keyspace_name"), row.getUUID("uuid"));
        }
        if (resultMap.isEmpty())
            resultMap.put("ERROR", "Application is not onboarded. Please contact admin.");
        return resultMap;
    }


    @DELETE
    @Path("/onboardAppWithMusic")
    @ApiOperation(value = "Delete Onboard application", response = String.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> deleteOnboardApp(JsonOnboard jsonObj,
                    @ApiParam(value = "AID", required = true) @HeaderParam("aid") String aid,
                    @Context HttpServletResponse response) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        response.addHeader("X-latestVersion", MusicUtil.getVersion());
        String appName = jsonObj.getAppname();
        PreparedQueryObject pQuery = new PreparedQueryObject();
        long count = 0;
        if (appName == null && aid == null) {
            resultMap.put("Exception", "Please make sure either appName(ns) or Aid is present");
            return resultMap;
        }
        if (aid != null) {
            pQuery.appendQueryString(
                            "select count(*) as count from admin.keyspace_master where uuid = ?");
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.uuid(),
                            UUID.fromString(aid)));
            Row row = MusicCore.get(pQuery).one();
            if (row != null) {
                count = row.getLong(0);
            }

            if (count == 0) {
                resultMap.put("Failure", "Please verify your AID.");
                return resultMap;
            } else {
                pQuery = new PreparedQueryObject();
                pQuery.appendQueryString("delete from admin.keyspace_master where uuid = ?");
                pQuery.addValue(MusicUtil.convertToActualDataType(DataType.uuid(),
                                UUID.fromString(aid)));
                String result = MusicCore.eventualPut(pQuery).toString();
                if (result.toLowerCase().contains("success")) {
                    resultMap.put("Success", "Your application has been deleted.");
                    return resultMap;
                } else {
                    resultMap.put("Failure", "Please verify your AID.");
                    return resultMap;
                }
            }

        }
        pQuery.appendQueryString(
                        "select uuid from admin.keyspace_master where application_name = ? allow filtering");
        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), appName));
        ResultSet rs = MusicCore.get(pQuery);
        List<Row> rows = rs.all();
        String uuid = null;
        if (rows.size() == 0) {
            resultMap.put("Exception",
                            "Application not found. Please make sure Application exists.");
            return resultMap;
        } else if (rows.size() == 1) {
            uuid = rows.get(0).getUUID("uuid").toString();
            pQuery = new PreparedQueryObject();
            pQuery.appendQueryString("delete from admin.keyspace_master where uuid = ?");
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.uuid(),
                            UUID.fromString(uuid)));
            MusicCore.eventualPut(pQuery);
            resultMap.put("Success", "Your application " + appName + " has been deleted.");
            return resultMap;
        } else {
            resultMap.put("Failure", "Please provide UUID for the application.");
        }

        return resultMap;
    }


    @PUT
    @Path("/onboardAppWithMusic")
    @ApiOperation(value = "Update Onboard application", response = String.class)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateOnboardApp(JsonOnboard jsonObj,
                    @ApiParam(value = "AID", required = true) @HeaderParam("aid") String aid,
                    @Context HttpServletResponse response) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        response.addHeader("X-latestVersion", MusicUtil.getVersion());
        String appName = jsonObj.getAppname();
        String userId = jsonObj.getUserId();
        String isAAF = jsonObj.getIsAAF();
        String password = jsonObj.getPassword();
        String consistency = "eventual";
        PreparedQueryObject pQuery = new PreparedQueryObject();

        if (aid == null) {
            resultMap.put("Exception", "Please make sure Aid is present");
            return resultMap;
        }

        if (appName == null && userId == null && password == null && isAAF == null) {
            resultMap.put("Exception",
                            "No parameters found to update. Please update atleast one parameter.");
            return resultMap;
        }

        StringBuilder preCql = new StringBuilder("UPDATE admin.keyspace_master SET ");
        if (appName != null)
            preCql.append(" application_name = ?,");
        if (userId != null)
            preCql.append(" username = ?,");
        if (password != null)
            preCql.append(" password = ?,");
        if (isAAF != null)
            preCql.append(" is_aaf = ?,");
        preCql.deleteCharAt(preCql.length() - 1);
        preCql.append(" WHERE uuid = ?");
        pQuery.appendQueryString(preCql.toString());
        if (appName != null)
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), appName));
        if (userId != null)
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), userId));
        if (password != null)
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.text(), password));
        if (isAAF != null)
            pQuery.addValue(MusicUtil.convertToActualDataType(DataType.cboolean(), isAAF));


        pQuery.addValue(MusicUtil.convertToActualDataType(DataType.uuid(), UUID.fromString(aid)));
        Boolean result = MusicCore.nonKeyRelatedPut(pQuery, consistency);

        if (result) {
            resultMap.put("Success", "Your application has been updated successfully");
        } else {
            resultMap.put("Exception",
                            "Oops. Spomething went wrong. Please make sure Aid is correct and application is onboarded");
        }

        return resultMap;
    }
}
