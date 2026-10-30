package com.invi.api.invitation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InvitationExpiryProperties.class)
public class InvitationExpiryConfiguration {}
