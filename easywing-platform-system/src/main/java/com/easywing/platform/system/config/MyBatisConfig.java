package com.easywing.platform.system.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 扫描配置。
 * <p>
 * 独立于应用主类，避免 Web 层切片测试（@WebMvcTest）加载 Mapper 后因缺少
 * SqlSessionFactory 导致上下文启动失败。
 */
@Configuration
@MapperScan(value = "com.easywing.platform.system.mapper", annotationClass = Mapper.class)
public class MyBatisConfig {}
