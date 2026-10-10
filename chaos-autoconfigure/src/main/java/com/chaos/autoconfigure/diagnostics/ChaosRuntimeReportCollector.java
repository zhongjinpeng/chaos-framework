package com.chaos.autoconfigure.diagnostics;

import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.EndpointStatus;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.EndpointUrl;
import com.chaos.autoconfigure.diagnostics.ChaosFeatureReport.RuntimeDetails;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;

/**
 * 收集启动报告中的运行环境、访问地址和配置明细。
 */
final class ChaosRuntimeReportCollector {

    private static final String ACTUATOR_MARKER = "org.springframework.boot.actuate.endpoint.annotation.Endpoint";

    private static final String SPRINGDOC_MARKER = "org.springdoc.core.configuration.SpringDocConfiguration";

    private static final Set<String> RUNTIME_ENVIRONMENT_KEYS = Set.of(
            "AWS_REGION",
            "AWS_DEFAULT_REGION",
            "ALIBABA_CLOUD_REGION_ID",
            "BUILD_NUMBER",
            "CI",
            "GIT_COMMIT",
            "HOSTNAME",
            "LANG",
            "LC_ALL",
            "NODE_NAME",
            "POD_NAME",
            "POD_NAMESPACE",
            "TZ");

    private static final List<String> RUNTIME_ENVIRONMENT_PREFIXES = List.of(
            "CHAOS_", "JAVA_", "JDK_", "LOGGING_", "MANAGEMENT_", "SERVER_", "SPRING_");

    private static final Set<String> RUNTIME_JVM_PROPERTY_KEYS = Set.of(
            "PID",
            "file.encoding",
            "java.awt.headless",
            "java.class.version",
            "java.home",
            "java.runtime.name",
            "java.runtime.version",
            "java.specification.version",
            "java.vendor",
            "java.version",
            "java.vm.name",
            "java.vm.vendor",
            "java.vm.version",
            "native.encoding",
            "os.arch",
            "os.name",
            "os.version",
            "user.country",
            "user.language",
            "user.timezone");

    private static final List<String> RUNTIME_JVM_PROPERTY_PREFIXES =
            List.of("chaos.", "logging.", "management.", "server.", "spring.");

    private final Environment environment;

    private final ApplicationContext applicationContext;

    private final boolean includeConfigurationDetails;

    private final Map<?, ?> systemEnvironment;

    private final Map<?, ?> jvmSystemProperties;

    private final String applicationEnvironmentPrefix;

    private final String applicationJvmPropertyPrefix;

    ChaosRuntimeReportCollector(
            Environment environment,
            ApplicationContext applicationContext,
            boolean includeConfigurationDetails) {
        this(environment, applicationContext, includeConfigurationDetails, System.getenv(), System.getProperties());
    }

    ChaosRuntimeReportCollector(
            Environment environment,
            ApplicationContext applicationContext,
            boolean includeConfigurationDetails,
            Map<?, ?> systemEnvironment,
            Map<?, ?> jvmSystemProperties) {
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.includeConfigurationDetails = includeConfigurationDetails;
        this.systemEnvironment = systemEnvironment == null ? Map.of() : systemEnvironment;
        this.jvmSystemProperties = jvmSystemProperties == null ? Map.of() : jvmSystemProperties;
        String applicationName = environment.getProperty("spring.application.name", "application");
        this.applicationEnvironmentPrefix = applicationPrefix(applicationName, '_').toUpperCase(Locale.ROOT);
        this.applicationJvmPropertyPrefix = applicationPrefix(applicationName, '.').toLowerCase(Locale.ROOT);
    }

