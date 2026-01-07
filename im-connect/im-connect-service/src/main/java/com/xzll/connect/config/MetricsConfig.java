package com.xzll.connect.config;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.sun.management.OperatingSystemMXBean;
import io.prometheus.client.hotspot.DefaultExports;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.HTTPServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;



import jakarta.annotation.Resource;


/**
 * @Author: hzz
 * @Date: 2024/7/3 22:47:05
 * @Description:
 */
@Configuration
@Slf4j
public class MetricsConfig {
    @Resource
    private IMConnectServerConfig imConnectServerConfig;

    @Bean
    public MetricRegistry metricRegistry() {

        MetricRegistry metricRegistry = new MetricRegistry();

        // 注册 JVM 内存使用情况指标
        metricRegistry.register("jvm.memory", new MemoryUsageGaugeSet());
        // 注册 JVM 线程指标
        metricRegistry.register("jvm.threads", new ThreadStatesGaugeSet());
        // 注册 JVM GC 指标
        metricRegistry.register("jvm.gc", new GarbageCollectorMetricSet());

        // 注册系统 CPU 和内存指标
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        metricRegistry.register("system.cpu.load", (Gauge<Double>) osBean::getSystemCpuLoad);
        metricRegistry.register("system.memory.total", (Gauge<Long>) osBean::getTotalPhysicalMemorySize);
        metricRegistry.register("system.memory.free", (Gauge<Long>) osBean::getFreePhysicalMemorySize);
        return metricRegistry;
    }
    @Bean
    public HTTPServer prometheusHttpServer(MetricRegistry metricRegistry) throws IOException {
        CollectorRegistry.defaultRegistry.register(new DropwizardExports(metricRegistry));
        // 启动 Prometheus HTTP Server，端口号为 10000
        HTTPServer httpServer = null;
        // 注册 JVM 和线程等指标
        DefaultExports.initialize();
        try {
            // 启动 HTTP 服务器来暴露指标
            httpServer = new HTTPServer(imConnectServerConfig.getPrometheusPort());
        } catch (Exception e) {
            log.error("prometheus采集服务器创建失败e:", e);
        }
        return httpServer;
    }
}
