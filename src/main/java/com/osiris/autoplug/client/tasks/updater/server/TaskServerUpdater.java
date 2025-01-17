/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.osiris.autoplug.client.Server;
import com.osiris.autoplug.client.configs.UpdaterConfig;
import com.osiris.autoplug.client.managers.FileManager;
import com.osiris.autoplug.client.tasks.updater.TaskDownload;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.StringComparator;
import com.osiris.autoplug.core.json.JsonTools;
import com.osiris.autoplug.core.json.exceptions.HttpErrorException;
import com.osiris.autoplug.core.json.exceptions.WrongJsonTypeException;
import com.osiris.betterthread.BetterThread;
import com.osiris.betterthread.BetterThreadManager;
import com.osiris.dyml.exceptions.DYReaderException;
import com.osiris.dyml.exceptions.DYWriterException;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class TaskServerUpdater extends BetterThread {
    private final File downloadsDir = new File(GD.WORKING_DIR + "/autoplug/downloads");
    private UpdaterConfig updaterConfig;
    private String profile;
    private String serverSoftware;
    private String serverVersion;

    public TaskServerUpdater(String name, BetterThreadManager manager) {
        super(name, manager);
    }

    @Override
    public void runAtStart() throws Exception {
        super.runAtStart();
        if (Server.isRunning()) throw new Exception("Cannot perform update while server is running!");
        updaterConfig = new UpdaterConfig();
        if (!updaterConfig.server_updater.asBoolean()) {
            skip();
            return;
        }
        profile = updaterConfig.server_updater_profile.asString();
        serverSoftware = updaterConfig.server_software.asString();
        serverVersion = updaterConfig.server_version.asString();

        setStatus("Searching for updates...");

        if (updaterConfig.server_jenkins.asBoolean()) {
            doJenkinsUpdatingLogic();
        } else if (serverSoftware.equalsIgnoreCase("purpur")) {
            doPurpurUpdatingLogic();
        } else {
            doPaperUpdatingLogic();
        }
        finish();
    }

    private void doJenkinsUpdatingLogic() throws IOException, WrongJsonTypeException, HttpErrorException, InterruptedException, DYWriterException, DuplicateKeyException, DYReaderException, IllegalListException {
        String project_url = updaterConfig.server_jenkins_project_url.asString();
        String artifact_name = updaterConfig.server_jenkins_artifact_name.asString();
        double minimumSimilarity = Double.parseDouble("0." + updaterConfig.server_jenkins_artifact_name_similarity.asInt());
        int build_id = 0;
        if (updaterConfig.server_jenkins_build_id.asString() != null)
            build_id = updaterConfig.server_jenkins_build_id.asInt();

        JsonTools json_tools = new JsonTools();
        JsonObject json_project = json_tools.getJsonObject(project_url + "/api/json");
        JsonObject json_last_successful_build = json_project.get("lastSuccessfulBuild").getAsJsonObject();
        int latest_build_id = json_last_successful_build.get("number").getAsInt();
        if (latest_build_id <= build_id) {
            setStatus("Your server is on the latest version!");
            setSuccess(true);
            return;
        }
        String buildUrl = json_last_successful_build.get("url").getAsString() + "/api/json";
        JsonArray arrayArtifacts = json_tools.getJsonObject(buildUrl).getAsJsonArray("artifacts");
        String onlineArtifactFileName = null;
        String download_url = null;
        for (JsonElement e :
                arrayArtifacts) {
            if (e.getAsJsonObject().get("fileName").getAsString().equals(artifact_name)) {
                onlineArtifactFileName = e.getAsJsonObject().get("fileName").getAsString();
                download_url = project_url + "/" + latest_build_id + "/artifact/" + e.getAsJsonObject().get("relativePath").getAsString();
                break;
            }
        }

        if (download_url == null && updaterConfig.server_jenkins_artifact_name_similarity.asString() != null)
            for (JsonElement e :
                    arrayArtifacts) {
                if (StringComparator.similarity(e.getAsJsonObject().get("fileName").getAsString(), artifact_name) > minimumSimilarity) {
                    onlineArtifactFileName = e.getAsJsonObject().get("fileName").getAsString();
                    download_url = project_url + "/" + latest_build_id + "/artifact/" + e.getAsJsonObject().get("relativePath").getAsString();
                    break;
                }
            }

        if (download_url == null) {
            finish("Failed to find an equal or similar artifact-name '" + artifact_name + "' inside of '" + arrayArtifacts + "'!", false);
            return;
        }

        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + onlineArtifactFileName);
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), download_url, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded successfully.");
                        setSuccess(true);
                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");
            GD.SERVER_JAR = new FileManager().serverJar();
            System.out.println(GD.SERVER_JAR);

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + onlineArtifactFileName);
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), download_url, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        File final_dest = GD.SERVER_JAR;
                        if (final_dest == null)
                            final_dest = new File(GD.WORKING_DIR + "/" + onlineArtifactFileName);
                        if (final_dest.exists()) final_dest.delete();
                        final_dest.createNewFile();
                        FileUtils.copyFile(cache_dest, final_dest);
                        setStatus("Server update was installed successfully (" + build_id + " -> " + latest_build_id + ")!");
                        updaterConfig.server_jenkins_build_id.setValues("" + latest_build_id);
                        updaterConfig.save();
                        setSuccess(true);
                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        }
        return;
    }

    private void doPurpurUpdatingLogic() throws WrongJsonTypeException, IOException, HttpErrorException, InterruptedException, DYWriterException, DuplicateKeyException, DYReaderException, IllegalListException, NoSuchAlgorithmException {
        PurpurDownloadsAPI purpurDownloadsAPI = new PurpurDownloadsAPI();
        int buildId = updaterConfig.server_build_id.asInt();
        JsonObject latestBuild = purpurDownloadsAPI.getLatestBuild(serverSoftware.toLowerCase(Locale.ROOT), serverVersion);
        int latestBuildId = latestBuild.get("build").getAsInt();
        String buildHash = latestBuild.get("md5").getAsString();
        String url = purpurDownloadsAPI.getLatestDownloadUrl(serverSoftware.toLowerCase(Locale.ROOT), serverVersion);

        // Check if the latest build-id is bigger than our current one.
        if (latestBuildId <= buildId) {
            setStatus("Your server is on the latest version!");
            setSuccess(true);
            return;
        }

        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest, true);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithMD5(buildHash)) {
                            setStatus("Server update downloaded successfully.");
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken. Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + buildId + " -> " + latestBuildId + "), started download!");

            // Download the file
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest, true);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithMD5(buildHash)) {
                            File final_dest = GD.SERVER_JAR;
                            if (final_dest == null)
                                final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                            if (final_dest.exists()) final_dest.delete();
                            final_dest.createNewFile();
                            FileUtils.copyFile(cache_dest, final_dest);
                            setStatus("Server update was installed successfully (" + buildId + " -> " + latestBuildId + ")!");
                            updaterConfig.server_build_id.setValues("" + latestBuildId);
                            updaterConfig.save();
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken. Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        }
    }

    private void doPaperUpdatingLogic() throws WrongJsonTypeException, IOException, HttpErrorException, InterruptedException, DYWriterException, DuplicateKeyException, DYReaderException, IllegalListException {

        PaperDownloadsAPI paperDownloadsAPI = new PaperDownloadsAPI();
        int build_id = updaterConfig.server_build_id.asInt();
        int latest_build_id = paperDownloadsAPI.getLatestBuildId(serverSoftware, serverVersion);

        // Check if the latest build-id is bigger than our current one.
        if (latest_build_id <= build_id) {
            setStatus("Your server is on the latest version!");
            setSuccess(true);
            return;
        }

        if (profile.equals("NOTIFY")) {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + ")!");
        } else if (profile.equals("MANUAL")) {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");

            // Download the file
            String build_hash = paperDownloadsAPI.getLatestBuildHash(serverSoftware, serverVersion, latest_build_id);
            String build_name = paperDownloadsAPI.getLatestBuildFileName(serverSoftware, serverVersion, latest_build_id);
            String url = "https://papermc.io/api/v2/projects/" + serverSoftware + "/versions/" + serverVersion + "/builds/" + latest_build_id + "/downloads/" + build_name;
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500); // Wait until download is finished
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithSHA256(build_hash)) {
                            setStatus("Server update downloaded successfully.");
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken. Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        } else {
            setStatus("Update found (" + build_id + " -> " + latest_build_id + "), started download!");

            // Download the file
            String build_hash = paperDownloadsAPI.getLatestBuildHash(serverSoftware, serverVersion, latest_build_id);
            String build_name = paperDownloadsAPI.getLatestBuildFileName(serverSoftware, serverVersion, latest_build_id);
            String url = "https://papermc.io/api/v2/projects/" + serverSoftware + "/versions/" + serverVersion + "/builds/" + latest_build_id + "/downloads/" + build_name;
            File cache_dest = new File(downloadsDir.getAbsolutePath() + "/" + serverSoftware + "-latest.jar");
            if (cache_dest.exists()) cache_dest.delete();
            cache_dest.createNewFile();
            TaskDownload download = new TaskDownload("ServerDownloader", getManager(), url, cache_dest);
            download.start();

            while (true) {
                Thread.sleep(500);
                if (download.isFinished()) {
                    if (download.isSuccess()) {
                        setStatus("Server update downloaded. Checking hash...");
                        if (download.compareWithSHA256(build_hash)) {
                            File final_dest = GD.SERVER_JAR;
                            if (final_dest == null)
                                final_dest = new File(GD.WORKING_DIR + "/" + serverSoftware + "-latest.jar");
                            if (final_dest.exists()) final_dest.delete();
                            final_dest.createNewFile();
                            FileUtils.copyFile(cache_dest, final_dest);
                            setStatus("Server update was installed successfully (" + build_id + " -> " + latest_build_id + ")!");
                            updaterConfig.server_build_id.setValues("" + latest_build_id);
                            updaterConfig.save();
                            setSuccess(true);
                        } else {
                            setStatus("Downloaded server update is broken. Nothing changed!");
                            setSuccess(false);
                        }

                    } else {
                        setStatus("Server update failed!");
                        setSuccess(false);
                    }
                    break;
                }
            }
        }
    }


}