    RuntimeDetails collect() {
        String webApplicationType = webApplicationType();
        boolean webApplication = !"NONE".equals(webApplicationType);
        Integer applicationPort = webApplication ? applicationPort() : null;
        String bindAddress = environment.getProperty("server.address", "0.0.0.0");
        boolean actuatorPresent = webApplication && isPresent(ACTUATOR_MARKER);
        Integer managementPort = actuatorPresent ? managementPort(applicationPort) : null;

        List<EndpointUrl> endpoints = endpoints(
                webApplicationType, bindAddress, applicationPort, managementPort, actuatorPresent);
        if (!includeConfigurationDetails) {
            return new RuntimeDetails(
                    webApplicationType, bindAddress, applicationPort, managementPort,
                    endpoints, List.of(), Map.of(), Map.of(), Map.of());
        }

        YamlConfiguration yaml = yamlConfiguration();
        return new RuntimeDetails(
                webApplicationType,
                bindAddress,
                applicationPort,
                managementPort,
                endpoints,
                yaml.sources(),
                yaml.values(),
                maskedSortedMap(systemEnvironment, this::isRelevantSystemEnvironment),
                maskedSortedMap(jvmSystemProperties, this::isRelevantJvmSystemProperty));
    }

    private String webApplicationType() {
        if (applicationContext != null) {
            for (Class<?> type = applicationContext.getClass(); type != null; type = type.getSuperclass()) {
                String name = type.getName().toLowerCase(Locale.ROOT);
                if (name.contains("reactive") && name.contains("web")) {
                    return "REACTIVE";
                }
                if (name.contains("servlet") && name.contains("web")) {
                    return "SERVLET";
                }
            }
            if (applicationContext instanceof WebServerApplicationContext) {
                return "WEB";
            }
        }
        String configured = environment.getProperty("spring.main.web-application-type");
        return configured == null || configured.isBlank() ? "NONE" : configured.strip().toUpperCase(Locale.ROOT);
    }

    private Integer applicationPort() {
        Integer localPort = integerProperty("local.server.port");
        if (localPort != null && localPort > 0) {
            return localPort;
        }
        if (applicationContext instanceof WebServerApplicationContext webContext
                && webContext.getWebServer() != null
                && webContext.getWebServer().getPort() > 0) {
            return webContext.getWebServer().getPort();
        }
        return environment.getProperty("server.port", Integer.class, 8080);
    }

    private Integer managementPort(Integer applicationPort) {
        Integer configured = integerProperty("management.server.port");
        if (configured != null && configured < 0) {
            return null;
        }
        Integer localPort = integerProperty("local.management.port");
        if (localPort != null && localPort > 0) {
            return localPort;
        }
        if (configured != null && configured > 0) {
            return configured;
        }
        return applicationPort;
    }

    private List<EndpointUrl> endpoints(
            String webApplicationType,
            String bindAddress,
            Integer applicationPort,
            Integer managementPort,
            boolean actuatorPresent) {
        if ("NONE".equals(webApplicationType) || applicationPort == null) {
            return List.of();
        }
        String applicationScheme = environment.getProperty("server.ssl.enabled", Boolean.class, false) ? "https" : "http";
        String applicationPath = applicationPath(webApplicationType);
        String applicationUrl = url(applicationScheme, displayHost(bindAddress), applicationPort, applicationPath);
        List<EndpointUrl> endpoints = new ArrayList<>();
        endpoints.add(new EndpointUrl("application", applicationUrl, EndpointStatus.ENABLED));

        if (actuatorPresent && managementPort != null) {
            appendActuatorEndpoints(endpoints, applicationPath, bindAddress, applicationPort, managementPort);
        }
        if (isPresent(SPRINGDOC_MARKER) || hasSpringdocConfiguration()) {
            appendSpringdocEndpoints(endpoints, applicationScheme, bindAddress, applicationPort, applicationPath);
        }
        return List.copyOf(endpoints);
    }

    private void appendActuatorEndpoints(
            List<EndpointUrl> endpoints,
            String applicationPath,
            String bindAddress,
            int applicationPort,
            int managementPort) {
        boolean separateManagementServer = managementPort != applicationPort;
        String managementAddress = environment.getProperty("management.server.address", bindAddress);
        boolean managementSsl = separateManagementServer
                ? environment.getProperty("management.server.ssl.enabled", Boolean.class, false)
                : environment.getProperty("server.ssl.enabled", Boolean.class, false);
        String managementScheme = managementSsl ? "https" : "http";
        String managementContextPath = separateManagementServer
                ? environment.getProperty("management.server.base-path", "")
                : applicationPath;
        String actuatorPath = joinPath(
                managementContextPath,
                environment.getProperty("management.endpoints.web.base-path", "/actuator"));
        String baseUrl = url(managementScheme, displayHost(managementAddress), managementPort, actuatorPath);
        boolean discoveryEnabled = environment.getProperty(
                "management.endpoints.web.discovery.enabled", Boolean.class, true);
        endpoints.add(new EndpointUrl(
                "actuator", baseUrl, discoveryEnabled ? EndpointStatus.EXPOSED : EndpointStatus.DISABLED));
        endpoints.add(actuatorEndpoint("health", baseUrl));
    }

