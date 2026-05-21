package com.noos.backend.lighting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class WizLightingService {

    private static final int WIZ_PORT = 38899;
    private static final int MIN_BRIGHTNESS = 10;
    private static final int MAX_BRIGHTNESS = 88;
    private static final int WRITE_RETRY_COUNT = 2;
    private static final int WRITE_RETRY_DELAY_MS = 35;
    private static final String MODE_CCT = "cct";
    private static final String MODE_RGB = "rgb";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean autoApply;
    private final List<String> configuredBulbIps;
    private final int commandTimeoutMs;
    private final int alternateIntervalSec;
    private final ExecutorService executor;
    private final AtomicReference<Future<?>> activeJob = new AtomicReference<>();
    private final AtomicReference<String> activeJobId = new AtomicReference<>();
    private final AtomicReference<Map<String, Map<String, Object>>> restoreBaselinePayloads =
            new AtomicReference<>(Map.of());
    private volatile Map<String, Object> activeJobStatus = Map.of("active", false);

    public WizLightingService(
            ObjectMapper objectMapper,
            @Value("${noos.lighting.wiz.enabled:false}") boolean enabled,
            @Value("${noos.lighting.wiz.auto-apply:false}") boolean autoApply,
            @Value("${noos.lighting.wiz.bulb-ips:}") String bulbIps,
            @Value("${noos.lighting.wiz.command-timeout-ms:700}") int commandTimeoutMs,
            @Value("${noos.lighting.wiz.alternate-interval-sec:10}") int alternateIntervalSec
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.autoApply = autoApply;
        this.configuredBulbIps = parseCsv(bulbIps);
        this.commandTimeoutMs = Math.max(150, commandTimeoutMs);
        this.alternateIntervalSec = Math.max(1, alternateIntervalSec);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "noos-wiz-lighting");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean shouldAutoApply() {
        return enabled && autoApply && !configuredBulbIps.isEmpty();
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>(activeJobStatus);
        Map<String, Map<String, Object>> baseline = restoreBaselinePayloads.get();
        status.put("enabled", enabled);
        status.put("autoApply", autoApply);
        status.put("bulbIps", configuredBulbIps);
        status.put("alternateIntervalSec", alternateIntervalSec);
        status.put("restoreAvailable", !baseline.isEmpty());
        status.put("restoreDeviceCount", baseline.size());
        return status;
    }

    public Map<String, Object> discoverConfiguredDevices() {
        ensureEnabled();
        List<Map<String, Object>> devices = new ArrayList<>();
        for (String ip : configuredBulbIps) {
            Map<String, Object> device = new LinkedHashMap<>();
            device.put("ip", ip);
            device.put("system", sendReadCommand(ip, Map.of("method", "getSystemConfig", "params", Map.of())));
            device.put("pilot", sendReadCommand(ip, Map.of("method", "getPilot", "params", Map.of())));
            devices.add(device);
        }
        return Map.of(
                "deviceCount", devices.size(),
                "devices", devices
        );
    }

    public Map<String, Object> stopActiveJob() {
        Future<?> previous = activeJob.getAndSet(null);
        String stoppedJobId = activeJobId.getAndSet(null);
        if (previous != null) {
            previous.cancel(true);
        }
        Map<String, Object> restoreResult = restoreBaselineAfterCancellation("stop");
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active", false);
        if (stoppedJobId != null) {
            status.put("jobId", stoppedJobId);
        }
        status.put("stoppedAt", Instant.now().toString());
        status.putAll(restoreResult);
        activeJobStatus = status;
        return status();
    }

    public Map<String, Object> startAlternatingFromPayload(Map<String, Object> payload) {
        return startAlternatingFromPayload(payload, alternateIntervalSec, 0);
    }

    public Map<String, Object> startAlternatingFromPayload(Map<String, Object> payload, int intervalSec, int cycles) {
        ensureEnabled();
        Map<String, Object> lightingSpec = extractLightingSpec(payload);
        List<Map<String, Object>> phases = listOfMaps(lightingSpec.get("phases"));
        if (phases.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "lighting_spec.phases is required");
        }
        return startAlternatingJob(phases, Math.max(1, intervalSec), Math.max(0, cycles), configuredBulbIps);
    }

    public Map<String, Object> startTestAlternate(Map<String, Object> payload) {
        ensureEnabled();
        String primaryHex = stringValue(payload.getOrDefault("primaryHex", "#f2decc"));
        String secondaryHex = stringValue(payload.getOrDefault("secondaryHex", "#4c86ff"));
        int brightness = boundedInt(payload.get("brightnessPercent"), 42, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
        int intervalSec = boundedInt(payload.get("intervalSec"), alternateIntervalSec, 1, 3600);
        int cycles = boundedInt(payload.get("cycles"), 1, 1, 120);
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("name", "test-alternate");
        phase.put("duration_sec", intervalSec * cycles * 2);
        phase.put("primary_hex", primaryHex);
        phase.put("secondary_hex", secondaryHex);
        phase.put("brightness_percent", brightness);
        return startAlternatingJob(List.of(phase), intervalSec, cycles, configuredBulbIps);
    }

    private Map<String, Object> startAlternatingJob(
            List<Map<String, Object>> phases,
            int intervalSec,
            int cycles,
            List<String> bulbIps
    ) {
        if (bulbIps.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No WiZ bulb IPs are configured");
        }
        Future<?> previous = activeJob.getAndSet(null);
        if (previous != null) {
            previous.cancel(true);
        }

        Map<String, Map<String, Object>> baseline = restoreBaselinePayloads.get();
        if (baseline.isEmpty()) {
            baseline = captureRestoreBaseline(bulbIps);
            restoreBaselinePayloads.set(baseline);
        }

        String jobId = UUID.randomUUID().toString();
        Map<String, Object> started = new LinkedHashMap<>();
        started.put("active", true);
        started.put("jobId", jobId);
        started.put("startedAt", Instant.now().toString());
        started.put("bulbIps", bulbIps);
        started.put("intervalSec", intervalSec);
        started.put("cycles", cycles);
        started.put("phaseCount", phases.size());
        started.put("restoreBaselineCaptured", !baseline.isEmpty());
        started.put("restoreDeviceCount", baseline.size());
        activeJobStatus = started;
        activeJobId.set(jobId);

        Future<?> next = executor.submit(() -> runAlternatingJob(jobId, phases, intervalSec, cycles, bulbIps));
        activeJob.set(next);
        return status();
    }

    private void runAlternatingJob(
            String jobId,
            List<Map<String, Object>> phases,
            int intervalSec,
            int cycles,
            List<String> bulbIps
    ) {
        try {
            int executedCommands = 0;
            for (Map<String, Object> phase : phases) {
                String phaseName = stringValue(phase.getOrDefault("name", "phase"));
                int brightness = boundedInt(phase.get("brightness_percent"), 42, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
                String primaryMode = stringValue(phase.getOrDefault("primary_mode", MODE_CCT));
                int primaryCctKelvin = boundedInt(
                        phase.get("primary_cct_kelvin"),
                        boundedInt(phase.get("cct_kelvin"), 4200, 2200, 6500),
                        2200,
                        6500
                );
                String primaryHex = stringValue(phase.getOrDefault("primary_hex", "#ffffff"));
                String secondaryHex = stringValue(phase.getOrDefault("secondary_hex", primaryHex));
                int durationSec = boundedInt(phase.get("duration_sec"), intervalSec * 2, intervalSec, 24 * 60 * 60);
                int steps = cycles > 0 ? cycles * 2 : Math.max(1, (int) Math.ceil((double) durationSec / intervalSec));

                for (int step = 0; step < steps; step += 1) {
                    if (Thread.currentThread().isInterrupted()) {
                        markStopped(jobId, executedCommands, "cancelled");
                        return;
                    }
                    boolean usePrimary = step % 2 == 0;
                    boolean usePrimaryCct = usePrimary && MODE_CCT.equalsIgnoreCase(primaryMode);
                    String selectedMode = usePrimaryCct ? MODE_CCT : MODE_RGB;
                    String selectedHex = usePrimary ? primaryHex : secondaryHex;
                    Integer selectedCctKelvin = usePrimaryCct ? primaryCctKelvin : null;
                    Map<String, Object> payload = usePrimaryCct
                            ? cctPayload(primaryCctKelvin, brightness)
                            : rgbPayload(selectedHex, brightness);
                    List<Map<String, Object>> stepErrors = new ArrayList<>();
                    for (String ip : bulbIps) {
                        if (Thread.currentThread().isInterrupted() || !jobId.equals(activeJobId.get())) {
                            markStopped(jobId, executedCommands, "cancelled");
                            return;
                        }
                        try {
                            sendWriteCommand(ip, payload);
                            executedCommands += 1;
                        } catch (ResponseStatusException error) {
                            stepErrors.add(Map.of(
                                    "ip", ip,
                                    "error", error.getReason() != null ? error.getReason() : error.getMessage()
                            ));
                        }
                    }
                    updateRunningStatus(
                            jobId,
                            phaseName,
                            selectedMode,
                            selectedHex,
                            selectedCctKelvin,
                            brightness,
                            executedCommands,
                            stepErrors
                    );
                    sleepInterruptibly(intervalSec * 1000L);
                }
            }
            if (jobId.equals(activeJobId.get())) {
                activeJobStatus = Map.of(
                        "active", false,
                        "jobId", jobId,
                        "completedAt", Instant.now().toString(),
                        "result", "completed"
                );
            }
        } catch (RuntimeException error) {
            if (jobId.equals(activeJobId.get())) {
                activeJobStatus = Map.of(
                        "active", false,
                        "jobId", jobId,
                        "failedAt", Instant.now().toString(),
                        "error", error.getMessage()
                );
            }
        } finally {
            if (jobId.equals(activeJobId.get())) {
                activeJobId.set(null);
            }
        }
    }

    private void updateRunningStatus(
            String jobId,
            String phaseName,
            String mode,
            String hex,
            Integer cctKelvin,
            int brightness,
            int executedCommands,
            List<Map<String, Object>> stepErrors
    ) {
        if (!jobId.equals(activeJobId.get())) {
            return;
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active", true);
        status.put("jobId", jobId);
        status.put("phase", phaseName);
        status.put("currentMode", mode);
        if (MODE_CCT.equalsIgnoreCase(mode) && cctKelvin != null) {
            status.put("currentCctKelvin", cctKelvin);
        } else {
            status.put("currentHex", hex);
        }
        status.put("brightnessPercent", brightness);
        status.put("executedCommands", executedCommands);
        status.put("updatedAt", Instant.now().toString());
        status.put("bulbIps", configuredBulbIps);
        if (!stepErrors.isEmpty()) {
            status.put("partialFailures", stepErrors);
        }
        activeJobStatus = status;
    }

    private void markStopped(String jobId, int executedCommands, String result) {
        if (!jobId.equals(activeJobId.get())) {
            return;
        }
        activeJobStatus = Map.of(
                "active", false,
                "jobId", jobId,
                "stoppedAt", Instant.now().toString(),
                "executedCommands", executedCommands,
                "result", result
        );
    }

    private Map<String, Object> rgbPayload(String hex, int brightness) {
        int[] rgb = rgbFromHex(hex);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("state", true);
        params.put("r", rgb[0]);
        params.put("g", rgb[1]);
        params.put("b", rgb[2]);
        params.put("dimming", Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness)));
        return Map.of("method", "setPilot", "params", params);
    }

    private Map<String, Object> cctPayload(int cctKelvin, int brightness) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("state", true);
        params.put("temp", Math.max(2200, Math.min(6500, cctKelvin)));
        params.put("dimming", Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness)));
        return Map.of("method", "setPilot", "params", params);
    }

    private Map<String, Map<String, Object>> captureRestoreBaseline(List<String> bulbIps) {
        Map<String, Map<String, Object>> baseline = new LinkedHashMap<>();
        for (String ip : bulbIps) {
            Map<String, Object> response = sendReadCommand(ip, Map.of("method", "getPilot", "params", Map.of()));
            Map<String, Object> pilot = mapValue(response.get("result"));
            Map<String, Object> restoreParams = restoreParamsFromPilot(pilot);
            if (!restoreParams.isEmpty()) {
                baseline.put(ip, Map.of("method", "setPilot", "params", restoreParams));
            }
        }
        return Map.copyOf(baseline);
    }

    private Map<String, Object> restoreBaselineAfterCancellation(String reason) {
        Map<String, Map<String, Object>> baseline = restoreBaselinePayloads.get();
        if (baseline.isEmpty()) {
            return Map.of(
                    "restored", false,
                    "restoreReason", reason,
                    "restoreMessage", "No saved WiZ baseline"
            );
        }

        Future<Map<String, Object>> restoreFuture = executor.submit(() -> restoreSavedBaseline(reason));
        try {
            int timeoutMs = Math.max(1500, commandTimeoutMs * Math.max(1, baseline.size()) + 1000);
            return restoreFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            restoreFuture.cancel(true);
            return Map.of(
                    "restored", false,
                    "restoreReason", reason,
                    "restoreError", "Interrupted while restoring WiZ baseline"
            );
        } catch (CancellationException | ExecutionException | TimeoutException error) {
            restoreFuture.cancel(true);
            return Map.of(
                    "restored", false,
                    "restoreReason", reason,
                    "restoreError", error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()
            );
        }
    }

    private Map<String, Object> restoreSavedBaseline(String reason) {
        Map<String, Map<String, Object>> baseline = restoreBaselinePayloads.getAndSet(Map.of());
        List<String> restoredIps = new ArrayList<>();
        List<Map<String, Object>> restoreErrors = new ArrayList<>();
        Map<String, Map<String, Object>> failedBaseline = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : baseline.entrySet()) {
            try {
                sendWriteCommand(entry.getKey(), entry.getValue());
                restoredIps.add(entry.getKey());
            } catch (ResponseStatusException error) {
                failedBaseline.put(entry.getKey(), entry.getValue());
                restoreErrors.add(Map.of(
                        "ip", entry.getKey(),
                        "error", error.getReason() != null ? error.getReason() : error.getMessage()
                ));
            }
        }
        if (!failedBaseline.isEmpty()) {
            restoreBaselinePayloads.set(Map.copyOf(failedBaseline));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restored", !restoredIps.isEmpty());
        result.put("restoreReason", reason);
        result.put("restoredAt", Instant.now().toString());
        result.put("restoredIps", restoredIps);
        result.put("restoreFailureCount", restoreErrors.size());
        if (!restoreErrors.isEmpty()) {
            result.put("restoreFailures", restoreErrors);
        }
        return result;
    }

    private Map<String, Object> restoreParamsFromPilot(Map<String, Object> pilot) {
        if (pilot.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        Boolean state = booleanValue(pilot.get("state"));
        if (state != null) {
            params.put("state", state);
            if (!state) {
                return params;
            }
        }

        Integer dimming = clampedIntegerValue(pilot.get("dimming"), 1, 100);
        if (dimming != null) {
            params.put("dimming", dimming);
        }

        Integer temp = clampedIntegerValue(pilot.get("temp"), 2200, 6500);
        if (temp != null) {
            params.put("temp", temp);
        } else {
            boolean hasRgb = false;
            for (String channel : List.of("r", "g", "b", "c", "w")) {
                Integer channelValue = clampedIntegerValue(pilot.get(channel), 0, 255);
                if (channelValue != null) {
                    params.put(channel, channelValue);
                    hasRgb = true;
                }
            }

            Integer sceneId = clampedIntegerValue(pilot.get("sceneId"), 1, 32);
            if (!hasRgb && sceneId != null) {
                params.put("sceneId", sceneId);
                Integer speed = clampedIntegerValue(pilot.get("speed"), 10, 200);
                if (speed != null) {
                    params.put("speed", speed);
                }
            }
        }

        if (!params.containsKey("state") && params.size() > 0) {
            params.put("state", true);
        }
        return params;
    }

    private Map<String, Object> sendReadCommand(String ip, Map<String, Object> payload) {
        try {
            return sendUdpCommand(ip, payload, commandTimeoutMs);
        } catch (RuntimeException error) {
            return Map.of("error", error.getMessage());
        }
    }

    private Map<String, Object> sendWriteCommand(String ip, Map<String, Object> payload) {
        return sendUdpWriteCommand(ip, payload);
    }

    private Map<String, Object> sendUdpWriteCommand(String ip, Map<String, Object> payload) {
        IOException lastError = null;
        for (int attempt = 1; attempt <= WRITE_RETRY_COUNT; attempt += 1) {
            try (DatagramSocket socket = new DatagramSocket()) {
                byte[] data = objectMapper.writeValueAsBytes(payload);
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ip), WIZ_PORT);
                socket.send(packet);
                return Map.of("sourceIp", ip, "sent", true, "attempt", attempt);
            } catch (IOException error) {
                lastError = error;
                if (attempt < WRITE_RETRY_COUNT) {
                    sleepInterruptibly(WRITE_RETRY_DELAY_MS);
                }
            }
        }
        throw new ResponseStatusException(SERVICE_UNAVAILABLE, "WiZ UDP write failed: " + ip, lastError);
    }

    private Map<String, Object> sendUdpCommand(String ip, Map<String, Object> payload, int timeoutMs) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            byte[] data = objectMapper.writeValueAsBytes(payload);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ip), WIZ_PORT);
            socket.send(packet);

            byte[] buffer = new byte[8192];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            String responseText = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
            Map<String, Object> parsed = objectMapper.readValue(responseText, MAP_TYPE);
            parsed.put("sourceIp", response.getAddress().getHostAddress());
            return parsed;
        } catch (SocketTimeoutException error) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "WiZ bulb timed out: " + ip, error);
        } catch (IOException error) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "WiZ UDP command failed: " + ip, error);
        }
    }

    private Map<String, Object> extractLightingSpec(Map<String, Object> payload) {
        Map<String, Object> direct = mapValue(payload.get("lighting_spec"));
        if (!direct.isEmpty()) {
            return direct;
        }
        Map<String, Object> camel = mapValue(payload.get("lightingSpec"));
        if (!camel.isEmpty()) {
            return camel;
        }
        Map<String, Object> interventionResult = mapValue(payload.get("interventionResult"));
        Map<String, Object> nested = mapValue(interventionResult.get("lighting_spec"));
        if (!nested.isEmpty()) {
            return nested;
        }
        throw new ResponseStatusException(BAD_REQUEST, "lighting_spec or interventionResult.lighting_spec is required");
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = mapValue(item);
            if (!map.isEmpty()) {
                mapped.add(map);
            }
        }
        return mapped;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized;
    }

    private int[] rgbFromHex(String rawHex) {
        String hex = Objects.toString(rawHex, "").trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid hex color: " + rawHex);
        }
        try {
            return new int[] {
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16),
            };
        } catch (NumberFormatException error) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid hex color: " + rawHex, error);
        }
    }

    private int boundedInt(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                parsed = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private Integer clampedIntegerValue(Object value, int min, int max) {
        Integer parsed = integerValue(value);
        if (parsed == null) {
            return null;
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return List.copyOf(values);
    }

    private void sleepInterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "WiZ lighting integration is disabled");
        }
    }

    @PreDestroy
    public void shutdown() {
        stopActiveJob();
        executor.shutdownNow();
    }
}
