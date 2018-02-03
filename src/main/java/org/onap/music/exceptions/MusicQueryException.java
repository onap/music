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
package org.onap.music.exceptions;



/**
 * @author inam
 *
 */
public class MusicQueryException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private int errorCode;


    /**
     * 
     */
    public MusicQueryException() {
        super();
    }

    /**
     * @param message
     */
    public MusicQueryException(String message) {
        super(message);
    }



    /**
     * @param message
     */
    public MusicQueryException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * @param cause
     */
    public MusicQueryException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message
     * @param cause
     */
    public MusicQueryException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     * @param cause
     * @param enableSuppression
     * @param writableStackTrace
     */
    public MusicQueryException(String message, Throwable cause, boolean enableSuppression,
                    boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
