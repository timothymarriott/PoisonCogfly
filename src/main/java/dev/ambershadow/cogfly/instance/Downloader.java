package dev.ambershadow.cogfly.instance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.util.FileUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Front end for the bundled CogflyDownloader executable. Downloads talk to it over stdin/stdout using one JSON
 * object per line (see tools/DepotDownloader/Bridge.cs).
 */
public class Downloader {

    public interface Listener {
        void onQr(String url);
        /** kind is password, guard_device or guard_email; answer with {@link Job#respond}. */
        void onPrompt(String kind, JsonObject data);
        void onConfirm(String message);
        void onProgress(long downloaded, long total);
        void onLoggedIn(String username);
    }

    public static class Job {
        private final Process process;
        private final BufferedWriter stdin;
        public final CompletableFuture<Void> future = new CompletableFuture<>();

        private Job(Process process) {
            this.process = process;
            this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        public synchronized void respond(String value) {
            JsonObject answer = new JsonObject();
            answer.addProperty("value", value);
            try {
                stdin.write(answer.toString());
                stdin.newLine();
                stdin.flush();
            } catch (IOException e) {
                Cogfly.logger.warn("Failed to answer downloader prompt", e);
            }
        }

        public void cancel() {
            process.destroyForcibly();
        }
    }

    private static final String APP_ID = "1030300";

    private static String exeName() {
        return Cogfly.isWindows() ? "CogflyDownloader.exe" : "CogflyDownloader";
    }

    private static Path configDir() {
        return Cogfly.localDataPath.resolve("downloader", "config");
    }

    /** Copies the bundled executable out of the jar (once per build) and returns its path. */
    public static synchronized Path executable() throws IOException {
        URL resource = Cogfly.class.getResource("/downloader/" + exeName());
        if (resource == null)
            throw new IOException("This build of Cogfly doesn't include the downloader (the .NET SDK was missing when it was built).");
        Path target = Cogfly.localDataPath.resolve("downloader", exeName());
        URLConnection connection = resource.openConnection();
        if (!Files.exists(target) || Files.size(target) != connection.getContentLengthLong()) {
            Files.createDirectories(target.getParent());
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Cogfly.setExecutable(target);
        }
        return target;
    }

    private static String depot(String platform) {
        return switch (platform) {
            case "Windows" -> "1030301";
            case "MacOS" -> "1030302";
            default -> "1030303";
        };
    }

    /** branch is "Public" or "PublicBeta", matching versions.txt. */
    public static Job download(String platform, String branch, long manifest, Path dir, Listener listener) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(executable().toString(), "download",
                "--app", APP_ID,
                "--depot", depot(platform),
                "--manifest", Long.toUnsignedString(manifest),
                "--branch", branch.equals("PublicBeta") ? "public-beta" : "public",
                "--dir", dir.toString(),
                "--config", configDir().toString()));
        String username = Cogfly.settings.steamUsername;
        if (username != null && !username.isBlank())
            cmd.addAll(List.of("--username", username.trim()));

        Files.createDirectories(dir);
        Process process = new ProcessBuilder(cmd).start();
        Job job = new Job(process);
        Cogfly.logger.info("Started downloader for manifest {} ({})", manifest, platform);

        Thread.ofVirtual().start(() -> drain(process.getErrorStream(), line -> Cogfly.logger.debug("[downloader] {}", line)));
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handle(line, listener, job);
                }
            } catch (IOException e) {
                job.future.completeExceptionally(e);
            }
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {}
            job.future.completeExceptionally(new IOException("The downloader stopped unexpectedly."));
        });
        return job;
    }

    private static void handle(String line, Listener listener, Job job) {
        JsonObject event;
        try {
            event = JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
            Cogfly.logger.debug("[downloader] {}", line);
            return;
        }
        switch (event.get("event").getAsString()) {
            case "qr" -> listener.onQr(event.get("url").getAsString());
            case "prompt" -> listener.onPrompt(event.get("kind").getAsString(), event);
            case "confirm" -> listener.onConfirm(event.get("message").getAsString());
            case "progress" -> listener.onProgress(event.get("downloaded").getAsLong(), event.get("total").getAsLong());
            case "logged_in" -> {
                if (event.has("username") && !event.get("username").isJsonNull())
                    listener.onLoggedIn(event.get("username").getAsString());
            }
            case "done" -> job.future.complete(null);
            case "error" -> job.future.completeExceptionally(new IOException(event.get("message").getAsString()));
            default -> Cogfly.logger.debug("[downloader] {}", line);
        }
    }

    private static void drain(InputStream stream, java.util.function.Consumer<String> sink) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank())
                    sink.accept(line);
            }
        } catch (IOException ignored) {}
    }

    /** Reads the game version from a game folder. Returns null if it can't be determined. */
    public static String identify(Path gameDir) {
        try {
            Process process = new ProcessBuilder(executable().toString(), "identify", gameDir.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0 || out.isEmpty())
                return null;
            return out;
        } catch (IOException | InterruptedException e) {
            Cogfly.logger.warn("Failed to identify game version for {}", gameDir, e);
            return null;
        }
    }

    /** Forgets the stored Steam login. */
    public static void logout() throws IOException {
        List<String> cmd = new ArrayList<>(List.of(executable().toString(), "logout", "--config", configDir().toString()));
        try {
            Files.createDirectories(configDir());
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            process.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
