/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.core.executor.sql.execute.row;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class QueryRowTest {
    
    private QueryRow queryRow;
    
    @Before
    public void setUp() {
        queryRow = new QueryRow(Collections.singletonList((Object) 10), Collections.singletonList(1));
    }
    
    @Test
    public void assertGetColumnValue() {
        assertThat(queryRow.getColumnValue(1), is((Object) 10));
    }
    
    @Test
    public void assertEquals() {
        QueryRow queryRow1 = new QueryRow(Collections.singletonList((Object) 10), Collections.<Integer>emptyList());
        assertTrue(queryRow1.equals(queryRow));
    }
    
    @Test
    public void assertEqualsPartly() {
        QueryRow queryRow1 = new QueryRow(Collections.singletonList((Object) 10), Collections.<Integer>emptyList());
        assertFalse(queryRow.equals(queryRow1));
    }
    
    @Test
    public void assertHashCode() {
    }
    
    @Test
    public void assertGetRowData() {
    }
    
    @Test
    public void assertGetDistinctColumnIndexes() {
    }
}
