### WARNING: Spring4Shell vulnerability
The demo application contained herein relies on a version of `org.springframework.boot` (2.4.4) that is susceptible to the recent Spring4Shell RCE vulnerability. We are currently awaiting a patched release from upstream, so that we can upgrade. In the interim, please be aware that this demo application is susceptible to the RCE when used in conjunction with JDK v9 or higher.

Please read the official announcement here: https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement for more details.