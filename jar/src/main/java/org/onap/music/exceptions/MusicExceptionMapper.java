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

import java.io.EOFException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.codehaus.jackson.map.exc.UnrecognizedPropertyException;
import org.onap.music.main.ResultType;
import org.onap.music.response.jsonobjects.JsonResponse;

@Provider
public class MusicExceptionMapper implements ExceptionMapper<Exception> {
    @Override
    public Response toResponse(Exception  exception) {
        if(exception instanceof UnrecognizedPropertyException) {
            return Response.status(Response.Status.BAD_REQUEST).
                        entity(new JsonResponse(ResultType.FAILURE).setError("Unknown field :"+((UnrecognizedPropertyException) exception).getUnrecognizedPropertyName()).toMap()).
                        build();
        }
        else if(exception instanceof EOFException) {
            return Response.status(Response.Status.BAD_REQUEST).
                        entity(new JsonResponse(ResultType.FAILURE).setError("Request body cannot be empty").toMap()).
                        build();
        }
        else {
            return Response.status(Response.Status.BAD_REQUEST).
                    entity(new JsonResponse(ResultType.FAILURE).setError(exception.getMessage()).toMap()).
                    build();
        }
    }
}
