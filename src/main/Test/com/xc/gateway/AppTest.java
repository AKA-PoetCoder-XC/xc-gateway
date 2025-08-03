package com.xc.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;

@SpringBootTest
public class AppTest {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Test
    public void test1() {
        List<ServiceInstance> instances = discoveryClient.getInstances("xc-ai-service");
        System.out.println(String.format("instance count:%s", instances.size()));
    }
}
