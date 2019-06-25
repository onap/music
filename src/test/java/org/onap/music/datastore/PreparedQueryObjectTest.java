/*
 * ============LICENSE_START==========================================
 * org.onap.music
 * ===================================================================
 *  Copyright (c) 2019 IBM.
 * ===================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * e
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * 
 * ============LICENSE_END=============================================
 * ====================================================================
 */

package org.onap.music.datastore;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class PreparedQueryObjectTest {
    
    private PreparedQueryObject preparedQueryObject;
    
    @Before
    public void setUp()
    {
        preparedQueryObject = new PreparedQueryObject();
    }
    
    @Test
    public void testKeyspaceName()
    {
        preparedQueryObject.setKeyspaceName("keyspaceName");
        assertEquals("keyspaceName", preparedQueryObject.getKeyspaceName());
    }
    
    @Test
    public void testConsistency()
    {
        preparedQueryObject.setConsistency("consistency");
        assertEquals("consistency", preparedQueryObject.getConsistency());
    }
    
    @Test
    public void testTableName()
    {
        preparedQueryObject.setTableName("tableName");
        assertEquals("tableName", preparedQueryObject.getTableName());
    }
    
    @Test
    public void testoperation()
    {
        preparedQueryObject.setOperation("operation");
        assertEquals("operation", preparedQueryObject.getOperation());
    }
    
    @Test
    public void testprimaryKeyValue()
    {
        preparedQueryObject.setPrimaryKeyValue("primaryKeyValue");
        assertEquals("primaryKeyValue", preparedQueryObject.getPrimaryKeyValue());
    }
    
}
