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
package org.onap.music.main;

/**
 * 
 *
 */
public class MusicDigest {
    private String evPutStatus;
    private String vectorTs;

    /**
     * @param evPutStatus
     * @param vectorTs
     */
    public MusicDigest(String evPutStatus, String vectorTs) {
        this.evPutStatus = evPutStatus;
        this.vectorTs = vectorTs;
    }

    /**
     * @return
     */
    public String getEvPutStatus() {
        return evPutStatus;
    }

    /**
     * @param evPutStatus
     */
    public void setEvPutStatus(String evPutStatus) {
        this.evPutStatus = evPutStatus;
    }

    /**
     * @return
     */
    public String getVectorTs() {
        return vectorTs;
    }

    /**
     * @param vectorTs
     */
    public void setVectorTs(String vectorTs) {
        this.vectorTs = vectorTs;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return vectorTs + "|" + evPutStatus;
    }
}
