/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2017 AT&T Intellectual Property
 * ===================================================================
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

package org.onap.music.datastore.jsonobjects;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class JsonDeleteTest {

    JsonDelete jd = null;

    @Before
    public void init() {
        jd = new JsonDelete();
    }

    @Test
    public void testGetConditions() {
        Map<String,Object>  mapSo = new HashMap<>();
        mapSo.put("key1","one");
        mapSo.put("key2","two");
        jd.setConditions(mapSo);
        assertEquals("one",jd.getConditions().get("key1"));
    }

    @Test
    public void testGetConsistencyInfo() {
        Map<String,String>  mapSs = new HashMap<>();
        mapSs.put("key3","three");
        mapSs.put("key4","four");
        jd.setConsistencyInfo(mapSs);
        assertEquals("three",jd.getConsistencyInfo().get("key3"));
    }

    @Test
    public void testGetColumns() {
        List<String> ary = new ArrayList<>();
        ary.add("e1");
        ary.add("e2");
        ary.add("e3");
        jd.setColumns(ary);
        assertEquals("e1",jd.getColumns().get(0));
    }

    @Test
    public void testGetTtl() {
        jd.setTtl("2000");
        assertEquals("2000",jd.getTtl());
    }

    @Test
    public void testGetTimestamp() {
        jd.setTimestamp("20:00");
        assertEquals("20:00",jd.getTimestamp());

    }

    @Test
    public void testGetKeyspaceName() {
        jd.setKeyspaceName("keyspace");
        assertEquals("keyspace",jd.getKeyspaceName());

    }

    @Test
    public void testGetTableName() {
        jd.setTableName("tablename");
        assertEquals("tablename",jd.getTableName());

    }

    @Test
    public void testGetPrimarKeyValue() {
        jd.setPrimarKeyValue("primarykey");
        assertEquals("primarykey",jd.getPrimarKeyValue());

    }

    @Test
    public void testRowIdString() {
        StringBuilder builder = new StringBuilder("testing");
        jd.setRowIdString(builder);
        assertEquals(jd.getRowIdString().toString(),builder.toString());
    }

}
