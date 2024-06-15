<div align="center">

![APItoolkit's Logo](https://github.com/apitoolkit/.github/blob/main/images/logo-white.svg?raw=true#gh-dark-mode-only)
![APItoolkit's Logo](https://github.com/apitoolkit/.github/blob/main/images/logo-black.svg?raw=true#gh-light-mode-only)

## Springboot SDK

[![APItoolkit SDK](https://img.shields.io/badge/APItoolkit-SDK-0068ff?logo=spring)](https://github.com/topics/apitoolkit-sdk) [![Join Discord Server](https://img.shields.io/badge/Chat-Discord-7289da)](https://discord.gg/dEB6EjQnKB) [![APItoolkit Docs](https://img.shields.io/badge/Read-Docs-0068ff)](https://apitoolkit.io/docs/sdks/java/springboot?utm_source=github-sdks)

APItoolkit is an end-to-end API and web services management toolkit for engineers and customer support teams. To integrate your Springboot Java application with APItoolkit, you need to use this SDK to monitor incoming traffic, aggregate the requests, and then deliver them to the APItoolkit's servers.

</div>

---

## Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
- [Contributing and Help](#contributing-and-help)
- [License](#license)

---

## Installation

To install the SDK, kindly add the following dependency to your `pom.xml` file within the `<dependencies>` section like so:

```xml
<dependency>
    <groupId>io.apitoolkit.springboot</groupId>
    <artifactId>apitoolkit-springboot</artifactId>
    <version>1.0.6</version>
</dependency>
```

## Configuration

First, add your APItoolkit API key to the `application.properties` file like so:

```sh
apitoolkit.apikey={ENTER_YOUR_API_KEY_HERE};

# Other configuation options
apitoolkit.debug=false
# ...
```

> [!NOTE]
>
> The `{ENTER_YOUR_API_KEY_HERE}` demo string should be replaced with the [API key](https://apitoolkit.io/docs/dashboard/settings-pages/api-keys?utm_source=github-sdks) generated from the APItoolkit dashboard.

<br />

Then, initialize the SDK like so:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Import APItoolkit annotation
import io.apitoolkit.springboot.annotations.EnableAPIToolkit;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
// Add APIToolkit custom annotation
@EnableAPIToolkit
@RestController
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@GetMapping("/greet/{name}")
	public String getUser(@PathVariable String name) {
		return "Hello, " + name;
	}
}
```

<br />

> [!IMPORTANT]
>
> To learn more configuration options (redacting fields, error reporting, outgoing requests, etc.), please read this [SDK documentation](https://apitoolkit.io/docs/sdks/java/springboot?utm_source=github-sdks).

## Contributing and Help

To contribute to the development of this SDK or request help from the community and our team, kindly do any of the following:

- Read our [Contributors Guide](https://github.com/apitoolkit/.github/blob/main/CONTRIBUTING.md).
- Join our community [Discord Server](https://discord.gg/dEB6EjQnKB).
- Create a [new issue](https://github.com/apitoolkit/apitoolkit-dotnet/issues/new/choose) in this repository.

## License

This repository is published under the [MIT](LICENSE) license.

---

<div align="center">
    
<a href="https://apitoolkit.io?utm_source=github-sdks" target="_blank" rel="noopener noreferrer"><img src="https://github.com/apitoolkit/.github/blob/main/images/icon.png?raw=true" width="40" /></a>

</div>
