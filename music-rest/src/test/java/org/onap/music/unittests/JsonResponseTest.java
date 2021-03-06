/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
 * ===================================================================
 *  Modifications Copyright (c) 2018-2019 IBM.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.onap.music.lockingservice.cassandra.MusicLockState.LockStatus;
import org.onap.music.main.ResultType;
import org.onap.music.response.jsonobjects.JsonResponse;

public class JsonResponseTest {

    JsonResponse result = null;
    
    @Test
    public void testJsonResponseBooleanStringString() {
        result = new JsonResponse(ResultType.SUCCESS).setError("error").setMusicVersion("version");
        assertEquals("error",result.getError());
    }

    @Test
    public void testStatus() {
        result = new JsonResponse(ResultType.SUCCESS);
        result.setStatus(ResultType.SUCCESS);
        assertEquals(ResultType.SUCCESS, result.getStatus());
        result = new JsonResponse(ResultType.FAILURE).setError("error").setMusicVersion("version");
        assertEquals(ResultType.FAILURE, result.getStatus());
    }

    @Test
    public void testError() {
        result = new JsonResponse(ResultType.FAILURE);
        result.setError("error");
        assertTrue(result.getError().equals("error"));
        result.setError("");
        assertFalse(result.getError().equals("error"));
    }

    @Test
    public void testVersion() {
        result = new JsonResponse(ResultType.SUCCESS);
        result.setMusicVersion("version");
        assertTrue(result.getMusicVersion().equals("version"));
        result.setMusicVersion("");
        assertFalse(result.getMusicVersion().equals("version"));
    }

    @Test
    public void testToMap() {
        result = new JsonResponse(ResultType.SUCCESS).setError("error").setMusicVersion("1.0");
        Map<String,Object> myMap = result.toMap();
        assertTrue(myMap.containsKey("status"));
        assertEquals(ResultType.SUCCESS, myMap.get("status"));
        assertEquals("error", myMap.get("error"));
        assertEquals("1.0", myMap.get("version"));
        
        result = new JsonResponse(ResultType.FAILURE);
        myMap = result.toMap();
        assertTrue(myMap.containsKey("status"));
        assertEquals(ResultType.FAILURE, myMap.get("status"));
    }

    @Test
    public void testMessage() {
        result = new JsonResponse(ResultType.SUCCESS);
        result.setMessage("message");
        assertEquals("message", result.getMessage());
        
    }
    
    @Test
    public void testDataResult() {
        result = new JsonResponse(ResultType.SUCCESS);
        Map<String, HashMap<String, Object>> dataResult= new HashMap<>();
        result.setDataResult(dataResult);
        assertEquals(dataResult, result.getDataResult());
        
    }
    
    @Test
    public void testLock() {
        result = new JsonResponse(ResultType.SUCCESS);
        result.setLock("lock");
        assertEquals("lock", result.getLock());
        
    }
    
    @Test
    public void testLockLease() {
    	result = new JsonResponse(ResultType.SUCCESS);
        result.setLockLease("lockLease");
        assertEquals("lockLease", result.getLockLease());
    }
    
    @Test
    public void testMusicBuild() {
    	result = new JsonResponse(ResultType.SUCCESS);
        result.setMusicBuild("Build");
        assertEquals("Build", result.getMusicBuild());
    }
    
    @Test
    public void testLockHolder() {
    	result = new JsonResponse(ResultType.SUCCESS);
    	List<String> lockHolders = new ArrayList<>();
        result.setLockHolder(lockHolders);
        assertEquals(lockHolders, result.getLockHolder());
    }
    
    @Test
    public void testLockStatus() {
        result = new JsonResponse(ResultType.SUCCESS);
        LockStatus status = LockStatus.LOCKED;
        result.setLockStatus(status);
        assertEquals(status, result.getLockStatus());
        
    }
    
    @Test
    public void testToString() {
        result = new JsonResponse(ResultType.SUCCESS);
        assertTrue(result.toString() instanceof String);
        
    }
    
    @Test
    public void testLockHolders() {
        result = new JsonResponse(ResultType.SUCCESS).setLock("lockName").setLockHolder("lockholder1");
        Map<String, Object> lockMap = (Map<String, Object>) result.toMap().get("lock");
        // assure that this is string for backwards compatibility
        assertEquals("lockholder1", lockMap.get("lock-holder"));

        List<String> lockholders = new ArrayList<>();
        lockholders.add("lockholder1");
        lockholders.add("lockholder2");
        result.setLockHolder(lockholders);
        lockMap = (Map<String, Object>) result.toMap().get("lock");
        assertEquals(lockMap.get("lock-holder"), lockholders);
    }
}
