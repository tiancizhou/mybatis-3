package org.apache.ibatis.datasource.unpooled;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class DriverInitializationExample {
    private static final Map<String, Driver> registeredDrivers = new HashMap<>();

    static {
        // 初始化 registeredDrivers
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            registeredDrivers.put(driver.getClass().getName(), driver);
        }
    }

    public static void main(String[] args) {
        // 打印已注册的驱动信息
        for (Map.Entry<String, Driver> entry : registeredDrivers.entrySet()) {
            System.out.println("Driver Class: " + entry.getKey());
        }
    }
}