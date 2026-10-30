package com.invi.api.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GuestWriteRateLimitProperties.class)
public class GuestWriteRateLimitConfiguration {}
