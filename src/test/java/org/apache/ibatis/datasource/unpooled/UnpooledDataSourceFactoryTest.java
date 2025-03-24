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
