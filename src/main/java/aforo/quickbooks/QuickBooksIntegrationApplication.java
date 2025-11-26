package aforo.quickbooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for QuickBooks Integration Service.
 * Handles OAuth flow, API synchronization, and webhook processing.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class QuickBooksIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickBooksIntegrationApplication.class, args);
    }
}
