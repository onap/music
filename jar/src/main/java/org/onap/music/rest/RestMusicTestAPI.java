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
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.onap.music.eelf.logging.EELFLoggerDelegate;
import org.onap.music.main.MusicUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;


@Path("/v{version: [0-9]+}/test")
@Api(value="Test Api")
public class RestMusicTestAPI {
    
    @SuppressWarnings("unused")
    private EELFLoggerDelegate logger =EELFLoggerDelegate.getLogger(RestMusicTestAPI.class);

    /**
     * Returns a test JSON. This will confirm that REST is working.
     * @return
     */
    @GET
    @ApiOperation(value = "Get Test", response = Map.class)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, HashMap<String, String>> simpleTests(
            @Context HttpServletResponse response) {
        response.addHeader("X-latestVersion",MusicUtil.getVersion());
        Map<String, HashMap<String, String>> testMap = new HashMap<>();
        for(int i=0; i < 3; i++){
            HashMap<String, String> innerMap = new HashMap<>();
            innerMap.put(i+"", i+1+"");
            innerMap.put(i+1+"", i+2+"");
            testMap.put(i+"", innerMap);
        }
        return testMap;
    }
}
