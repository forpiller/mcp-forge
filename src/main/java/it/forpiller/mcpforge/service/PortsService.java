package it.forpiller.mcpforge.service;

import it.forpiller.mcpforge.history.SourceRegistrationHistory;
import it.forpiller.mcpforge.repository.SourceRegistrationHistoryRepository;
import it.forpiller.mcpforge.web.dto.JdkInfo;
import it.forpiller.mcpforge.web.dto.PortInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PortsService {

    static final String SELF_NAME = "mcp-forge";

    private static final Logger log = LoggerFactory.getLogger(PortsService.class);

    private final SourceRegistrationHistoryRepository repository;
    private final int selfPort;
    private final String selfWorkingDir;

    public PortsService(SourceRegistrationHistoryRepository repository, int selfPort) {
        this.repository = repository;
        this.selfPort = selfPort;
        this.selfWorkingDir = System.getProperty("user.dir", "");
    }

    public List<PortInfo> listAll() {
        List<PortInfo> list = new ArrayList<>();
        list.add(selfInfo());
        repository.findAllByOrderByLastUsedAtDesc().stream()
                .map(this::toPortInfo)
                .forEach(list::add);
        list = deduplicateByContainer(list);
        list = deduplicateByName(list);
        list.sort(java.util.Comparator.comparingInt(p -> p.dockerManaged() ? 1 : 0));
        return list;
    }

    private List<it.forpiller.mcpforge.history.SourceRegistrationHistory> deduplicateDbEntries(
            List<it.forpiller.mcpforge.history.SourceRegistrationHistory> entries) {
        java.util.Map<String, Boolean> seenByUrl = new java.util.LinkedHashMap<>();
        java.util.Map<String, Boolean> seenByDir = new java.util.LinkedHashMap<>();
        List<it.forpiller.mcpforge.history.SourceRegistrationHistory> result = new ArrayList<>();
        for (it.forpiller.mcpforge.history.SourceRegistrationHistory entry : entries) {
            String urlKey = normalizeUrl(entry.getBaseUrl());
            String dirKey = normalizeDir(entry.getWorkingDir());
            boolean dupUrl = !urlKey.isEmpty() && seenByUrl.containsKey(urlKey);
            boolean dupDir = !dirKey.isEmpty() && seenByDir.containsKey(dirKey);
            if (!dupUrl && !dupDir) {
                result.add(entry);
                if (!urlKey.isEmpty()) seenByUrl.put(urlKey, true);
                if (!dirKey.isEmpty()) seenByDir.put(dirKey, true);
            } else {
                log.debug("DB entry '{}' deduplicata: stesso {} di una entry più recente",
                        entry.getName(), dupUrl ? "baseUrl" : "workingDir");
            }
        }
        return result;
    }

    private static String normalizeUrl(String url) {
        if (isBlank(url)) return "";
        return url.trim().toLowerCase().replaceAll("/+$", "");
    }

    private static String normalizeDir(String dir) {
        if (isBlank(dir)) return "";
        return dir.trim().replaceAll("/+$", "");
    }

    private List<PortInfo> deduplicateByPort(List<PortInfo> list) {
        // Per servizi DOWN (no PID, no Docker): se due voci puntano alla stessa porta, tieni la più recente
        java.util.Map<Integer, PortInfo> byPort = new java.util.LinkedHashMap<>();
        List<PortInfo> others = new ArrayList<>();
        for (PortInfo p : list) {
            if (!p.up() && p.pid() == null && !p.dockerManaged() && p.port() > 0) {
                byPort.merge(p.port(), p, (ex, nx) -> ex); // ex = più recente (lista già in DESC)
            } else {
                others.add(p);
            }
        }
        others.addAll(byPort.values());
        return others;
    }

    private List<PortInfo> deduplicateByName(List<PortInfo> list) {
        java.util.Map<String, PortInfo> byName = new java.util.LinkedHashMap<>();
        for (PortInfo p : list) {
            byName.merge(p.sourceName(), p, (ex, nx) -> ex.up() ? ex : nx.up() ? nx : ex);
        }
        return new ArrayList<>(byName.values());
    }

    private List<PortInfo> deduplicateByContainer(List<PortInfo> list) {
        java.util.Map<String, PortInfo> byContainer = new java.util.LinkedHashMap<>();
        java.util.Map<Long, PortInfo> byPid = new java.util.LinkedHashMap<>();
        List<PortInfo> noPid = new ArrayList<>();

        for (PortInfo p : list) {
            if (p.dockerContainerName() != null) {
                byContainer.merge(p.dockerContainerName(), p,
                        (ex, nx) -> nx.sourceName().length() > ex.sourceName().length() ? nx : ex);
            } else if (p.pid() != null) {
                byPid.merge(p.pid(), p, (ex, nx) -> resolvePidConflict(ex, nx));
            } else {
                noPid.add(p);
            }
        }

        List<PortInfo> result = new ArrayList<>(byPid.values());
        result.addAll(noPid);
        result.addAll(byContainer.values());
        return result;
    }

    private PortInfo resolvePidConflict(PortInfo existing, PortInfo candidate) {
        boolean exMatch = dirMatch(existing.savedWorkingDir(), existing.detectedWorkingDir());
        boolean nxMatch = dirMatch(candidate.savedWorkingDir(), candidate.detectedWorkingDir());
        if (nxMatch && !exMatch) return candidate;
        if (exMatch && !nxMatch) return existing;
        // Parità: vince il più recente (la lista è già in ordine lastUsedAt DESC, existing = primo = più recente)
        return existing;
    }

    private boolean dirMatch(String savedDir, String detectedDir) {
        return !isBlank(savedDir) && detectedDir != null && detectedDir.startsWith(savedDir);
    }

    public List<JdkInfo> listInstalledJdks() {
        List<JdkInfo> result = new ArrayList<>();

        if (isMac()) {
            result.addAll(detectJdksMac());
        }

        if (!isMac() || result.isEmpty()) {
            result.addAll(detectJdksLinux());
        }

        if (isWindows()) {
            result.addAll(detectJdksWindows());
        }

        if (result.isEmpty()) {
            result.add(new JdkInfo("21", "21", ""));
            result.add(new JdkInfo("17", "17", ""));
        }
        return result;
    }

    private List<JdkInfo> detectJdksMac() {
        List<JdkInfo> result = new ArrayList<>();
        try {
            String out = exec("/usr/libexec/java_home", "-V");
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile(
                    "^\\s+(\\d+\\.\\S+)\\s+\\([^)]+\\)\\s+\"[^\"]+\"\\s+-\\s+\"([^\"]+)\"\\s+(\\S+)$");
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (String l : out.split("\n")) {
                java.util.regex.Matcher m = pat.matcher(l);
                if (!m.matches()) continue;
                String full = m.group(1), name = m.group(2), path = m.group(3);
                String major = full.split("\\.")[0];
                if (seen.add(major))
                    result.add(new JdkInfo(major, major + "  —  " + name + "  (" + full + ")", path));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private List<JdkInfo> detectJdksLinux() {
        List<JdkInfo> result = new ArrayList<>();
        try {
            String out = shellExec("update-java-alternatives -l 2>/dev/null");
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (String l : out.split("\n")) {
                l = l.trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(l);
                if (m.find() && seen.add(m.group(1))) {
                    String[] parts = l.split("\\s+");
                    String path = parts.length > 2 ? parts[2] : "";
                    result.add(new JdkInfo(m.group(1), m.group(1) + "  —  " + (parts.length > 0 ? parts[0] : l), path));
                }
            }
        } catch (Exception ignored) {}

        if (result.isEmpty()) {
            try {
                String dirs = shellExec("find /usr/lib/jvm -maxdepth 1 -type d -name 'java-*' 2>/dev/null");
                java.util.Set<String> seen = new java.util.LinkedHashSet<>();
                for (String d : dirs.split("\n")) {
                    d = d.trim();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("java-(\\d+)").matcher(d);
                    if (m.find() && seen.add(m.group(1)))
                        result.add(new JdkInfo(m.group(1), m.group(1) + "  —  " + java.nio.file.Path.of(d).getFileName(), d));
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private List<JdkInfo> detectJdksWindows() {
        List<JdkInfo> result = new ArrayList<>();
        String[] roots = {
                "C:\\Program Files\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft",
                System.getenv("JAVA_HOME") != null ? System.getenv("JAVA_HOME") + "\\.." : ""
        };
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String root : roots) {
            if (root.isBlank()) continue;
            try {
                java.io.File dir = new java.io.File(root);
                if (!dir.exists()) continue;
                for (java.io.File sub : java.util.Objects.requireNonNull(dir.listFiles())) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(sub.getName());
                    if (m.find() && seen.add(m.group(1)))
                        result.add(new JdkInfo(m.group(1), m.group(1) + "  —  " + sub.getName(), sub.getAbsolutePath()));
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    public List<String[]> scanLocalProcesses() {
        List<String[]> found = new ArrayList<>();
        try {
            java.util.Map<Integer, Long> portToPid = listenPortToPid();
            java.util.Set<Integer> trackedPorts = trackedPorts();

            for (java.util.Map.Entry<Integer, Long> entry : portToPid.entrySet()) {
                int port = entry.getKey();
                long pid = entry.getValue();

                if (port < 1024 || trackedPorts.contains(port)) continue;

                String args = getProcessArgs(pid);
                if (args == null || !args.toLowerCase().contains("java")) continue;

                String workDir = getWorkingDir(pid);
                if (isDockerProcess(pid, workDir)) continue;

                String name = deriveNameFromDir(workDir);
                if (repository.existsByName(name)) continue;

                String baseUrl = "http://localhost:" + port;
                String cmd = deriveStartCommand(args, workDir);
                found.add(new String[]{name, baseUrl, cmd, workDir != null ? workDir : ""});
            }
        } catch (Exception e) {
            log.warn("Error scanning local processes: {}", e.getMessage());
        }
        return found;
    }

    private java.util.Map<Integer, Long> listenPortToPid() {
        java.util.Map<Integer, Long> result = new java.util.LinkedHashMap<>();
        try {
            if (isWindows()) {
                result.putAll(listenPortToPidWindows());
            } else {
                result.putAll(listenPortToPidUnix());
            }
        } catch (Exception e) {
            log.debug("listenPortToPid error: {}", e.getMessage());
        }
        return result;
    }

    private java.util.Map<Integer, Long> listenPortToPidWindows() throws Exception {
        java.util.Map<Integer, Long> result = new java.util.LinkedHashMap<>();
        String out = exec("netstat", "-ano", "-p", "TCP");
        for (String l : out.split("\n")) {
            if (!l.contains("LISTENING")) continue;
            String[] p = l.trim().split("\\s+");
            if (p.length < 5) continue;
            int port = parsePort(p[1]);
            try {
                long pid = Long.parseLong(p[4]);
                if (port > 0) result.put(port, pid);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private java.util.Map<Integer, Long> listenPortToPidUnix() throws Exception {
        java.util.Map<Integer, Long> result = new java.util.LinkedHashMap<>();
        String out = "";
        try {
            out = exec("/bin/sh", "-c",
                    "lsof -i -sTCP:LISTEN -nP 2>/dev/null | awk 'NR>1 {print $2, $9}'");
        } catch (Exception ignored) {}
        if (out.isBlank()) {
            out = shellExec("ss -tlnp 2>/dev/null | awk 'NR>1 {print $4, $6}'");
        }
        for (String l : out.split("\n")) {
            l = l.trim();
            if (l.isBlank()) continue;
            String[] p = l.split("\\s+", 2);
            if (p.length < 2) continue;
            long pid;
            try { pid = Long.parseLong(p[0]); } catch (NumberFormatException e) { continue; }
            int port = parsePort(p[1]);
            if (port > 0) result.putIfAbsent(port, pid);
        }
        return result;
    }

    private String getProcessArgs(long pid) {
        try {
            if (isWindows()) {
                return exec("wmic", "process", "where", "ProcessId=" + pid, "get", "CommandLine", "/value");
            } else {
                return exec("ps", "-p", String.valueOf(pid), "-o", "args=");
            }
        } catch (Exception e) { return null; }
    }

    private java.util.Set<Integer> trackedPorts() {
        java.util.Set<Integer> ports = new java.util.HashSet<>();
        ports.add(selfPort);
        repository.findAllByOrderByLastUsedAtDesc()
                .forEach(e -> {
                    int p = extractPort(e.getBaseUrl());
                    if (p > 0) ports.add(p);
                });
        return ports;
    }

    private int parsePort(String hostPort) {
        int idx = hostPort.lastIndexOf(':');
        if (idx < 0) return -1;
        try { return Integer.parseInt(hostPort.substring(idx + 1)); } catch (NumberFormatException e) { return -1; }
    }

    private String deriveNameFromDir(String workDir) {
        if (isBlank(workDir)) return "service";
        java.nio.file.Path p = java.nio.file.Path.of(workDir);
        String name = p.getFileName() != null ? p.getFileName().toString() : workDir;
        if ("app".equals(name) && p.getParent() != null && p.getParent().getFileName() != null) {
            name = p.getParent().getFileName().toString();
        }
        return name.toLowerCase().replace(" ", "-").replace("_", "-");
    }

    private String deriveStartCommand(String args, String workDir) {
        if (args.contains("spring-boot:run")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(mvn[^&\n]+spring-boot:run)").matcher(args);
            if (m.find()) return m.group(1).trim();
            return "mvn spring-boot:run";
        }
        if (args.contains("-jar")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("-jar\\s+(\\S+\\.jar)").matcher(args);
            if (m.find()) return "java -jar " + m.group(1);
        }
        return "";
    }

    public PortInfo getByName(String name) {
        if (SELF_NAME.equals(name)) return selfInfo();
        return repository.findByName(name).map(this::toPortInfo).orElse(null);
    }

    private PortInfo selfInfo() {
        Long pid = getPid(selfPort);
        boolean up = pid != null;
        String detectedDir = (pid != null) ? getWorkingDir(pid) : null;
        String branch = getGitBranch(selfWorkingDir);
        return new PortInfo(SELF_NAME, "http://localhost:" + selfPort, selfPort, up, pid,
                detectedDir, "mvn spring-boot:run", selfWorkingDir, "21", null, false, null, branch);
    }

    public boolean kill(String sourceName) {
        if (SELF_NAME.equals(sourceName)) {
            log.warn("Non è possibile terminare mcp-forge tramite l'interfaccia web");
            return false;
        }
        PortInfo info = getByName(sourceName);
        if (info == null || !info.up() || info.pid() == null) return false;
        try {
            exec("kill", "-9", String.valueOf(info.pid()));
            log.info("Processo {} (porta {}) terminato", info.pid(), info.port());
            return true;
        } catch (Exception e) {
            log.warn("Errore kill porta {}: {}", info.port(), e.getMessage());
            return false;
        }
    }

    public boolean start(String sourceName) {
        return startWithFallbackDir(sourceName, null);
    }

    private boolean startWithFallbackDir(String sourceName, String fallbackDir) {
        String startCmd;
        String workDir;
        String jdkVer;
        String envVarsStr;
        if (SELF_NAME.equals(sourceName)) {
            startCmd = "mvn spring-boot:run";
            workDir = selfWorkingDir;
            jdkVer = "21";
            envVarsStr = null;
        } else {
            SourceRegistrationHistory entry = repository.findByName(sourceName).orElse(null);
            startCmd = (entry != null) ? entry.getStartCommand() : null;
            String savedDir = (entry != null) ? entry.getWorkingDir() : null;
            workDir = !isBlank(savedDir) ? savedDir : fallbackDir;
            jdkVer = (entry != null && !isBlank(entry.getJdkVersion())) ? entry.getJdkVersion() : "21";
            envVarsStr = (entry != null) ? entry.getEnvVars() : null;
            if (isBlank(startCmd)) {
                log.warn("Comando di avvio non configurato per '{}'", sourceName);
                return false;
            }
            if (isBlank(workDir)) {
                log.warn("Directory di lavoro non configurata e non rilevata per '{}'", sourceName);
                return false;
            }
        }
        try {
            String javaHome = detectJavaHome(jdkVer);
            String envExports = buildEnvExports(envVarsStr);
            String logFile = "/tmp/mcpforge-start-" + sourceName.replaceAll("[^a-zA-Z0-9]", "_") + ".log";
            String extraPath = "/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin";
            // Truncate prima di avviare: evita che il BUILD FAILURE del vecchio run rimanga visibile
            try { new java.io.FileOutputStream(logFile).close(); } catch (Exception ignored) {}
            String[] cmd = {"/bin/sh", "-c",
                    "cd " + shellEscape(workDir) +
                    " && export JAVA_HOME=" + javaHome +
                    " && export PATH=" + extraPath + ":$JAVA_HOME/bin:$PATH" +
                    envExports +
                    " && nohup " + startCmd +
                    " > " + logFile + " 2>&1 &"
            };
            new ProcessBuilder(cmd).inheritIO().start();
            log.info("Avvio '{}' in background da '{}' (JDK {}): {}", sourceName, workDir, jdkVer, startCmd);
            return true;
        } catch (Exception e) {
            log.warn("Errore avvio '{}': {}", sourceName, e.getMessage());
            return false;
        }
    }

    private String buildEnvExports(String envVarsStr) {
        if (isBlank(envVarsStr)) return "";
        StringBuilder sb = new StringBuilder();
        for (String pair : envVarsStr.split(";")) {
            pair = pair.trim();
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                String envKey = key.replace('.', '_').replace('-', '_').toUpperCase();
                sb.append(" && export ").append(envKey).append("=").append(shellEscape(value));
            }
        }
        return sb.toString();
    }

    public String missingConfig(String sourceName) {
        if (SELF_NAME.equals(sourceName)) return null;
        SourceRegistrationHistory entry = repository.findByName(sourceName).orElse(null);
        if (entry == null) return "Fonte non trovata";
        if (isBlank(entry.getStartCommand())) return "Comando di avvio non configurato";
        return null;
    }

    public boolean restart(String sourceName) {
        if (SELF_NAME.equals(sourceName)) {
            return restartSelf();
        }
        PortInfo info = getByName(sourceName);
        String detectedDir = (info != null) ? info.detectedWorkingDir() : null;

        boolean killed = kill(sourceName);
        if (!killed) {
            log.info("Restart '{}': nessun processo da terminare, avvio diretto", sourceName);
        }
        return startWithFallbackDir(sourceName, detectedDir);
    }

    private boolean restartSelf() {
        Long pid = getPid(selfPort);
        if (pid == null) {
            log.info("mcp-forge non in esecuzione, avvio diretto");
            return start(SELF_NAME);
        }
        try {
            String javaHome = detectJavaHome("21");
            String script = "sleep 2 && kill -9 " + pid +
                    " && cd " + shellEscape(selfWorkingDir) +
                    " && export JAVA_HOME=" + javaHome +
                    " && export PATH=$JAVA_HOME/bin:$PATH" +
                    " && nohup mvn spring-boot:run > /tmp/mcpforge-start-mcp_forge.log 2>&1 &";
            new ProcessBuilder("/bin/sh", "-c", script).start();
            log.info("Restart mcp-forge: script di riavvio avviato (kill PID {} tra 2s)", pid);
            return true;
        } catch (Exception e) {
            log.warn("Errore restart mcp-forge: {}", e.getMessage());
            return false;
        }
    }

    public String logFilePath(String sourceName) {
        if (SELF_NAME.equals(sourceName)) return "/tmp/mcp-forge.log";
        PortInfo info = getByName(sourceName);
        if (info != null && info.dockerManaged()) {
            String container = info.dockerContainerName();
            return "docker logs: " + (container != null ? container : "(container non trovato)");
        }
        return "/tmp/mcpforge-start-" + sourceName.replaceAll("[^a-zA-Z0-9]", "_") + ".log";
    }

    public List<String> tailLog(String sourceName, int lines) {
        PortInfo info = getByName(sourceName);
        if (info != null && info.dockerManaged()) {
            return tailDockerLog(info.dockerContainerName(), lines);
        }
        Path path = Path.of(SELF_NAME.equals(sourceName)
                ? "/tmp/mcp-forge.log"
                : "/tmp/mcpforge-start-" + sourceName.replaceAll("[^a-zA-Z0-9]", "_") + ".log");
        if (!Files.exists(path)) return Collections.emptyList();
        try {
            List<String> all = Files.readAllLines(path);
            // Trova l'inizio dell'ultimo run: ultima riga [INFO] BUILD FAILURE seguita da righe Spring Boot
            int lastBuildFailure = -1;
            for (int i = all.size() - 1; i >= 0; i--) {
                if (all.get(i).contains("[INFO] BUILD FAILURE")) {
                    lastBuildFailure = i;
                    break;
                }
            }
            // Cerca la prima riga Spring Boot (timestamp ISO) dopo l'ultimo BUILD FAILURE
            int runStart = 0;
            if (lastBuildFailure >= 0) {
                for (int i = lastBuildFailure + 1; i < all.size(); i++) {
                    if (all.get(i).matches("^\\d{4}-\\d{2}-\\d{2}T.*")) {
                        runStart = i;
                        break;
                    }
                }
            }
            List<String> currentRun = all.subList(runStart, all.size());
            int from = Math.max(0, currentRun.size() - lines);
            return currentRun.subList(from, currentRun.size());
        } catch (Exception e) {
            return List.of("Errore lettura log: " + e.getMessage());
        }
    }

    public int cleanupDuplicates() {
        List<SourceRegistrationHistory> all = repository.findAllByOrderByLastUsedAtDesc();
        java.util.Map<String, List<SourceRegistrationHistory>> byContainer = new java.util.LinkedHashMap<>();
        for (SourceRegistrationHistory entry : all) {
            String containerName = getDockerContainerByName(entry.getName());
            if (containerName != null) {
                byContainer.computeIfAbsent(containerName, k -> new ArrayList<>()).add(entry);
            }
        }
        int removed = 0;
        for (List<SourceRegistrationHistory> entries : byContainer.values()) {
            if (entries.size() <= 1) continue;
            entries.sort(java.util.Comparator.comparingInt(h -> -h.getName().length()));
            for (int i = 1; i < entries.size(); i++) {
                repository.delete(entries.get(i));
                removed++;
                log.info("Rimossa voce duplicata '{}' (stessa container di '{}')",
                        entries.get(i).getName(), entries.get(0).getName());
            }
        }
        return removed;
    }

    public List<String[]> listDockerContainers() {
        try {
            String out = dockerShell("docker ps --format '{{.Names}}\t{{.Ports}}' 2>/dev/null");
            if (out.isBlank()) return Collections.emptyList();
            List<String[]> result = new ArrayList<>();
            for (String line : out.split("\n")) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\t", 2);
                String rawName = parts[0].trim();
                String serviceName = rawName.split("\\.")[0];
                serviceName = serviceName.replace("_", "-");
                String portsCol = parts.length > 1 ? parts[1].trim() : "";
                String baseUrl = extractBaseUrlFromPorts(portsCol);
                result.add(new String[]{serviceName, baseUrl});
            }
            return result;
        } catch (Exception e) {
            log.warn("Errore scoperta container Docker: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String extractBaseUrlFromPorts(String portsCol) {
        if (!portsCol.isBlank()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(":(\\d+)->").matcher(portsCol);
            if (m.find()) return "http://localhost:" + m.group(1);
        }
        return "http://localhost";
    }

    private List<String> tailDockerLog(String containerName, int lines) {
        if (containerName == null || containerName.isBlank()) return Collections.emptyList();
        try {
            String out = dockerShell("docker logs --tail " + lines + " " + containerName + " 2>&1");
            if (out.isBlank()) return Collections.emptyList();
            return List.of(out.split("\n", -1));
        } catch (Exception e) {
            return List.of("Errore docker logs: " + e.getMessage());
        }
    }

    private String dockerShell(String command) throws Exception {
        String dockerPath = "/usr/local/bin:/usr/bin:/opt/homebrew/bin";
        return exec("/bin/sh", "-c", "export PATH=" + dockerPath + ":$PATH && " + command);
    }

    private String getDockerContainerName(int port) {
        try {
            String out = dockerShell(
                    "docker ps --format '{{.Names}}\t{{.Ports}}' 2>/dev/null | grep ':" + port + "->'");
            if (!out.isBlank()) {
                String name = out.trim().split("\n")[0].split("\t")[0].trim();
                if (!name.isBlank()) return name;
            }
        } catch (Exception ignored) {}
        return null;
    }

    String getDockerContainerByName(String sourceName) {
        try {
            String flexPattern = sourceName.replaceAll("[-_]", "[-_]");
            String out = dockerShell(
                    "docker ps --format '{{.Names}}' 2>/dev/null | grep -iE '" + flexPattern + "'");
            if (out.isBlank()) return null;
            return out.trim().split("\n")[0].trim();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDockerProcess(Long pid, String detectedDir) {
        if (detectedDir != null && detectedDir.contains("com.docker.docker")) return true;
        try {
            String args = exec("ps", "-p", String.valueOf(pid), "-o", "args=");
            String lower = args.toLowerCase();
            return lower.contains("com.docker") || lower.contains("docker-proxy")
                    || lower.contains("vpnkit") || lower.contains("docker desktop");
        } catch (Exception e) {
            return false;
        }
    }

    private PortInfo toPortInfo(SourceRegistrationHistory entry) {
        int port = extractPort(entry.getBaseUrl());
        Long pid = (port > 0) ? getPid(port) : null;
        boolean up = pid != null;
        String detectedDir = (pid != null) ? getWorkingDir(pid) : null;
        boolean docker = (pid != null) && isDockerProcess(pid, detectedDir);
        String containerName = null;
        if (docker) {
            containerName = getDockerContainerName(port);
            if (containerName == null) containerName = getDockerContainerByName(entry.getName());
        }
        if (!docker) {
            containerName = getDockerContainerByName(entry.getName());
            if (containerName != null) {
                docker = true;
                up = true;
            }
        }
        String workDirForBranch = !isBlank(entry.getWorkingDir()) ? entry.getWorkingDir() : detectedDir;
        String branch = docker ? null : getGitBranch(workDirForBranch);
        return new PortInfo(entry.getName(), entry.getBaseUrl(), port, up, pid,
                detectedDir, entry.getStartCommand(), entry.getWorkingDir(),
                entry.getJdkVersion(), entry.getEnvVars(), docker, containerName, branch);
    }

    private int extractPort(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            int p = uri.getPort();
            if (p > 0) return p;
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        } catch (Exception e) {
            return -1;
        }
    }

    private Long getPid(int port) {
        try {
            String out = exec("lsof", "-i", ":" + port, "-sTCP:LISTEN", "-t");
            String trimmed = out.trim();
            if (trimmed.isBlank()) return null;
            return Long.parseLong(trimmed.split("\\s+")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private String getWorkingDir(long pid) {
        try {
            String out = exec("/bin/sh", "-c",
                    "lsof -p " + pid + " -a -d cwd -Fn 2>/dev/null | grep '^n' | sed 's/^n//'");
            return out.trim().isBlank() ? null : out.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String getGitBranch(String workingDir) {
        if (isBlank(workingDir)) return null;
        try {
            String branch = exec("/bin/sh", "-c",
                    "git -C " + shellEscape(workingDir) + " branch --show-current 2>/dev/null");
            String trimmed = branch.trim();
            return trimmed.isBlank() ? null : trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    private String detectJavaHome(String version) {
        try {
            String out = exec("/usr/libexec/java_home", "-v", version);
            return out.trim();
        } catch (Exception e) {
            return System.getProperty("java.home", "/usr/lib/jvm/java-" + version);
        }
    }

    public boolean openTerminal(String sourceName) {
        String dir = resolveWorkingDir(sourceName);
        try {
            if (isMac()) {
                new ProcessBuilder("open", "-a", "Terminal", dir.isBlank() ? System.getProperty("user.home") : dir)
                        .start();
            } else if (isWindows()) {
                new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k",
                        "cd /d \"" + (dir.isBlank() ? "%" + "USERPROFILE%" : dir) + "\"")
                        .start();
            } else {
                String[] cmd = dir.isBlank()
                        ? new String[]{"/bin/sh", "-c", "x-terminal-emulator &"}
                        : new String[]{"/bin/sh", "-c",
                                "x-terminal-emulator --working-directory=" + shellEscape(dir)
                                + " 2>/dev/null || gnome-terminal --working-directory=" + shellEscape(dir)
                                + " 2>/dev/null || xterm -e 'cd " + shellEscape(dir) + " && bash' &"};
                new ProcessBuilder(cmd).start();
            }
            return true;
        } catch (Exception e) {
            log.warn("openTerminal error for {}: {}", sourceName, e.getMessage());
            return false;
        }
    }

    private String resolveWorkingDir(String sourceName) {
        return repository.findByName(sourceName)
                .map(e -> !isBlank(e.getWorkingDir()) ? e.getWorkingDir() : "")
                .orElse("");
    }

    public String pickDirectory() {
        try {
            String raw;
            if (isMac()) {
                raw = exec("/bin/sh", "-c",
                        "osascript -e 'POSIX path of (choose folder)' 2>/dev/null");
            } else if (isWindows()) {
                raw = exec("powershell", "-NoProfile", "-Command",
                        "Add-Type -AssemblyName System.Windows.Forms;" +
                        "$d=New-Object System.Windows.Forms.FolderBrowserDialog;" +
                        "if($d.ShowDialog()-eq'OK'){$d.SelectedPath}");
            } else {
                raw = exec("/bin/sh", "-c",
                        "zenity --file-selection --directory 2>/dev/null " +
                        "|| kdialog --getexistingdirectory \"$HOME\" 2>/dev/null");
            }
            String path = raw == null ? "" : raw.trim().replaceAll("[\r\n]+$", "");
            return path.isEmpty() ? null : path;
        } catch (Exception e) {
            log.debug("pickDirectory error: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private String shellExec(String command) throws Exception {
        if (isWindows()) return exec("cmd.exe", "/c", command);
        return exec("/bin/sh", "-c", command);
    }

    private String exec(String... command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            return r.lines().collect(Collectors.joining("\n"));
        }
    }

    private static String shellEscape(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
