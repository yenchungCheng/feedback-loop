package org.example.feedbackloop.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "org.example.feedbackloop.repositories")
public class MongoDBConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database}")
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

    @PostConstruct
    public void validateConnection() {
        try {
            MongoClient client = MongoClients.create(mongoUri);
            client.getDatabase(databaseName).runCommand(new Document("ping", 1));
            System.out.println("✓ MongoDB 連線成功: " + databaseName);
            client.close();
        } catch (Exception e) {
            System.err.println("✗ MongoDB 連線失敗: " + e.getMessage());
        }
    }
}
