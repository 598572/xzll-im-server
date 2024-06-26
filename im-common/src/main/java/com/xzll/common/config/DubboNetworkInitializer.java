package com.xzll.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @Author: hzz
 * @Date: 2024/6/26 20:17:08
 * @Description: 在 application.yaml文件中 指定dubbo选择网卡的方式 。如果想在yaml配置文件中配，必须使用此方式，要不就像下边老实在启动参数中配系统参数。
 * <p>
 * 在启动时指定是这样（这里示例 指定enp0s3优先， docker0网卡忽略）： java -Ddubbo.network.interface.preferred=enp0s3 -Ddubbo.network.interface.ignored=docker0 -jar im-connect-service.jar --server.port=8087
 * <p>
 * 总之你必须给这个网卡配置搞到 系统的配置中也就是说能让 System.getProperty方法读取到 ， 仅在application.ymal文件中配 想让dubbo读到？ 想都别想。dubbo是使用 System.getProperty 这样读取的：
 * <p>
 * public static boolean isPreferredNetworkInterface(NetworkInterface networkInterface) {
 * String preferredNetworkInterface = System.getProperty("dubbo.network.interface.preferred");
 * return Objects.equals(networkInterface.getDisplayName(), preferredNetworkInterface);
 * }
 */
@Slf4j
public class DubboNetworkInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String DUBBO_PREFERRED_NETWORK_INTERFACE_CONSTANT = "dubbo.network.interface.preferred";
    private static final String DUBBO_NETWORK_IGNORED_INTERFACE_CONSTANT = "dubbo.network.interface.ignored";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        String preferredInterface = applicationContext.getEnvironment().getProperty(DUBBO_PREFERRED_NETWORK_INTERFACE_CONSTANT);
        String ignoredInterface = applicationContext.getEnvironment().getProperty(DUBBO_NETWORK_IGNORED_INTERFACE_CONSTANT);

        log.info("dubbo网卡选择策略_优先:{},忽略:{}", preferredInterface, ignoredInterface);

        if (StringUtils.isNotBlank(preferredInterface)) {
            System.setProperty(DUBBO_PREFERRED_NETWORK_INTERFACE_CONSTANT, preferredInterface);
        }
        if (StringUtils.isNotBlank(ignoredInterface)) {
            System.setProperty(DUBBO_NETWORK_IGNORED_INTERFACE_CONSTANT, ignoredInterface);
        }
    }
}
