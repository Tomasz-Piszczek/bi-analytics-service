package org.example.bianalyticsservice.infrastructure.events.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;

@Configuration
public class SqsClientProvider {

    @Value("${aws.access-key:test}")
    private String accessKey;

    @Value("${aws.secret-key:test}")
    private String secretKey;

    @Value("${sqs.local-uri:}")
    private String localUri;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.EU_CENTRAL_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (!localUri.isEmpty()) {
            builder.endpointOverride(URI.create(localUri));
        }

        return builder.build();
    }

    @Bean
    public SnsAsyncClient snsAsyncClient() {
        var builder = SnsAsyncClient.builder()
                .region(Region.EU_CENTRAL_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (!localUri.isEmpty()) {
            builder.endpointOverride(URI.create(localUri));
        }

        return builder.build();
    }
}