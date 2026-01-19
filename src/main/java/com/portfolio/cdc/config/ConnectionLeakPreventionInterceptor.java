// This class has been superseded by RequestLifecycleInterceptor.
// It was incorrectly implementing both HandlerInterceptor and
// Hibernate StatementInspector in a single class (SRP violation), and
// contained tenant-context logic that does not belong in the CDC service.
// See: RequestLifecycleInterceptor.java
package com.portfolio.cdc.config;