    private EndpointUrl actuatorEndpoint(String endpointId, String actuatorBaseUrl) {
        String mapping = environment.getProperty(
                "management.endpoints.web.path-mapping." + endpointId, endpointId);
        boolean disabled = !environment.getProperty(
                "management.endpoint." + endpointId + ".enabled", Boolean.class, true)
                || "none".equalsIgnoreCase(environment.getProperty(
                        "management.endpoint." + endpointId + ".access", ""));
        EndpointStatus status = disabled
                ? EndpointStatus.DISABLED
                : (isExposed(endpointId) ? EndpointStatus.EXPOSED : EndpointStatus.NOT_EXPOSED);
        return new EndpointUrl(endpointId, appendUrlPath(actuatorBaseUrl, mapping), status);
    }

    private void appendSpringdocEndpoints(
            List<EndpointUrl> endpoints,
            String scheme,
            String bindAddress,
            int applicationPort,
            String applicationPath) {
        boolean apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true);
        String apiDocsPath = joinPath(
                applicationPath, environment.getProperty("springdoc.api-docs.path", "/v3/api-docs"));
        endpoints.add(new EndpointUrl(
                "openapi",
                url(scheme, displayHost(bindAddress), applicationPort, apiDocsPath),
                apiDocsEnabled ? EndpointStatus.ENABLED : EndpointStatus.DISABLED));

