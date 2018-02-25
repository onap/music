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

package org.onap.music.unittests;

import static org.junit.Assert.*;
import java.util.Map;
import org.junit.Test;
import org.onap.music.response.jsonobjects.JsonResponse;

public class JsonResponseTest {

    JsonResponse result = null;
    
    @Test
    public void testJsonResponseBooleanStringString() {
        result = new JsonResponse(true,"error","version");
        assertEquals("error",result.getError());
    }

    @Test
    public void testJsonResponse() {
        result = new JsonResponse();
        assertFalse(result.getStatus());
    }

    @Test
    public void testStatus() {
        result = new JsonResponse();
        result.setStatus(true);
        assertTrue(result.getStatus());
        result = new JsonResponse(false,"error","version");
        assertFalse(result.getStatus());
    }

    @Test
    public void testError() {
        result = new JsonResponse();
        result.setError("error");
        assertTrue(result.getError().equals("error"));
        result.setError("");
        assertFalse(result.getError().equals("error"));
    }

    @Test
    public void testVersion() {
        result = new JsonResponse();
        result.setVersion("version");
        assertTrue(result.getVersion().equals("version"));
        result.setVersion("");
        assertFalse(result.getVersion().equals("version"));
    }

    @Test
    public void testToMap() {
        result = new JsonResponse(true,"error","version");
        Map<String,Object> myMap = result.toMap();
        assertTrue(myMap.containsKey("status"));
        result = new JsonResponse(false,"","");
        myMap = result.toMap();
        assertTrue(myMap.containsKey("status"));
    }

}
