/*
 * Copyright (c) 2021 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online;

import com.osiris.autoplug.client.network.online.connections.ConFileManager;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleReceive;
import com.osiris.autoplug.client.network.online.connections.ConOnlineConsoleSend;
import com.osiris.autoplug.client.network.online.connections.ConServerStatus;
import com.osiris.autoplug.core.logger.AL;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * This is the main connection to AutoPlugs online server/website.
 * It stays active all the time and sends/receives true/false values, regarding the users login status.
 * When the user is logged in, it creates the secondary connections and holds them until the user logs out.
 * The main connection authenticates using the server_key.
 * If it receives a true boolean it means that the user is logged in and opens new connections.
 */
public class ConMain extends Thread {

    // Secondary connections:
    public static final ConOnlineConsoleReceive CON_CONSOLE_RECEIVE = new ConOnlineConsoleReceive();
    public static final ConOnlineConsoleSend CON_CONSOLE_SEND = new ConOnlineConsoleSend();
    public static final ConServerStatus CON_SERVER_STATUS = new ConServerStatus();
    public static final ConFileManager CON_FILE_MANAGER = new ConFileManager();
    //public static PluginsUpdateResultConnection CON_PLUGINS_UPDATER;

    public static boolean isDone = false; // So that the log isn't a mess because of the processes which start right after this.
    public static boolean isUserAuthenticated = false;

    @Override
    public void run() {
        try {
            super.run();
            AL.info("Authenticating server...");
            SecuredConnection auth = new SecuredConnection((byte) 0);
            AL.info("Authentication success!");
            //Socket socket = auth.getSocket();
            DataInputStream dis = new DataInputStream(auth.getInput());
            //DataOutputStream dos = new DataOutputStream(auth.getOut());
            //CON_PLUGINS_UPDATER = new PluginsUpdateResultConnection();
            CON_SERVER_STATUS.open();

            isDone = true;
            boolean oldAuth = false; // Local variable that holds the auth boolean before the current one
            while (true) {
                // It can happen that we don't get a response because the web server is offline
                // In that case see the catch statement
                try {
                    while (true) {
                        isUserAuthenticated = dis.readBoolean();

                        if (isUserAuthenticated) {
                            if (!oldAuth) {
                                AL.debug(this.getClass(), "User is online.");
                                // User is online, so open secondary connections if they weren't already
                                if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                                CON_CONSOLE_RECEIVE.open();
                                if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                                CON_CONSOLE_SEND.open();
                                if (CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.close();
                                CON_FILE_MANAGER.open();
                                //if (!CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.open(); Only is used at restarts!
                            }
                        } else {
                            if (oldAuth) {
                                AL.debug(this.getClass(), "User is offline.");
                                // Close secondary connections when user is offline/logged out
                                if (CON_CONSOLE_RECEIVE.isConnected()) CON_CONSOLE_RECEIVE.close();
                                if (CON_CONSOLE_SEND.isConnected()) CON_CONSOLE_SEND.close();
                                if (CON_FILE_MANAGER.isConnected()) CON_FILE_MANAGER.close();
                                //if (CON_PLUGINS_UPDATER.isConnected()) CON_PLUGINS_UPDATER.close(); Only is used at restarts!
                            }
                        }
                        oldAuth = isUserAuthenticated;
                        Thread.sleep(1000);
                    }
                } catch (Exception e) {
                    AL.warn("Lost connection to AutoPlug-Web! Reconnecting in 30 seconds...", e);

                    // Reset booleans
                    oldAuth = false;
                    isUserAuthenticated = false;

                    // Make sure socket is really closed
                    try {
                        if (auth.getSocket() != null && !auth.getSocket().isClosed())
                            auth.getSocket().close();
                    } catch (IOException ioException) {
                        AL.warn(ioException);
                    }

                    // Close child connections
                    try {
                        if (CON_CONSOLE_RECEIVE != null && CON_CONSOLE_RECEIVE.isConnected())
                            CON_CONSOLE_RECEIVE.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }

                    try {
                        if (CON_CONSOLE_SEND != null && CON_SERVER_STATUS.isConnected())
                            CON_CONSOLE_SEND.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }

                    try {
                        if (CON_SERVER_STATUS != null && CON_SERVER_STATUS.isConnected())
                            CON_SERVER_STATUS.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }

                    try {
                        if (CON_FILE_MANAGER != null && CON_FILE_MANAGER.isConnected())
                            CON_FILE_MANAGER.close();
                    } catch (IOException e1) {
                        AL.warn(e1);
                    }

                    Thread.sleep(30000);
                    try {
                        AL.info("Authenticating server...");
                        auth = new SecuredConnection((byte) 0);
                        dis = new DataInputStream(auth.getInput());
                        AL.info("Authentication success!");
                        CON_SERVER_STATUS.open();
                    } catch (Exception exception) {
                        AL.warn(e);
                    }
                }
            }
        } catch (Exception e) {
            AL.warn(this.getClass(), e, "Connection aborted due to issues!");
            isDone = true;
        }
    }
}
