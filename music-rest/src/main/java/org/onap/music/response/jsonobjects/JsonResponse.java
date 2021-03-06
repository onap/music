/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
 *
 *  Modifications Copyright (C) 2019 IBM.
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

package org.onap.music.response.jsonobjects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.onap.music.lockingservice.cassandra.MusicLockState.LockStatus;
import org.onap.music.main.ResultType;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "JsonResponse", description = "General Response JSON")
public class JsonResponse {

    /* Status is required */
    private ResultType status;
    
    /* Standard informational fields */
    private String error;
    private String message;
    
    /* versioning */
    private String musicVersion;
    private String musicBuild;
    
    /* Data Fields */
    private Map<String, HashMap<String, Object>> dataResult;
    
    /* Locking fields */
    private String lock;
    private LockStatus lockStatus;
    private List<String> lockHolders;
    private String lockLease;
    private boolean isLockHolders=false;

    /**
     * Create a JSONLock Response
     * Use setters to provide more information as in
     * JsonLockResponse(ResultType.SUCCESS).setMessage("We did it").setLock(mylockname)
     * @param status
     */
    public JsonResponse(ResultType status) {
        this.status = status;
    }

    public boolean isLockHolders() {
		return isLockHolders;
	}

	public JsonResponse setisLockHolders(boolean isLockHolders) {
		this.isLockHolders = isLockHolders;
		return this;
	}

    /**
     * 
     * @return
     */
    @ApiModelProperty(value = "Overall status of the response.",
                    allowableValues = "Success,Failure")
    public ResultType getStatus() {
        return status;
    }

    /**
     * 
     * @param status
     */
    public JsonResponse setStatus(ResultType status) {
        this.status = status;
        return this;
    }

    /**
     * 
     * @return the error
     */
    @ApiModelProperty(value = "Error value")
    public String getError() {
        return error;
    }

    /**
     * 
     * @param error
     */
    public JsonResponse setError(String error) {
        this.error = error;
        return this;
    }
    
    /**
     * 
     * @return the message
     */
    @ApiModelProperty(value = "Message value")
    public String getMessage() {
        return message;
    }

    /**
     * 
     * @param message
     */
    public JsonResponse setMessage(String message) {
        this.message = message;
        return this;
    }
    
    
    /**
     * .
     * @return the music version
     */
    public String getMusicVersion() {
        return this.musicVersion;
    }
    
    /**
     * .
     * @param version of music
     * @return
     */
    public JsonResponse setMusicVersion(String version) {
        this.musicVersion = version;
        return this;
    }

    /**
     * .
     * @return the music version
     */
    public String getMusicBuild() {
        return this.musicBuild;
    }
    
    /**
     * .
     * @param build of music
     * @return
     */
    public JsonResponse setMusicBuild(String build) {
        this.musicBuild = build;
        return this;
    }


    public Map<String, HashMap<String, Object>> getDataResult() {
        return this.dataResult;
    }
    
    public JsonResponse setDataResult(Map<String, HashMap<String, Object>> map) {
        this.dataResult = map;
        return this;
    }

    /**
     * 
     * @return
     */
    public String getLock() {
        return lock;
    }

    /**
     * 
     * @param lock
     */
    public JsonResponse setLock(String lock) {
        this.lock = lock;
        return this;
    }
    
    /**
     * 
     * @return the lockStatus
     */
    @ApiModelProperty(value = "Status of the lock")
    public LockStatus getLockStatus() {
        return lockStatus;
    }

    /**
     * 
     * @param lockStatus
     */
    public JsonResponse setLockStatus(LockStatus lockStatus) {
        this.lockStatus = lockStatus;
        return this;
    }

    /**
     * 
     * 
     * @return the lockHolder
     */
    @ApiModelProperty(value = "Holder of the Lock")
    public List<String> getLockHolder() {
        return lockHolders;
    }

    /**
     * 
     * @param lockHolder
     */
    public JsonResponse setLockHolder(String lockHolder) {
        this.lockHolders = new ArrayList<String>();
        this.lockHolders.add(lockHolder);
        return this;
    }

    public JsonResponse setLockHolder(List<String> lockHolders) {
        this.lockHolders = lockHolders;
        return this;
    }


    /**
     * @return the lockLease
     */
    public String getLockLease() {
        return lockLease;
    }

    /**
     * @param lockLease the lockLease to set
     */
    public JsonResponse setLockLease(String lockLease) {
        this.lockLease = lockLease;
        return this;
    }

    /**
     * Convert to Map
     * 
     * @return
     */
    public Map<String, Object> toMap() {
        Map<String, Object> fullMap = new HashMap<>();
        fullMap.put("status", status);
        if (error != null && !"".equals(error)) { 
            fullMap.put("error", error); 
        }
        if (message != null) { 
            fullMap.put("message", message); 
        }
        
        if (musicVersion != null) {
            fullMap.put("version", musicVersion);
        }
        
        if (musicBuild != null) {
            fullMap.put("build", musicBuild);
        }
    
        if (dataResult != null) {
            fullMap.put("result", dataResult);
        }
        
        if (lock != null) {
            Map<String, Object> lockMap = new HashMap<>();
            if (lock != null) {
                lockMap.put("lock", lock);
            }
            if (lockStatus != null) {
                lockMap.put("lock-status", lockStatus);
            }
            if (lockHolders != null && !lockHolders.isEmpty()) {
                if (lockHolders.size()==1 && !isLockHolders) {
                    //for backwards compatability
                    lockMap.put("lock-holder", lockHolders.get(0));
                } else {
                    lockMap.put("lock-holder", lockHolders);
                }
            }
            if (lockLease != null) {
                lockMap.put("lock-lease", lockLease);
            }
            fullMap.put("lock", lockMap);
        }

        return fullMap;
    }

    /**
     * Convert to String
     */
    @Override
    public String toString() {
        return "JsonLockResponse [status=" + status + ", error=" + error + ", message=" + message
                        + ", lock=" + lock + ", lockStatus=" + lockStatus + ", lockHolder="
                        + lockHolders + "]";
    }

}
