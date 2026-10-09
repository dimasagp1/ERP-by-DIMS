package id.herbatech.erp;

import org.springframework.boot.SpringApplication;

public class TestErpBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ErpBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
