package com.kbase.mail.config;

import java.util.Properties;

import com.kbase.config.properties.MailProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Creates the SMTP client from typed, environment-backed mail settings. */
@Configuration(proxyBeanMethods = false)
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setUsername(properties.getUsername());
        sender.setPassword(properties.getAppPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties mailProperties = new Properties();
        mailProperties.put("mail.smtp.auth", Boolean.toString(properties.isAuth()));
        mailProperties.put("mail.smtp.starttls.enable", Boolean.toString(properties.isStartTls()));
        mailProperties.put("mail.smtp.starttls.required", Boolean.toString(properties.isStartTls()));
        long timeoutMillis = properties.getTimeout() == null
                ? 10_000L
                : Math.max(1L, properties.getTimeout().toMillis());
        mailProperties.put("mail.smtp.connectiontimeout", Long.toString(timeoutMillis));
        mailProperties.put("mail.smtp.timeout", Long.toString(timeoutMillis));
        mailProperties.put("mail.smtp.writetimeout", Long.toString(timeoutMillis));
        sender.setJavaMailProperties(mailProperties);
        return sender;
    }
}
