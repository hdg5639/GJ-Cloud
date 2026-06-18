package gj.cloud.vm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VmApplication {

    public static void main(String[] args) {
        SpringApplication.run(VmApplication.class, args);
    }
}