        boolean swaggerEnabled = environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true);
        String swaggerPath = joinPath(
                applicationPath, environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html"));
        endpoints.add(new EndpointUrl(
                "swagger-ui",
                url(scheme, displayHost(bindAddress), applicationPort, swaggerPath),
                swaggerEnabled ? EndpointStatus.ENABLED : EndpointStatus.DISABLED));
    }

    private boolean isExposed(String endpointId) {
        Set<String> included = stringSet("management.endpoints.web.exposure.include", Set.of("health"));
        Set<String> excluded = stringSet("management.endpoints.web.exposure.exclude", Set.of());
        return (included.contains("*") || included.contains(endpointId)) && !excluded.contains(endpointId);
    }

    private Set<String> stringSet(String property, Set<String> defaultValue) {
        String[] values = environment.getProperty(property, String[].class);
        if (values == null) {
            return defaultValue;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    result.add(item.strip().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private YamlConfiguration yamlConfiguration() {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return YamlConfiguration.empty();
        }
        Set<String> sources = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            collectYamlProperties(propertySource, sources, keys);
        }
        Map<String, String> values = new LinkedHashMap<>();
        keys.stream().sorted().forEach(key -> {
            String value;
            try {
                value = environment.getProperty(key);
            } catch (RuntimeException ex) {
                value = "<unavailable: " + ex.getClass().getSimpleName() + ">";
            }
            values.put(key, ChaosSettingMasker.mask(key, value == null ? "" : value));
        });
        List<String> maskedSources = sources.stream()
                .map(source -> ChaosSettingMasker.mask("configuration-source", source))
                .toList();
        return new YamlConfiguration(maskedSources, values);
    }

    private static void collectYamlProperties(
            PropertySource<?> propertySource,
            Collection<String> sources,
            Collection<String> keys) {
        if (propertySource instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested : composite.getPropertySources()) {
                collectYamlProperties(nested, sources, keys);
            }
        }
        String name = propertySource.getName();
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (!(normalizedName.contains(".yml") || normalizedName.contains(".yaml"))) {
            return;
        }
        sources.add(name);
        if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
            for (String propertyName : enumerable.getPropertyNames()) {
                keys.add(propertyName);
            }
        }
    }

    private static Map<String, String> maskedSortedMap(Map<?, ?> source, Predicate<String> keyFilter) {
        Map<String, String> sorted = new TreeMap<>();
        source.forEach((key, value) -> {
            String textKey = String.valueOf(key);
            if (keyFilter.test(textKey)) {
                sorted.put(textKey, ChaosSettingMasker.mask(textKey, value == null ? "" : String.valueOf(value)));
            }
        });
        return new LinkedHashMap<>(sorted);
    }

    private boolean isRelevantSystemEnvironment(String key) {
        String normalized = key.toUpperCase(Locale.ROOT);
        return RUNTIME_ENVIRONMENT_KEYS.contains(normalized)
                || startsWithAny(normalized, RUNTIME_ENVIRONMENT_PREFIXES)
                || normalized.startsWith(applicationEnvironmentPrefix);
    }

    private boolean isRelevantJvmSystemProperty(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return RUNTIME_JVM_PROPERTY_KEYS.contains(key)
                || startsWithAny(normalized, RUNTIME_JVM_PROPERTY_PREFIXES)
                || normalized.startsWith(applicationJvmPropertyPrefix);
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String applicationPrefix(String applicationName, char separator) {
        String normalized = applicationName == null ? "application" : applicationName.strip();
        int delimiter = firstDelimiter(normalized);
        String organization = delimiter > 0 ? normalized.substring(0, delimiter) : normalized;
        String prefix = organization.replaceAll("[^A-Za-z0-9]+", String.valueOf(separator));
        return prefix.isBlank() ? "application" + separator : prefix + separator;
    }

    private static int firstDelimiter(String value) {
        int result = value.length();
        for (char delimiter : new char[] {'-', '_', '.'}) {
            int index = value.indexOf(delimiter);
            if (index >= 0 && index < result) {
                result = index;
            }
        }
        return result;
    }

    private boolean hasSpringdocConfiguration() {
        return environment.containsProperty("springdoc.api-docs.enabled")
                || environment.containsProperty("springdoc.api-docs.path")
                || environment.containsProperty("springdoc.swagger-ui.enabled")
                || environment.containsProperty("springdoc.swagger-ui.path");
    }

    private boolean isPresent(String className) {
        ClassLoader classLoader = applicationContext == null
                ? ChaosRuntimeReportCollector.class.getClassLoader()
                : applicationContext.getClassLoader();
        return ClassUtils.isPresent(className, classLoader);
    }

    private Integer integerProperty(String property) {
        try {
            return environment.getProperty(property, Integer.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String applicationPath(String webApplicationType) {
        if ("REACTIVE".equals(webApplicationType)) {
            return normalizePath(environment.getProperty("spring.webflux.base-path", ""));
        }
        return normalizePath(environment.getProperty("server.servlet.context-path", ""));
    }

    private static String displayHost(String bindAddress) {
        if (bindAddress == null || bindAddress.isBlank()
                || "0.0.0.0".equals(bindAddress)
                || "::".equals(bindAddress)
                || "[::]".equals(bindAddress)) {
            return "localhost";
        }
        String host = bindAddress.strip();
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static String url(String scheme, String host, int port, String path) {
        return scheme + "://" + host + ":" + port + normalizePath(path);
    }

    private static String appendUrlPath(String baseUrl, String path) {
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + normalizePath(path)
                : baseUrl + normalizePath(path);
    }

    private static String joinPath(String... parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank() || "/".equals(part.strip())) {
                continue;
            }
            String normalized = part.strip();
            int start = normalized.startsWith("/") ? 1 : 0;
            int end = normalized.endsWith("/") ? normalized.length() - 1 : normalized.length();
            if (end > start) {
                result.append('/').append(normalized, start, end);
            }
        }
        return result.isEmpty() ? "/" : result.toString();
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.strip())) {
            return "/";
        }
        String normalized = path.strip();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private record YamlConfiguration(List<String> sources, Map<String, String> values) {

        private static YamlConfiguration empty() {
            return new YamlConfiguration(List.of(), Map.of());
        }
    }
}
