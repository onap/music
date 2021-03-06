/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
 * ===================================================================
 *  Modifications Copyright (c) 2018-2019 IBM
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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class JsonSelectTest {
    JsonSelect js = new JsonSelect();

    @Test
    public void testGetConsistencyInfo() {
        Map<String, String> mapSs = new HashMap<>();
        mapSs.put("k1", "one");
        js.setConsistencyInfo(mapSs);
        assertEquals("one", js.getConsistencyInfo().get("k1"));
    }

    @Test
    public void testSerialize() throws IOException {
        Map<String, String> mapSs = new HashMap<>();
        mapSs.put("Key", "Value");
        js.setConsistencyInfo(mapSs);
        js.serialize();
    }

    @Test
    public void testGetKeyspaceName() {
        js.setKeyspaceName("testkeyspace");
        assertEquals("testkeyspace",js.getKeyspaceName());

    }

    @Test
    public void testGetTableName() {
        js.setTableName("testkeyspace");
        assertEquals("testkeyspace",js.getTableName());

    }

}
