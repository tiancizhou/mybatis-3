/**
 *    Copyright 2009-2025 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.datasource.unpooled;


import org.apache.ibatis.datasource.DataSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class UnpooledDataSourceFactoryTest {

    private UnpooledDataSourceFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new UnpooledDataSourceFactory();
    }

    @Test
    public void setProperties_DriverProperties_CollectedCorrectly() {
        Properties properties = new Properties();
        properties.setProperty("driver.someProperty", "someValue");

        factory.setProperties(properties);

        UnpooledDataSource dataSource = (UnpooledDataSource) factory.getDataSource();
        Properties driverProperties = dataSource.getDriverProperties();
        assertNotNull(driverProperties);
        assertEquals("someValue", driverProperties.getProperty("someProperty"));
    }

    @Test
    public void setProperties_NonDriverProperties_SetCorrectly() {
        Properties properties = new Properties();
        properties.setProperty("url", "jdbc:mysql://localhost/test");

        factory.setProperties(properties);

        UnpooledDataSource dataSource = (UnpooledDataSource) factory.getDataSource();
        assertEquals("jdbc:mysql://localhost/test", dataSource.getUrl());
    }

    @Test
    public void setProperties_UnknownProperty_ThrowsException() {
        Properties properties = new Properties();
        properties.setProperty("unknownProperty", "someValue");

        assertThrows(DataSourceException.class, () -> factory.setProperties(properties));
    }

    @Test
    public void setProperties_NoDriverProperties_DriverPropertiesNotSet() {
        Properties properties = new Properties();
        properties.setProperty("url", "jdbc:mysql://localhost/test");

        factory.setProperties(properties);

        UnpooledDataSource dataSource = (UnpooledDataSource) factory.getDataSource();
        assertNull(dataSource.getDriverProperties());
    }
}
