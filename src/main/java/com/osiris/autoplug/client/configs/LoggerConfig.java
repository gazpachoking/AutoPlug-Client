/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import com.osiris.dyml.DYModule;
import com.osiris.dyml.DreamYaml;
import com.osiris.dyml.exceptions.*;

import java.io.IOException;

public class LoggerConfig extends DreamYaml {

    public DYModule debug;
    public DYModule autoplug_label;
    public DYModule force_ansi;

    public LoggerConfig() throws IOException, DuplicateKeyException, DYReaderException, IllegalListException, DYWriterException, NotLoadedException, IllegalKeyException {
        super(System.getProperty("user.dir") + "/autoplug/logger-config.yml");
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################\n" +
                        "    ___       __       ___  __\n" +
                        "   / _ |__ __/ /____  / _ \\/ /_ _____ _\n" +
                        "  / __ / // / __/ _ \\/ ___/ / // / _ `/\n" +
                        " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /\n" +
                        "                                /___/ Logger-Config\n" +
                        "Thank you for using AutoPlug!\n" +
                        "You can find detailed installation instructions at our Spigot post: https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/\n" +
                        "If there are any questions or you just wanna chat, join our Discord: https://discord.gg/GGNmtCC\n" +
                        "\n" +
                        "#######################################################################################################################");

        debug = put(name, "debug").setDefValues("false").setComments(
                "Writes the debug output to console.\n" +
                        "The log file contains the debug output by default and this option wont affect that.\n" +
                        "This is the only setting that needs a restart to work.");

        autoplug_label = put(name, "autoplug-label").setDefValues("AP");
        force_ansi = put(name, "force-ANSI").setDefValues("false").setComments(
                "Forces the terminal to use ANSI. Note that this may fail."
        );

        save();
        unlockFile();
    }


}
