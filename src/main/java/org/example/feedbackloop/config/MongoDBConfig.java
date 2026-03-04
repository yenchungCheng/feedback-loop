package org.example.feedbackloop.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "org.example.feedbackloop.repositories")
public class MongoDBConfig extends AbstractMongoClientConfiguration {
    
    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/feedback_loop}")
    private String mongoUri;
    
    @Value("${spring.data.mongodb.database:feedback_loop}")
    private String databaseName;
    
    @Override
    protected String getDatabaseName() {
        return databaseName;
    }
    
    @Bean
    @Override
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }
}
