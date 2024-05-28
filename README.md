# apitoolkit-springboot

## Overview

The APItoolkit Spring Boot SDK allows seamless integration of APItoolkit with your Spring Boot applications.

## Installation

To install the APItoolkit Spring Boot SDK, add the following dependency to your `pom.xml` file within the `<dependencies>` section:

```xml
<dependency>
    <groupId>io.apitoolkit.springboot</groupId>
    <artifactId>apitoolkit-springboot</artifactId>
    <version>1.0.3</version>
</dependency>
```

## Configuration

Before integrating APItoolkit, add your APItoolkit `API_KEY` to the `application.properties` file as shown below:

```sh
apitoolkit.apikey=<YOUR_API_KEY>

## Other configuation options
apitoolkit.debug=false # Set to true to enable debug mode
apitoolkit.redactHeaders=Authorization,Cookie,accept,accept-encoding # list of headers to redact
apitoolkit.redactRequestBody=$.password,$.account_number # jsonpath list of request body fields to redact
apitoolkit.redactResponseBody=$.cvv,$.email # jsonpath list of response body fields to redact
```

## Integration

After installing the SDK and configuring the properties for your application, integrate APItoolkit into your Spring Boot server as follows:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.apitoolkit.springboot.annotations.EnableAPIToolkit;

import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@EnableAPIToolkit
@RestController
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@GetMapping("/user/{name}")
	public User getUser(@PathVariable String name) {
		return new User("Jon Doe", "example@gmail.com");
	}
}
```
