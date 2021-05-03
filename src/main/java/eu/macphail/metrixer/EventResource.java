package eu.macphail.metrixer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventResource implements HealthIndicator {
    private final Logger log = LoggerFactory.getLogger(EventResource.class);

    private final KafkaTemplate<String, String> template;
    private final Counter successfulKafkaSendCounter;
    private final Counter errorKafkaSendCounter;

    public EventResource(KafkaTemplate<String, String> template, MeterRegistry registry) {
        this.template = template;
        this.successfulKafkaSendCounter = registry.counter("successfulKafkaSendCounter");
        this.errorKafkaSendCounter = registry.counter("errorKafkaSendCounter");
    }

    @GetMapping("/events")
    public String getEvents() {
        ListenableFuture<SendResult<String, String>> future = template.send("random_topic", null, "my data");
        future.addCallback(sentRecordsCallback());
        return "events";
    }

    @GetMapping("/failure")
    public String getFailures() {
        this.errorKafkaSendCounter.increment();
        return "events";
    }

    private ListenableFutureCallback<SendResult<String, String>> sentRecordsCallback() {
        return new ListenableFutureCallback<>() {
            @Override
            public void onFailure(Throwable ex) {
                log.info("Failed to send record", ex);
                errorKafkaSendCounter.increment();
            }

            @Override
            public void onSuccess(SendResult<String, String> result) {
                successfulKafkaSendCounter.increment();
            }
        };
    }

    @Override
    public Health health() {
        return errorKafkaSendCounter.count() > 10
                ? Health.down().build()
                : Health.up().build();
    }
}
