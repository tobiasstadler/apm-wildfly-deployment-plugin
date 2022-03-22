# apm-wildfly-deployment-plugin

An Elastic APM agent plugin that sets the service name for a deployment on a WildFly application server to the implementation-title attribute of the META-INF/MANIFEST.MF.

## Supported Versions

| Plugin | Elastic APM Agent |
| :--- | :--- |
| 1.0+ | 1.18.0+ |
| 2.0+ | 1.27.0+ |
| 3.0+ | 1.29.0+ |
| 4.0+ | 1.30.0+ |

## Installation

Set the [`plugins_dir`](https://www.elastic.co/guide/en/apm/agent/java/current/config-core.html#config-plugins-dir) agent configuration option and copy the plugin to specified directory.

Remove `org.jboss.as.*` from the `classes_excluded_from_instrumentation_default` agent configuration option, e.g. set it to `(?-i)org.infinispan*,(?-i)org.apache.xerces*,(?-i)io.undertow.core*,(?-i)org.eclipse.jdt.ecj*,(?-i)org.wildfly.extension.*,(?-i)org.wildfly.security*`.
